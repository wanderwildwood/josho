package com.wanderwildwood.josho.engine

import android.content.Context
import android.os.SystemClock
import android.system.Os
import java.io.File
import java.util.zip.ZipInputStream

/**
 * LilyPond on the phone: the prefix (Guile, LilyPond's Scheme and fonts, the libraries) unpacked
 * once into files/usr, and the executable run from the native library directory, the one place
 * Android 10+ lets an app start a program of its own.
 */
class LilyPond(private val context: Context) {

    private val prefix = File(context.filesDir, "usr")
    /**
     * LilyPond's own Scheme (the .scm files under scm/lily, 2.6 MB) compiled by Guile, shipped in the prefix
     * zip under guile-cache/. Guile finds a compiled file for an absolute source path in its
     * "fallback" cache, $XDG_CACHE_HOME/guile/ccache/…/<source path>.go, so this is that cache.
     * Without it Guile interprets all of LilyPond's Scheme on every compile: 10 s instead of
     * 2 on a Kompakt.
     */
    private val guileCache = File(context.filesDir, "guile-cache")
    private val stamp = File(prefix, ".josho-version")
    private val exe = File(context.applicationInfo.nativeLibraryDir, "liblilypond_exe.so")

    /**
     * What the unpacked prefix belongs to: the version and the install time, so a test build
     * installed over one of the same version code (as happens before a release) unpacks afresh.
     */
    private val version: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).let { "${it.longVersionCode}-${it.lastUpdateTime}" }

    fun isReady(): Boolean = stamp.isFile && stamp.readText() == version

    /** Unpacks the prefix if this version has not yet; returns how long it took, 0 if nothing to do. */
    fun prepare(): Long {
        if (isReady()) return 0
        val t0 = SystemClock.elapsedRealtime()
        val files = context.filesDir
        prefix.deleteRecursively()
        guileCache.deleteRecursively()
        val abi = File(context.applicationInfo.nativeLibraryDir).name   // "arm64" or "x86_64"
        ZipInputStream(context.assets.open("prefix-$abi.zip").buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val out = File(files, e.name)
                if (!out.canonicalPath.startsWith(files.canonicalPath + File.separator)) continue
                if (e.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                out.outputStream().use { zip.copyTo(it) }
            }
        }
        // Zip keeps no symlinks; the packer lists them as "link<TAB>target", relative to files/.
        context.assets.open("prefix-$abi-symlinks.txt").bufferedReader().forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val (link, target) = line.split('\t', limit = 2)
            val f = File(files, link)
            f.parentFile?.mkdirs()
            f.delete()
            Os.symlink(target, f.path)
        }
        // Unzipping stamps every file with the time it was written, in no particular order, so
        // a Scheme source can look newer than its compiled .go. Guile then ignores the .go and
        // interprets everything, which turns a 2 s compile into minutes.
        val later = System.currentTimeMillis() + 60_000
        for (dir in listOf(prefix, guileCache)) {
            dir.walkTopDown().filter { it.isFile && it.name.endsWith(".go") }.forEach { it.setLastModified(later) }
        }
        stamp.writeText(version)
        return SystemClock.elapsedRealtime() - t0
    }

    class Result(val exit: Int, val millis: Long, val log: String, val pdf: File?, val midi: File?)

    /**
     * A LilyPond kept running between compiles while the editor is open. Most of a one-shot
     * compile on a Kompakt is LilyPond starting (Guile, its Scheme, the fonts, the init files);
     * this pays that once. It is LilyPond's own many-files loop (lilypond-all in lily.scm, what
     * `lilypond a.ly b.ly` runs), fed one file at a time on stdin, so each score starts from the
     * same clean state a fresh process would give it.
     */
    private var server: Process? = null
    private var serverOut: java.io.BufferedReader? = null
    private var serverRuns = 0

    /** Compiles [name] inside [dir] (where its includes are too); output lands beside it. */
    @Synchronized
    fun compile(dir: File, name: String, autoCompile: Boolean = false): Result {
        val base = name.substringBeforeLast('.')
        val pdf = File(dir, "$base.pdf")
        val midi = File(dir, "$base.midi")
        pdf.delete(); midi.delete()
        val t0 = SystemClock.elapsedRealtime()
        // Filling the Scheme cache (debug builds) wants a fresh process; so does a server that
        // has done its share, so memory from many scores never piles up.
        val kept = if (autoCompile) null else runCatching { viaServer(dir, name) }
            .onFailure { stop() }.getOrNull()
        val (exit, log) = kept ?: oneShot(dir, name, autoCompile)
        return Result(
            exit, SystemClock.elapsedRealtime() - t0, log,
            pdf.takeIf { it.isFile && it.length() > 0 },
            midi.takeIf { it.isFile && it.length() > 0 },
        )
    }

    /** Starts the kept LilyPond ahead of the first compile, so even that one is quick. */
    @Synchronized
    fun warmUp() {
        if (!isReady() || server?.isAlive == true) return
        runCatching { startServer() }.onFailure { stop() }
    }

    /** Ends the kept LilyPond (the app left the screen); the next compile starts it again. */
    @Synchronized
    fun stop() {
        runCatching { server?.outputStream?.close() }
        server?.destroy()
        server = null; serverOut = null; serverRuns = 0
    }

    private fun startServer() {
        val pb = processBuilder(
            context.cacheDir,
            listOf(exe.path, "-dbackend=cairo", "--pdf", "-dno-point-and-click", "-e", SERVER_SCHEME, "-"),
            autoCompile = false,
        )
        val p = pb.start()
        server = p
        serverOut = p.inputStream.bufferedReader()
        serverRuns = 0
    }

    private fun viaServer(dir: File, name: String): Pair<Int, String> {
        if (serverRuns >= SERVER_RUNS || server?.isAlive != true) { stop(); startServer() }
        val p = server!!
        val out = serverOut!!
        p.outputStream.write("${dir.path}\t$name\n".toByteArray(Charsets.UTF_8))
        p.outputStream.flush()
        serverRuns++
        // A score that hangs LilyPond must not hang the editor: past the limit the process goes,
        // its output ends, and the compile below falls back to a one-shot run that says why.
        val watchdog = Thread {
            try { Thread.sleep(SERVER_LIMIT_MS); p.destroy() } catch (_: InterruptedException) {}
        }.apply { isDaemon = true; start() }
        try {
            val log = StringBuilder()
            while (true) {
                val line = out.readLine() ?: error("LilyPond stopped")
                if (line.startsWith(DONE)) {
                    return (line.removePrefix(DONE).trim().toIntOrNull() ?: 1) to log.toString()
                }
                log.append(line).append('\n')
            }
        } finally {
            watchdog.interrupt()
        }
    }

    private fun oneShot(dir: File, name: String, autoCompile: Boolean): Pair<Int, String> {
        val pb = processBuilder(dir, listOf(exe.path, "-dbackend=cairo", "--pdf", "-dno-point-and-click", name), autoCompile)
        val p = pb.start()
        val log = p.inputStream.bufferedReader().readText()
        return p.waitFor() to log
    }

    private fun processBuilder(dir: File, command: List<String>, autoCompile: Boolean): ProcessBuilder {
        val pb = ProcessBuilder(command)
            .directory(dir)
            .redirectErrorStream(true)
        val usr = prefix.path
        pb.environment().apply {
            put("HOME", dir.path)
            put("TMPDIR", context.cacheDir.path)
            put("LD_LIBRARY_PATH", "$usr/lib")
            put("LILYPOND_DATADIR", "$usr/share/lilypond/2.26.0")
            put("GUILE_LOAD_PATH", "$usr/share/guile/3.0")
            put("GUILE_LOAD_COMPILED_PATH", "$usr/lib/guile/3.0/ccache")
            put("FONTCONFIG_FILE", "$usr/etc/fonts/fonts.conf")
            put("XDG_CACHE_HOME", guileCache.path)
            // Some Guile modules ship as Scheme only (build/pack-prefix.py); load them interpreted
            // rather than trying to compile them into a cache. [autoCompile] (debug builds only)
            // fills guile-cache/ instead: that is how the shipped cache is made (build/README).
            put("GUILE_AUTO_COMPILE", if (autoCompile) "1" else "0")
        }
        return pb
    }

    companion object {
        /** Starts the line the kept LilyPond writes after each score, then 0 (done) or 1 (failed). */
        private const val DONE = "\u001fjosho-done "
        /** Scores per kept LilyPond before a fresh one. */
        private const val SERVER_RUNS = 25
        private const val SERVER_LIMIT_MS = 120_000L

        /**
         * Run by `-e` before LilyPond would look at its files, and never returns: the body of
         * lilypond-all (lily.scm), one score per stdin line "folder<TAB>file". LilyPond writes a
         * score's output beside it in the working directory, hence the chdir. End of stdin ends it.
         * `(@@ (lily) …)` reaches the two helpers lily.scm keeps to itself.
         */
        internal val SERVER_SCHEME = """
            (use-modules (ice-9 rdelim))
            (ly:reset-all-fonts)
            (ly:parse-init "declarations-init.ly")
            (let loop ()
              (let ((line (read-line (current-input-port))))
                (if (eof-object? line) (exit 0))
                (let* ((tab (string-index line #\tab))
                       (dir (substring line 0 tab))
                       (file (substring line (1+ tab)))
                       (failed #f)
                       (settings (ly:all-options)))
                  (catch #t
                    (lambda ()
                      (chdir dir)
                      ((@@ (lily) lilypond-file) (lambda (key f) (set! failed #t)) file)
                      (ly:check-expected-warnings))
                    (lambda (key . args)
                      (set! failed #t)
                      (format (current-error-port) "~a: ~s~%" key args)))
                  ((@@ (lily) session-terminate))
                  (ly:reset-options settings)
                  (ly:reset-all-fonts)
                  (flush-all-ports)
                  (format (current-error-port) "~%${DONE}~a~%" (if failed 1 0))
                  (force-output (current-error-port))
                  (loop))))
        """.trimIndent()
    }
}
