package com.wanderwildwood.josho.engine

import android.content.Context
import java.io.File

/** A file in a [Folder]: [id] is how the folder finds it again, [path] is relative to the folder. */
data class Doc(val id: String, val path: String, val name: String, val parent: String, val size: Long)

/** Where scores live: the reader's chosen folder ([Workspace]) or the app's own ([LocalFolder]). */
interface Folder {
    fun title(): String
    fun scores(): List<Doc>
    fun read(doc: Doc): String
    fun write(doc: Doc, text: String)
    fun create(name: String, text: String): Doc?
    /** The folder's LilyPond files as real files for LilyPond, and where [score] is among them. */
    fun mirror(score: Doc): File
    /** Writes [file] beside [score], replacing a file of the same name. */
    fun putBeside(score: Doc, file: File, mime: String)
    /** The files in the same folder as [score], itself among them; not those in folders under it. */
    fun beside(score: Doc): List<Doc>
    /** A new file [name] holding [text], in the same folder as [score]. */
    fun createBeside(score: Doc, name: String, text: String): Doc?
    /** Deletes [doc]; false when it could not be. */
    fun delete(doc: Doc): Boolean
}

/** What LilyPond may \include: scores, include files, Scheme, plain text; nothing large. */
val SOURCE_TYPES = setOf("ly", "ily", "lyi", "scm", "txt")
internal const val MAX_SOURCE = 2L * 1024 * 1024

fun isSource(doc: Doc): Boolean =
    doc.name.substringAfterLast('.', "").lowercase() in SOURCE_TYPES && doc.size <= MAX_SOURCE

/** The copy a save leaves beside a score until the score itself holds the new text. */
internal fun backupName(name: String): String = "$name.saving"

/**
 * Saves in three steps: the new text into a copy beside the score, then into the score, then the
 * copy is dropped. A copy that cannot be made stops the save before the score is touched; a score
 * that cannot be written keeps the copy. Only dropping the copy may fail quietly: by then the
 * score holds the text, and a copy left over does no harm.
 */
internal fun saveWithBackup(writeBackup: () -> Unit, writeInPlace: () -> Unit, dropBackup: () -> Unit) {
    writeBackup()
    writeInPlace()
    try {
        dropBackup()
    } catch (_: Exception) {
    }
}

/** A score past [MAX_SOURCE]: more than the editor opens or LilyPond is given. */
class TooLarge : java.io.IOException("larger than $MAX_SOURCE bytes")

/** All of [input] as text; past [limit] it is not read on, and [TooLarge] says why. */
internal fun readAtMost(input: java.io.InputStream, limit: Long = MAX_SOURCE): String {
    val out = java.io.ByteArrayOutputStream()
    if (copyAtMost(input, out, limit) > limit) throw TooLarge()
    return String(out.toByteArray(), Charsets.UTF_8)
}

/**
 * A folder inside the app (files/scratch), for trying Engraver before choosing a folder of one's
 * own: it holds the sample. LilyPond compiles in place here, so nothing needs copying.
 */
class LocalFolder(private val dir: File) : Folder {
    constructor(context: Context) : this(File(context.filesDir, "scratch"))

    init { dir.mkdirs() }

    private fun doc(f: File) = Doc(f.path, f.name, f.name, dir.path, f.length())

    override fun title(): String = "Sample"

    override fun scores(): List<Doc> = dir.listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(".ly") }
        .sortedBy { it.name.lowercase() }
        .map(::doc)

    override fun read(doc: Doc): String = File(doc.id).inputStream().use { readAtMost(it) }

    /** Written beside the score first and then put in its place, so a write that fails leaves the score as it was. */
    override fun write(doc: Doc, text: String) {
        val target = File(doc.id)
        val temp = File(target.parentFile, ".${target.name}.saving")
        try {
            temp.writeText(text)
            if (!temp.renameTo(target)) throw java.io.IOException("could not replace the score")
        } finally {
            temp.delete()
        }
    }

    override fun create(name: String, text: String): Doc? {
        val f = File(dir, name)
        f.writeText(text)
        return doc(f)
    }

    override fun mirror(score: Doc): File = File(score.id)

    override fun putBeside(score: Doc, file: File, mime: String) {
        val target = File(File(score.id).parentFile, file.name)
        if (target.canonicalPath != file.canonicalPath) file.copyTo(target, overwrite = true)
    }

    override fun beside(score: Doc): List<Doc> = dir.listFiles().orEmpty().filter { it.isFile }.map(::doc)

    override fun createBeside(score: Doc, name: String, text: String): Doc? = create(name, text)

    override fun delete(doc: Doc): Boolean = File(doc.id).delete()
}
