package com.wanderwildwood.josho.engine

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * What LilyPond's unpacked files should hold, so a score cannot change them for the scores
 * after it. LilyPond runs the Scheme a score carries, and that Scheme runs as this app: it could
 * rewrite LilyPond's own files, and every later score would run what it wrote.
 *
 * As the prefix is unpacked, each file's SHA-256 is taken from the bytes the APK ships ([take]),
 * and the files are made read-only ([seal]). Before LilyPond is started, [check] hashes them
 * again and looks for any file that was not shipped; a difference means the prefix is unpacked
 * afresh from the APK. The hashes are kept beside the prefix, in [file].
 */
class PrefixSeal(private val base: File, private val file: File) {

    private val hashes = LinkedHashMap<String, String>()

    /** Starts a new unpacking: nothing taken yet. */
    fun reset() = hashes.clear()

    /** A symbolic link made under [base], to [target]: noted, and checked as a link to it. */
    fun link(link: File, target: String) {
        hashes[relative(link)] = LINK + target
    }

    /** Copies [input] to [out], a file under [base], noting the SHA-256 of what was written. */
    fun take(input: InputStream, out: File) {
        val digest = MessageDigest.getInstance("SHA-256")
        out.outputStream().use { o ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
                o.write(buffer, 0, n)
            }
        }
        hashes[relative(out)] = hex(digest.digest())
    }

    /** Writes the hashes down and makes every file taken read-only. */
    fun seal() {
        for ((rel, hash) in hashes) if (!hash.startsWith(LINK)) File(base, rel).setWritable(false, false)
        file.setWritable(true, true)
        file.writeText(hashes.entries.joinToString("\n") { "${it.value}  ${it.key}" } + "\n")
        file.setWritable(false, false)
    }

    /**
     * True when every file taken is there with the bytes it was shipped with, and no other file
     * has appeared under [watched] (folders under [base]), [ignore] apart: files, or folders
     * whose whole contents are left out.
     */
    fun check(watched: List<File>, ignore: Set<File> = emptySet()): Boolean {
        val expected = read() ?: return false
        for ((rel, hash) in expected) {
            val f = File(base, rel)
            if (hash.startsWith(LINK)) {
                val now = runCatching { java.nio.file.Files.readSymbolicLink(f.toPath()).toString() }.getOrNull()
                if (now != hash.removePrefix(LINK)) return false
                continue
            }
            // A file made writable again has been touched, whatever it holds now.
            if (!f.isFile || f.canWrite()) return false
            if (hashOf(f) != hash) return false
        }
        val ignored = ignore.map { it.absolutePath }
        fun isIgnored(f: File) = ignored.any { f.absolutePath == it || f.absolutePath.startsWith(it + File.separator) }
        for (dir in watched) {
            if (!dir.exists()) continue
            // Every file and link, links not followed: one that was not unpacked is a change.
            val extra = java.nio.file.Files.walk(dir.toPath()).use { paths ->
                paths.filter { !java.nio.file.Files.isDirectory(it, java.nio.file.LinkOption.NOFOLLOW_LINKS) }
                    .map { it.toFile() }
                    .anyMatch { !isIgnored(it) && relative(it) !in expected }
            }
            if (extra) return false
        }
        return true
    }

    private fun read(): Map<String, String>? = runCatching {
        file.readLines().filter { it.isNotBlank() }.associate { line ->
            val hash = line.substringBefore("  ")
            line.substringAfter("  ") to hash
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun relative(f: File): String = f.absoluteFile.relativeTo(base.absoluteFile).path

    companion object {
        private const val BUFFER = 64 * 1024

        /** Marks a link's entry: its target follows, in place of a hash. */
        private const val LINK = "link:"

        fun hashOf(f: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return hex(digest.digest())
        }

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}
