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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.wanderwildwood.josho.engine.copyScore
import com.wanderwildwood.josho.engine.deleteScore
import com.wanderwildwood.josho.engine.nextFreeName
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
    // Kept when the screen is rebuilt: the editor then opens the last score again (below).
    var screen by rememberSaveable { mutableStateOf(if (autoCompile) Screen.Editor else Screen.Files) }
    var about by remember { mutableStateOf(false) }
    var textSize by remember { mutableIntStateOf(prefs.textSize) }
    var turn by remember { mutableStateOf(prefs.turn) }
    var foldBars by remember { mutableStateOf(prefs.foldBars) }
    var scores by remember { mutableStateOf<List<Doc>?>(null) }
    var lost by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Doc?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    // A score on the files page being copied: itself, and the names already in its folder.
    var copying by remember { mutableStateOf<Pair<Doc, List<String>>?>(null) }
    /** A copy or delete that did not work, said until it is pressed away. */
    var trouble by remember { mutableStateOf<Int?>(null) }
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
            val resolver = context.contentResolver
            resolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            // The app reaches one folder: the grant to any folder chosen before goes back.
            resolver.persistedUriPermissions.filter { it.uri != uri }.forEach { old ->
                runCatching {
                    resolver.releasePersistableUriPermission(
                        old.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
            prefs.folder = uri
            prefs.lastScore = null
            folderUri = uri
            open = null
            screen = Screen.Files
        }
    }

    if (about) AboutDialog(onDismiss = { about = false })
    trouble?.let { TroubleDialog(stringResource(it)) { trouble = null } }

    copying?.let { (doc, taken) ->
        ScoreNameDialog(
            title = stringResource(R.string.copy_title, doc.name),
            confirm = stringResource(R.string.menu_copy),
            initial = remember(doc.id, taken) { nextFreeName(doc.name, taken) },
            taken = taken,
            onDone = { name ->
                scope.launch {
                    val copy = withContext(Dispatchers.IO) { runCatching { copyScore(folder, doc, name) }.getOrNull() }
                    if (copy == null) trouble = R.string.copy_failed
                    reload++
                }
            },
            onDismiss = { copying = null },
        )
    }

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
            onCopy = { doc ->
                scope.launch {
                    val taken = withContext(Dispatchers.IO) { runCatching { folder.beside(doc).map { it.name } }.getOrDefault(emptyList()) }
                    copying = doc to taken
                }
            },
            onDelete = { doc ->
                scope.launch {
                    val gone = withContext(Dispatchers.IO) { runCatching { deleteScore(folder, doc) }.getOrDefault(false) }
                    if (!gone) trouble = R.string.delete_failed
                    if (prefs.lastScore == doc.path) prefs.lastScore = null
                    if (open?.id == doc.id) open = null
                    reload++
                }
            },
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
                    onSavedAs = { copy -> open = copy; prefs.lastScore = copy.path },
                    onDeleted = { open = null; prefs.lastScore = null; screen = Screen.Files; reload++ },
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
