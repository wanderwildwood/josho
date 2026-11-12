package com.wanderwildwood.josho.engine

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * The folder the reader chose with the system picker, and the private copy LilyPond works in.
 *
 * LilyPond opens `\include "motif.ily"` by path, and a folder from the picker has none, so
 * before each compile the folder's LilyPond files are copied into files/work/ (same names, same
 * subfolders), compiled there, and the PDF (and MIDI, if the score asks for one) is written back
 * beside the score in the chosen folder.
 */
class Workspace(private val context: Context, val tree: Uri) : Folder {

    private val resolver: ContentResolver = context.contentResolver
    private val work: File get() = File(context.filesDir, "work").apply { mkdirs() }

    /** The folder's name as the picker shows it. */
    override fun title(): String = query(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)))
        .firstOrNull()?.name ?: tree.lastPathSegment.orEmpty().substringAfterLast(':')

    /** Every file under the folder, depth first, with its path relative to the folder. */
    private fun files(): List<Doc> {
        val out = mutableListOf<Doc>()
        fun walk(parentId: String, parentUri: Uri, prefix: String, depth: Int) {
            if (depth > MAX_DEPTH) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            resolver.query(children, COLUMNS, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (!name.startsWith(".")) walk(id, uri, "$prefix$name/", depth + 1)
                    } else {
                        out += Doc(uri.toString(), prefix + name, name, parentUri.toString(), size)
                    }
                }
            }
        }
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        walk(rootId, DocumentsContract.buildDocumentUriUsingTree(tree, rootId), "", 0)
        return out
    }

    override fun scores(): List<Doc> {
        // A folder deleted since it was chosen lists as empty, as if it had no scores; asked for
        // itself, it is not there, and the files page then says so and offers to choose again.
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        if (query(DocumentsContract.buildDocumentUriUsingTree(tree, rootId)).isEmpty()) {
            throw java.io.FileNotFoundException("The chosen folder is not there")
        }
        return files().filter { it.name.endsWith(".ly") }.sortedBy { it.path.lowercase() }
    }

    override fun read(doc: Doc): String =
        resolver.openInputStream(Uri.parse(doc.id))!!.use { it.readBytes().toString(Charsets.UTF_8) }

    override fun write(doc: Doc, text: String) {
        resolver.openOutputStream(Uri.parse(doc.id), "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    override fun create(name: String, text: String): Doc? {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        return createIn(DocumentsContract.buildDocumentUriUsingTree(tree, rootId), "", name, text)
    }

    /** A new file in the folder [parent], whose path under the chosen folder is [prefix] ("" or "parts/"). */
    private fun createIn(parent: Uri, prefix: String, name: String, text: String): Doc? {
        val uri = DocumentsContract.createDocument(resolver, parent, "text/x-lilypond", name) ?: return null
        val real = query(uri).firstOrNull()?.name ?: name
        return Doc(uri.toString(), prefix + real, real, parent.toString(), 0).also { write(it, text) }
    }

    override fun createBeside(score: Doc, name: String, text: String): Doc? =
        createIn(Uri.parse(score.parent), score.path.removeSuffix(score.name), name, text)

    /** The one folder [score] is in, asked for by itself: quicker than walking the whole tree. */
    override fun beside(score: Doc): List<Doc> {
        val parent = Uri.parse(score.parent)
        val prefix = score.path.removeSuffix(score.name)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        return resolver.query(children, COLUMNS, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                    add(Doc(uri.toString(), prefix + name, name, score.parent, if (c.isNull(3)) 0L else c.getLong(3)))
                }
            }
        } ?: emptyList()
    }

    override fun delete(doc: Doc): Boolean = DocumentsContract.deleteDocument(resolver, Uri.parse(doc.id))

    /**
     * Copies the folder's LilyPond files into [work] (cleared first) and returns where [score]
     * now is there. Only text LilyPond reads, and nothing large, so a folder of PDFs and
     * recordings costs nothing.
     */
    override fun mirror(score: Doc): File {
        work.deleteRecursively()
        work.mkdirs()
        for (f in files()) {
            if (!isSource(f)) continue
            val out = File(work, f.path)
            if (!out.canonicalPath.startsWith(work.canonicalPath + File.separator)) continue
            out.parentFile?.mkdirs()
            resolver.openInputStream(Uri.parse(f.id))?.use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        return File(work, score.path)
    }

    override fun putBeside(score: Doc, file: File, mime: String) {
        val existing = files().firstOrNull { it.parent == score.parent && it.name == file.name }
        val target = existing?.id?.let(Uri::parse)
            ?: DocumentsContract.createDocument(resolver, Uri.parse(score.parent), mime, file.name)
            ?: return
        resolver.openOutputStream(target, "wt")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    private fun query(uri: Uri): List<Doc> =
        resolver.query(uri, COLUMNS, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    add(Doc(uri.toString(), name, name, uri.toString(), if (c.isNull(3)) 0 else c.getLong(3)))
                }
            }
        } ?: emptyList()

    companion object {
        private val COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        private const val MAX_DEPTH = 4
    }
}
