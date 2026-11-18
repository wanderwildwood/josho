package com.wanderwildwood.josho.engine

import android.content.Context
import com.wanderwildwood.josho.R
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
    private val seal = PrefixSeal(context.filesDir, File(context.filesDir, "prefix-sha256.txt"))

    /**
     * The prefix's files as they were last found whole (size and time of each), so a compile by
     * the kept LilyPond can be followed by a quick look rather than a full check.
     */
    private var lastSeen: Map<String, Pair<Long, Long>>? = null
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
        stop()
        lastSeen = null
        seal.reset()
        // Read-only files go with their folder; the folders themselves were left writable.
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
                seal.take(zip, out)
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
            seal.link(f, target)
        }
        // Unzipping stamps every file with the time it was written, in no particular order, so
        // a Scheme source can look newer than its compiled .go. Guile then ignores the .go and
        // interprets everything, which turns a 2 s compile into minutes.
        val later = System.currentTimeMillis() + 60_000
        for (dir in listOf(prefix, guileCache)) {
            dir.walkTopDown().filter { it.isFile && it.name.endsWith(".go") }.forEach { it.setLastModified(later) }
        }
        // Read-only from here, and their hashes kept: see [ensureIntact].
        seal.seal()
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

    /**
     * Scores that name no paper are engraved on A6, which fits a phone's screen (the score pane
     * draws a page its full width, so on a larger screen it is only larger); a \paper block in
     * the score still wins, so a file written for printing keeps its size. It reaches LilyPond
     * as a settings file read before each score (the include-settings option); the paper-size
     * option itself failed here: "Wrong type (expecting real number)".
     */
    private fun options(): List<String> {
        val settings = File(context.filesDir, "paper.ly")
        val line = "#(set-default-paper-size \"$PAPER\")\n"
        if (!settings.isFile || settings.readText() != line) settings.writeText(line)
        // The path bare: LilyPond takes this option's value as the file name as it stands, and a
        // quoted one is a name with quotes in it (tried on the emulator 2026-11-09). The app's own
        // files folder has no spaces in its path.
        return listOf("-dbackend=cairo", "--pdf", "-dno-point-and-click", "-dinclude-settings=${settings.path}")
    }
    private var serverOut: java.io.BufferedReader? = null
    @Volatile private var serverTimedOut = false
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
        serverTimedOut = false
        val kept = if (autoCompile) null else runCatching { viaServer(dir, name) }
            .onFailure { stop() }.getOrNull()
        // After a score in the kept LilyPond, a quick look over the prefix: anything changed
        // there and the kept LilyPond goes, so the next start checks it in full and unpacks it
        // again. The score that changed it has run; none after it runs on what it wrote.
        if (kept != null && lastSeen != snapshot()) {
            stop()
            lastSeen = null
        }
        // A score that ran past the limit would only run past it again on its own.
        val (exit, log) = kept
            ?: if (serverTimedOut) 1 to context.getString(R.string.compile_too_long) else oneShot(dir, name, autoCompile)
        return Result(
            exit, SystemClock.elapsedRealtime() - t0, log,
            pdf.takeIf { it.isFile && it.length() > 0 },
            midi.takeIf { it.isFile && it.length() > 0 },
        )
    }

    /**
     * Before LilyPond is started: the prefix's files are hashed against what the APK shipped,
     * and anything else found in it is a change too. A change means a score's Scheme wrote
     * there, so the prefix is unpacked afresh before LilyPond runs. [autoCompile] (debug builds)
     * fills guile-cache/ on purpose, so new files there are expected then.
     */
    private fun ensureIntact(autoCompile: Boolean) {
        val watched = if (autoCompile) listOf(prefix) else listOf(prefix, guileCache)
        if (isReady() && seal.check(watched, ignore = notSealed())) {
            lastSeen = snapshot()
            return
        }
        stamp.delete()
        prepare()
        lastSeen = snapshot()
    }

    /**
     * What LilyPond writes for itself and is not sealed: the version stamp, and fontconfig's
     * cache of the fonts, which it makes on its first run in the first folder it may write to
     * (fonts.conf names usr/var/cache/fontconfig, then $XDG_CACHE_HOME/fontconfig).
     */
    private fun notSealed(): Set<File> =
        setOf(stamp, File(prefix, "var/cache/fontconfig"), File(guileCache, "fontconfig"))

    /** Size and time of every file under the prefix and the Scheme cache, fontconfig's cache apart. */
    private fun snapshot(): Map<String, Pair<Long, Long>> {
        val skip = notSealed().map { it.path }
        return listOf(prefix, guileCache).flatMap { dir ->
            dir.walkTopDown()
                .onEnter { d -> skip.none { d.path == it } }
                .filter { it.isFile && it.path !in skip }
                .map { it.path to (it.length() to it.lastModified()) }.toList()
        }.toMap()
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
        ensureIntact(autoCompile = false)
        val pb = processBuilder(
            context.cacheDir,
            listOf(exe.path) + options() + listOf("-e", SERVER_SCHEME, "-"),
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
            try { Thread.sleep(SERVER_LIMIT_MS); serverTimedOut = true; p.destroy() } catch (_: InterruptedException) {}
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
        ensureIntact(autoCompile)
        // "./": a file name is never taken for an option, whatever it begins with.
        val pb = processBuilder(dir, listOf(exe.path) + options() + listOf("./$name"), autoCompile)
        val p = pb.start()
        // The same limit as the kept LilyPond's, so a score that hangs it cannot hold every
        // later compile behind it. The output is read beside the wait.
        val log = StringBuilder()
        val reader = Thread {
            runCatching { p.inputStream.bufferedReader().forEachLine { synchronized(log) { log.append(it).append('\n') } } }
        }.apply { isDaemon = true; start() }
        val limit = if (autoCompile) AUTO_COMPILE_LIMIT_MS else SERVER_LIMIT_MS
        if (!p.waitFor(limit, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            p.destroyForcibly()
            reader.join(2_000)
            return 1 to synchronized(log) { log.toString() } + context.getString(R.string.compile_too_long)
        }
        reader.join(5_000)
        return p.exitValue() to synchronized(log) { log.toString() }
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
        /** The paper for scores that name none. */
        private const val PAPER = "a6"
        /** Scores per kept LilyPond before a fresh one. */
        /** Held by a compile from setting up to its PDF beside the score; there is one work folder. */
        val workLock = Any()
        private const val SERVER_RUNS = 25
        private const val SERVER_LIMIT_MS = 120_000L
        /** Filling the Scheme cache (debug builds only) takes far longer than a compile. */
        private const val AUTO_COMPILE_LIMIT_MS = 30 * 60_000L

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
