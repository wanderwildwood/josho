package com.wanderwildwood.josho.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.wanderwildwood.josho.engine.MAX_SOURCE
import com.wanderwildwood.josho.engine.NotText
import com.wanderwildwood.josho.engine.Shared
import com.wanderwildwood.josho.engine.TooLarge
import com.wanderwildwood.josho.engine.readAtMost

/** Whether [intent] hands Engraver something through the Share menu. */
fun isShare(intent: Intent?): Boolean = intent?.action == Intent.ACTION_SEND

/**
 * What [intent] shares: a file's text and name, or plain text. Read at once, while the grant to
 * the file lasts. Throws [TooLarge] past the size a score may have, [NotText] when nothing usable
 * came, or an IOException when the file could not be read.
 */
fun readShared(context: Context, intent: Intent): Shared {
    @Suppress("DEPRECATION")
    val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
    if (stream != null) {
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(stream, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: stream.lastPathSegment
        val text = resolver.openInputStream(stream)?.use { readAtMost(it) } ?: throw NotText()
        return Shared(text, name)
    }
    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: throw NotText()
    if (text.length > MAX_SOURCE) throw TooLarge()
    return Shared(text, null)
}
