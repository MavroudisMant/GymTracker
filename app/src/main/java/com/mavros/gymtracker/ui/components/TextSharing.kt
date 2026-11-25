package com.mavros.gymtracker.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Opens the share sheet with plain text (e.g. to a chat app, email, or a chatbot app). */
fun Context.shareText(title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TITLE, title)
    }
    startActivity(Intent.createChooser(send, title))
}

/**
 * Copies text to the clipboard. Returns true when the app should confirm it itself; Android 13+
 * shows its own confirmation.
 */
fun Context.copyText(label: String, text: String): Boolean {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
}

/** The clipboard's text, if any. */
fun Context.clipboardText(): String? {
    val clip = getSystemService(ClipboardManager::class.java).primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(this)?.toString()?.takeIf { it.isNotBlank() }
}

/** Returns a function that asks where to save a .txt file, then writes the text there. */
@Composable
fun rememberTextFileSaver(onResult: (saved: Boolean) -> Unit = {}): (fileName: String, text: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingText by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = pendingText
        pendingText = null
        if (uri == null || text == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
                }.getOrDefault(false)
            }
            onResult(saved)
        }
    }
    return { fileName, text ->
        pendingText = text
        launcher.launch(fileName)
    }
}

/** A filesystem-friendly name, e.g. "Upper / Lower" -> "Upper - Lower.txt". */
fun templateFileName(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|]+"""), "-").trim().ifEmpty { "template" } + ".txt"

/** Templates are small; anything bigger than this isn't one. */
const val MAX_TEXT_FILE_BYTES = 256 * 1024

/** The stream as UTF-8 text, or null if it's longer than [maxBytes]. */
fun InputStream.readTextUpTo(maxBytes: Int): String? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) return null
        out.write(buffer, 0, read)
    }
    return out.toString(Charsets.UTF_8.name())
}
