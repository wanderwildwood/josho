package com.wanderwildwood.josho.ui

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import androidx.core.content.FileProvider
import com.wanderwildwood.josho.engine.Doc
import java.io.File
import java.io.FileOutputStream

private const val TAG = "Josho"
private const val PDF = "application/pdf"

/**
 * The address another app can read [doc] by. A file in the chosen folder already has one (the
 * folder chooser's, which this app may pass on); a file in the app's own sample folder gets one
 * from the FileProvider in the manifest.
 */
private fun addressOf(context: Context, doc: Doc): Uri =
    if (doc.id.startsWith("content://")) Uri.parse(doc.id)
    else FileProvider.getUriForFile(context, context.packageName + ".files", File(doc.id))

/**
 * Hands the engraved [pdf] to whichever app the reader has for PDFs, with leave to read that one
 * file. False when no app takes it.
 */
internal fun openElsewhere(context: Context, pdf: Doc): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(addressOf(context, pdf), PDF)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
    true
} catch (e: RuntimeException) {
    // ActivityNotFoundException with no PDF app; anything else is logged and answered the same.
    Log.e(TAG, "open elsewhere failed", e)
    false
}

/** Whether this device prints at all; the Print row is left out where it does not. */
internal fun canPrint(context: Context): Boolean =
    runCatching { context.packageManager.hasSystemFeature(PackageManager.FEATURE_PRINTING) }.getOrDefault(false)

/**
 * Opens Android's print screen for the engraved [pdf]. False when printing could not start (a
 * device that says it prints and has no print spooler, say).
 */
internal fun printPdf(context: Context, pdf: Doc, title: String): Boolean = try {
    val printing = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    printing.print(title, PdfPrint(context.contentResolver, addressOf(context, pdf), pdf.name), null)
    true
} catch (e: RuntimeException) {
    Log.e(TAG, "print failed", e)
    false
}

/** Gives the print screen the PDF as it stands: LilyPond has already laid the pages out. */
private class PdfPrint(
    private val resolver: ContentResolver,
    private val pdf: Uri,
    private val name: String,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        // The file is the same whatever paper is asked for, so nothing changes after the first time.
        callback?.onLayoutFinished(info, oldAttributes == null)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?,
    ) {
        // Off the main thread: a chosen folder may be on a slow card.
        Thread {
            try {
                val input = resolver.openInputStream(pdf) ?: throw java.io.FileNotFoundException(name)
                input.use { from -> FileOutputStream(destination!!.fileDescriptor).use { from.copyTo(it) } }
                if (cancellationSignal?.isCanceled == true) callback?.onWriteCancelled()
                else callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                Log.e(TAG, "print write failed", e)
                callback?.onWriteFailed(e.message)
            }
        }.start()
    }
}
