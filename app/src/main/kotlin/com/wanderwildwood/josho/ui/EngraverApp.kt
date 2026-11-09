package com.wanderwildwood.josho.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wanderwildwood.josho.Prefs
import com.wanderwildwood.josho.R
import com.wanderwildwood.josho.Turn
import com.wanderwildwood.josho.engine.Doc
import com.wanderwildwood.josho.engine.Folder
import com.wanderwildwood.josho.engine.LilyPond
import com.wanderwildwood.josho.engine.LocalFolder
import com.wanderwildwood.josho.engine.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen { Files, Editor, Settings }

/**
 * The whole app. The folder's scores; a score open in the editor; settings; About over any of them.
 * With no folder chosen yet, the app's own folder holds the sample, so it can be tried at once.
 */
@Composable
fun EngraverApp(
    autoCompile: Boolean,
    autoCompileScheme: Boolean = false,
    onTurn: (Turn) -> Unit = {},
    layout: String? = null,
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val lily = remember { LilyPond(context) }
    // Out of sight, the kept LilyPond goes (it holds memory the phone may want back); the next
    // compile, or opening a score, starts it again.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val watch = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) Thread { lily.stop() }.start()
        }
        lifecycle.addObserver(watch)
        onDispose { lifecycle.removeObserver(watch); Thread { lily.stop() }.start() }
    }
    val scope = rememberCoroutineScope()

    var folderUri by remember { mutableStateOf(prefs.folder) }
    val folder: Folder = remember(folderUri) { folderUri?.let { Workspace(context, it) } ?: LocalFolder(context) }
    var screen by remember { mutableStateOf(if (autoCompile) Screen.Editor else Screen.Files) }
    var about by remember { mutableStateOf(false) }
    var textSize by remember { mutableIntStateOf(prefs.textSize) }
    var turn by remember { mutableStateOf(prefs.turn) }
    var foldBars by remember { mutableStateOf(prefs.foldBars) }
    var scores by remember { mutableStateOf<List<Doc>?>(null) }
    var lost by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Doc?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val sample = remember { context.assets.open("sample.ly").bufferedReader().readText() }
    // A new score starts from the least LilyPond needs: a header, one note, layout and MIDI.
    val blank = remember { context.assets.open("new.ly").bufferedReader().readText() }

    LaunchedEffect(folder, reload) {
        scores = null
        val found = withContext(Dispatchers.IO) { runCatching { folder.scores() } }
        lost = found.isFailure
        var list = found.getOrDefault(emptyList())
        // The app's own folder always has the sample to try.
        if (folder is LocalFolder && list.isEmpty()) {
            withContext(Dispatchers.IO) { folder.create("sample.ly", sample) }
            list = withContext(Dispatchers.IO) { folder.scores() }
        }
        scores = list
        if (open == null && screen == Screen.Editor) {
            open = list.firstOrNull { it.path == prefs.lastScore } ?: list.firstOrNull()
            if (open == null) screen = Screen.Files
        }
    }

    val pick = rememberLauncherForActivityResult(FilesOpenTree()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            prefs.folder = uri
            prefs.lastScore = null
            folderUri = uri
            open = null
            screen = Screen.Files
        }
    }

    if (about) AboutDialog(onDismiss = { about = false })

    when (screen) {
        Screen.Files -> FilesScreen(
            title = stringResource(R.string.app_name),
            chosen = folderUri != null,
            scores = scores,
            lost = lost,
            onChoose = { pick.launch(null) },
            onOpen = { doc -> open = doc; prefs.lastScore = doc.path; screen = Screen.Editor },
            onNew = {
                scope.launch {
                    val doc = withContext(Dispatchers.IO) { runCatching { folder.create(context.getString(R.string.files_new_name), blank) }.getOrNull() }
                    reload++
                    if (doc != null) { open = doc; prefs.lastScore = doc.path; screen = Screen.Editor }
                }
            },
            onSettings = { screen = Screen.Settings },
        )
        Screen.Editor -> {
            val doc = open
            if (doc != null) {
                BackHandler { screen = Screen.Files; reload++ }
                EditorScreen(
                    doc = doc,
                    folder = folder,
                    lily = lily,
                    textSize = Prefs.TEXT_SIZES[textSize],
                    autoCompile = autoCompile,
                    foldBars = foldBars,
                    autoCompileScheme = autoCompileScheme,
                    layout = layout,
                    onBack = { screen = Screen.Files; reload++ },
                )
            }
        }
        Screen.Settings -> {
            BackHandler { screen = Screen.Files }
            SettingsScreen(
                folderName = if (folderUri == null) null else remember(folder) { runCatching { folder.title() }.getOrDefault("") },
                textSize = textSize,
                onTextSize = { textSize = it; prefs.textSize = it },
                turn = turn,
                onTurn = { turn = it; prefs.turn = it; onTurn(it) },
                foldBars = foldBars,
                onFoldBars = { foldBars = it; prefs.foldBars = it },
                onChooseFolder = { pick.launch(null) },
                onBack = { screen = Screen.Files },
                onAbout = { about = true },
            )
        }
    }
}
