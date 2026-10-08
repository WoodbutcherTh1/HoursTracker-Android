package com.hourstracker.app.ui.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Writes a file the user asked for into the cache and hands it to the Android share sheet. */
object FileSharing {
    /** A file in `cache/exports`; files older than a day are removed first. */
    fun cacheFile(context: Context, name: String): File {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600 * 1000 }?.forEach { it.delete() }
        return File(directory, name)
    }

    fun share(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
