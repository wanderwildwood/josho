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
private const val MAX_SOURCE = 2L * 1024 * 1024

fun isSource(doc: Doc): Boolean =
    doc.name.substringAfterLast('.', "").lowercase() in SOURCE_TYPES && doc.size <= MAX_SOURCE

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

    override fun read(doc: Doc): String = File(doc.id).readText()

    override fun write(doc: Doc, text: String) = File(doc.id).writeText(text)

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
