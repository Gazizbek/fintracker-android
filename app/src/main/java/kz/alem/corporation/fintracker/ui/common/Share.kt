package kz.alem.corporation.fintracker.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Writes [content] to a cache file and opens the system share sheet for it. */
fun shareCsv(context: Context, fileName: String, content: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, fileName)
    file.writeText(content, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
