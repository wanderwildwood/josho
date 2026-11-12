package com.wanderwildwood.josho.ui

import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Spacer
import android.view.ViewTreeObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.josho.R
import com.wanderwildwood.josho.engine.Doc
import com.wanderwildwood.josho.engine.EditHistory
import com.wanderwildwood.josho.engine.Folder
import com.wanderwildwood.josho.engine.LilyPond
import com.wanderwildwood.josho.engine.LogLine
import com.wanderwildwood.josho.engine.copyScore
import com.wanderwildwood.josho.engine.deleteScore
import com.wanderwildwood.josho.engine.nextFreeName
import com.wanderwildwood.josho.engine.offsetOf
import com.wanderwildwood.josho.engine.parseLog
import com.wanderwildwood.josho.engine.pdfBeside
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Pane { Source, Score, Log }

/** The keys LilyPond leans on that a soft keyboard hides on its second and third pages. */
private val SYMBOLS = listOf("\\", "{", "}", "<", ">", "'", ",", "~", "(", ")", "[", "]", "|", "#", "%")

/**
 * A score open: its source, the engraved pages, LilyPond's messages. Held across, or on a wide
 * screen (the Supernotes, anything 600 dp across), source and score sit side by side, with the
 * log under the source's tab; a Kompakt held upright shows one pane at a time, by the tabs.
 * Side by side, the Score button folds the score away so the source has the whole width, and a
 * compile brings it back. With [foldBars], the title bar and the Compile row fold away while the
 * on-screen keyboard is up, and come back when it goes down.
 *
 * The three dots in the title bar open the one menu: Undo and Redo for the source, Save as
 * (the copy is then the score being edited, [onSavedAs]), Delete ([onDeleted], after the score
 * and what compiling left beside it are gone), and the engraved PDF to another app or a printer.
 */
@Composable
fun EditorScreen(
    doc: Doc,
    folder: Folder,
    lily: LilyPond,
    textSize: Int,
    autoCompile: Boolean,
    foldBars: Boolean = true,
    autoCompileScheme: Boolean = false,
    /** Debug only: "side" or "tabs" forces a layout, for comparing them in screenshots. */
    layout: String? = null,
    onSavedAs: (Doc) -> Unit = {},
    onDeleted: () -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember(doc.id) { mutableStateOf<TextFieldValue?>(null) }
    var savedText by remember(doc.id) { mutableStateOf("") }
    var pane by remember(doc.id) { mutableStateOf(Pane.Source) }
    var status by remember(doc.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var log by remember(doc.id) { mutableStateOf<List<LogLine>>(emptyList()) }
    var pdf by remember(doc.id) { mutableStateOf<File?>(null) }
    var pdfStamp by remember(doc.id) { mutableStateOf(0L) }
    var page by remember(doc.id) { mutableIntStateOf(0) }
    var scoreShown by remember(doc.id) { mutableStateOf(true) }
    var midi by remember(doc.id) { mutableStateOf<File?>(null) }
    // The score on the whole screen, opened by a tap on it; Back returns to the editor.
    var full by remember(doc.id) { mutableStateOf(false) }
    // What was typed, to take back and put again; another score starts with none.
    val history = remember(doc.id) { EditHistory() }
    var menu by remember(doc.id) { mutableStateOf(false) }
    // The PDF beside the score, from this compile or an earlier day's: what Open in and Print hand on.
    var engraved by remember(doc.id) { mutableStateOf<Doc?>(null) }
    var compiles by remember(doc.id) { mutableIntStateOf(0) }
    // Save as is asking for a name: the names already in the folder, which it may not take.
    var naming by remember(doc.id) { mutableStateOf<List<String>?>(null) }
    val prints = remember { canPrint(context) }
    val focus = remember { FocusRequester() }
    // The on-screen keyboard up: the reader is typing, and the bars give the source their room.
    val keyboardUp = rememberKeyboardUp()
    val folded = foldBars && keyboardUp

    // The kept LilyPond starts while the score is being read, so the first compile is quick too.
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { lily.warmUp() } }

    LaunchedEffect(doc.id) {
        val read = withContext(Dispatchers.IO) { runCatching { folder.read(doc) }.getOrDefault("") }
        savedText = read
        history.reset(EditHistory.Snapshot(read))
        text = TextFieldValue(read)
    }

    LaunchedEffect(doc.id, compiles) {
        engraved = withContext(Dispatchers.IO) { runCatching { pdfBeside(folder, doc) }.getOrNull() }
    }

    /** Every change to the source comes through here, so it can be taken back. */
    fun edit(v: TextFieldValue) {
        history.record(EditHistory.Snapshot(v.text, v.selection.start, v.selection.end), SystemClock.uptimeMillis())
        text = v
    }

    fun restore(step: EditHistory.Snapshot?) {
        if (step != null) text = TextFieldValue(step.text, TextRange(step.start, step.end))
    }

    fun save() {
        val t = text?.text ?: return
        if (t == savedText) return
        runCatching { folder.write(doc, t) }.onSuccess { savedText = t }
    }

    fun compile(wide: Boolean) {
        val t = text?.text ?: return
        if (busy) return
        busy = true
        scope.launch {
            status = context.getString(if (lily.isReady()) R.string.compiling else R.string.setting_up)
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    if (t != savedText) folder.write(doc, t)
                    val setUp = lily.prepare()
                    if (setUp > 0) Log.i(TAG, "set up in $setUp ms")
                    val source = folder.mirror(doc)
                    val result = lily.compile(source.parentFile!!, source.name, autoCompileScheme)
                    result.pdf?.let { folder.putBeside(doc, it, "application/pdf") }
                    result.midi?.let { folder.putBeside(doc, it, "audio/midi") }
                    Log.i(TAG, "compile: exit ${result.exit} in ${result.millis} ms; pdf ${result.pdf?.length() ?: "missing"}")
                    result
                }
            }
            busy = false
            compiles++
            // Written before compiling, whatever came of it after.
            if (outcome.isSuccess || outcome.exceptionOrNull() !is java.io.IOException) savedText = t
            outcome.onFailure {
                Log.e(TAG, "compile failed", it)
                status = context.getString(R.string.compile_could_not_start, it.message ?: it.javaClass.simpleName)
            }
            outcome.onSuccess { r ->
                log = parseLog(r.log)
                val seconds = "%.1f".format(r.millis / 1000.0)
                val warned = log.any { it.kind == LogLine.Kind.Warning }
                status = when {
                    r.exit != 0 -> context.getString(R.string.compile_failed)
                    warned -> context.getString(R.string.compiled_with_warnings, seconds)
                    else -> context.getString(R.string.compiled_in, seconds)
                }
                if (r.pdf != null) { pdf = r.pdf; pdfStamp = r.pdf.lastModified(); page = 0; scoreShown = true; midi = r.midi }
                pane = when {
                    r.exit != 0 || r.pdf == null -> Pane.Log
                    wide -> Pane.Source
                    else -> Pane.Score
                }
            }
        }
    }

    fun jumpTo(line: LogLine) {
        val t = text ?: return
        val at = offsetOf(t.text, line.line, line.column)
        edit(t.copy(selection = TextRange(at)))
        pane = Pane.Source
        runCatching { focus.requestFocus() }
    }

    fun askName() {
        scope.launch {
            naming = withContext(Dispatchers.IO) { runCatching { folder.beside(doc).map { it.name } }.getOrDefault(emptyList()) }
        }
    }

    // What is in the editor now goes into the copy; the original stays as it was last saved.
    fun saveAs(name: String) {
        val t = text?.text ?: return
        scope.launch {
            val copy = withContext(Dispatchers.IO) { runCatching { copyScore(folder, doc, name, t) }.getOrNull() }
            if (copy != null) onSavedAs(copy) else status = context.getString(R.string.save_as_failed)
        }
    }

    fun delete() {
        scope.launch {
            val gone = withContext(Dispatchers.IO) { runCatching { deleteScore(folder, doc) }.getOrDefault(false) }
            if (gone) onDeleted() else status = context.getString(R.string.delete_failed)
        }
    }

    val shownPdf = pdf
    if (full && shownPdf != null) {
        BackHandler { full = false }
        FullScore(doc.name, shownPdf, pdfStamp, page, { page = it }, midi, onClose = { full = false })
        return
    }

    naming?.let { taken ->
        ScoreNameDialog(
            title = stringResource(R.string.menu_save_as),
            confirm = stringResource(R.string.name_save),
            initial = remember(taken) { nextFreeName(doc.name, taken) },
            taken = taken,
            onDone = ::saveAs,
            onDismiss = { naming = null },
        )
    }

    // The system Back leaves the way the bar's arrow does: what was typed is saved first.
    BackHandler { save(); onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (!folded) Bar(doc.name, { save(); onBack() }, null, null) {
                Box {
                    BarButton(Icons.More, stringResource(R.string.cd_more)) { menu = true }
                    HouseMenu(menu, { menu = false }) {
                        MenuRow(stringResource(R.string.menu_undo), first = true, enabled = history.canUndo) {
                            menu = false
                            restore(history.undo())
                        }
                        MenuRow(stringResource(R.string.menu_redo), enabled = history.canRedo) {
                            menu = false
                            restore(history.redo())
                        }
                        MenuRow(stringResource(R.string.menu_save_as), enabled = text != null) {
                            menu = false
                            askName()
                        }
                        val pdfDoc = engraved
                        MenuRow(stringResource(R.string.menu_open_in), enabled = pdfDoc != null) {
                            menu = false
                            if (pdfDoc != null && !openElsewhere(context, pdfDoc)) status = context.getString(R.string.no_pdf_app)
                        }
                        if (prints) {
                            MenuRow(stringResource(R.string.menu_print), enabled = pdfDoc != null) {
                                menu = false
                                if (pdfDoc != null && !printPdf(context, pdfDoc, doc.name.substringBeforeLast('.'))) {
                                    status = context.getString(R.string.print_failed)
                                }
                            }
                        }
                        // Not while LilyPond is at work on it: the PDF would land beside nothing.
                        ArmedMenuRow(stringResource(R.string.menu_delete), stringResource(R.string.menu_delete_armed), enabled = !busy) {
                            menu = false
                            delete()
                        }
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            // Held across (a Kompakt across is 533 x 320 dp) or 600 dp wide (the Supernotes):
            // source and score side by side. Upright on a phone: one pane at a time, by tabs.
            // Decided by the screen, not by this box: the keyboard coming up shrinks the box to
            // wider than tall, and switching to side by side then rebuilt the text field, which
            // lost focus and closed the keyboard again the moment it opened.
            val screenConf = LocalConfiguration.current
            val landscape = screenConf.screenWidthDp > screenConf.screenHeightDp
            val wide = when (layout) { "side" -> true; "tabs" -> false; else -> screenConf.screenWidthDp >= 600 || landscape }
            // Short of height (a Kompakt across): one header row, and the symbol keys in two
            // short rows under the source, so the score keeps the column's full height.
            val across = landscape && screenConf.screenHeightDp < 480
            LaunchedEffect(text != null, autoCompile) { if (autoCompile && text != null) compile(wide) }
            val keys = Modifier.onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) false
                else when (e.key) {
                    Key.Enter, Key.M -> { compile(wide); true }   // Ctrl+M is Frescobaldi's Engrave
                    Key.S -> { save(); true }
                    Key.Z -> { restore(if (e.isShiftPressed) history.redo() else history.undo()); true }
                    Key.Y -> { restore(history.redo()); true }
                    else -> false
                }
            }
            val tabs = if (wide) listOf(Pane.Source, Pane.Log) else Pane.entries
            val labels = tabs.map {
                stringResource(when (it) { Pane.Source -> R.string.tab_source; Pane.Score -> R.string.tab_score; Pane.Log -> R.string.tab_log })
            }
            val shown = if (wide && pane == Pane.Score) Pane.Source else pane
            val statusText = status.ifEmpty { if ((text?.text ?: savedText) != savedText) stringResource(R.string.unsaved) else "" }
            val scoreButton: @Composable () -> Unit = {
                if (wide) HouseButton(stringResource(R.string.score_button), { scoreShown = !scoreShown }, on = scoreShown, compact = true)
            }
            Column(keys.fillMaxSize()) {
                if (folded) {
                    // Typing: no bars. They come back when the keyboard goes down.
                } else if (across) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HouseButton(stringResource(R.string.compile), { compile(wide) }, on = true, enabled = !busy && text != null, compact = true)
                        scoreButton()
                        StoneTabs(labels, tabs.indexOf(shown), { pane = tabs[it] }, Modifier.weight(1.4f), rule = false)
                        TextMMD(text = statusText, style = MaterialTheme.typography.labelSmall, maxLines = 2, modifier = Modifier.weight(1f))
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface))
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HouseButton(stringResource(R.string.compile), { compile(wide) }, on = true, enabled = !busy && text != null)
                        scoreButton()
                        TextMMD(text = statusText, style = MaterialTheme.typography.labelSmall, maxLines = 2, modifier = Modifier.weight(1f))
                    }
                }
                val sourceSide: @Composable (Modifier) -> Unit = { m ->
                    Column(m) {
                        if (!across && !folded) StoneTabs(labels, tabs.indexOf(shown), { pane = tabs[it] })
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (shown) {
                                Pane.Source -> SourcePane(
                                    text, ::edit, textSize, focus,
                                    keyboardUp = keyboardUp,
                                    // Side by side, a tap on the source folds the score away, as
                                    // the Score button does; a compile brings it back.
                                    onTouch = { if (wide && scoreShown) scoreShown = false },
                                    symbols = when {
                                        // Held across beside the score: one row of eight, and
                                        // the rest a press away; with the score folded away
                                        // the source has the width for all fifteen.
                                        across && wide && scoreShown -> Symbols.OneRowPaged
                                        across && wide -> Symbols.OneRow
                                        wide -> Symbols.None
                                        across -> Symbols.OneRow
                                        else -> Symbols.TwoRows
                                    },
                                )
                                Pane.Log -> LogPane(log, doc.name, ::jumpTo)
                                Pane.Score -> ScorePane(pdf, pdfStamp, page, { page = it }, onOpen = { full = true })
                            }
                        }
                    }
                }
                // One shape whether the score is beside the source or folded away, so the source
                // (and the text field in it) stays the same one: rebuilt, it would lose focus and
                // close the keyboard that the tap folding the score had just opened.
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    sourceSide(Modifier.weight(1f).fillMaxHeight())
                    if (wide && scoreShown) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.onSurface))
                        // Half and half when held across: the symbol keys need the source's width.
                        Box(Modifier.weight(if (across) 1f else 1.3f).fillMaxHeight()) { ScorePane(pdf, pdfStamp, page, { page = it }, onOpen = { full = true }) }
                    }
                }
            }
        }
    }
}

private enum class Symbols { None, OneRow, OneRowPaged, TwoRows }

/** The source: plain monospace text, no suggestions, no capitals; under it, on a phone, the symbol keys. */
@Composable
private fun SourcePane(
    value: TextFieldValue?,
    onValue: (TextFieldValue) -> Unit,
    textSize: Int,
    focus: FocusRequester,
    symbols: Symbols,
    keyboardUp: Boolean,
    onTouch: () -> Unit,
) {
    if (value == null) return
    val keyboard = LocalSoftwareKeyboardController.current
    // A symbol key types into the source without bringing the keyboard up: with the keyboard
    // down, the reader is placing symbols and wants the screen.
    val insert: (TextFieldValue) -> Unit = { v ->
        val wasUp = keyboardUp
        onValue(v)
        if (!wasUp) keyboard?.hide()
    }
    Column(Modifier.fillMaxSize()) {
        // The touch is only watched on its way past (the Initial pass, nothing consumed): the
        // text field under it still gets the tap that places the cursor and raises the keyboard.
        val touched by androidx.compose.runtime.rememberUpdatedState(onTouch)
        Box(
            Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); touched() }
            },
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValue,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = textSize.sp, color = Color.Black),
                cursorBrush = SolidColor(Color.Black),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp).focusRequester(focus).textActions(),
            )
        }
        // The on-screen keyboard is up: it has these symbols on its own pages, and the source
        // needs every line it can get (held across, it had one).
        if (keyboardUp) return@Column
        // A full keyboard attached (the Manta's, or one on the Kompakt held across) has these
        // keys already; the room goes to the source instead. The Kompakt alone reports
        // "nokeys", so it keeps them.
        val configuration = LocalConfiguration.current
        val hardKeyboard = configuration.keyboard == Configuration.KEYBOARD_QWERTY &&
            configuration.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
        if (hardKeyboard) return@Column
        if (symbols == Symbols.OneRowPaged) {
            // Eight keys and a "more" key that turns to the other seven and back.
            var second by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (if (second) SYMBOLS.drop(8) else SYMBOLS.take(8)).forEach { SymbolKey(it, value, insert, Modifier.weight(1f)) }
                val more = stringResource(R.string.cd_more_symbols)
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).border(1.dp, MaterialTheme.colorScheme.onSurface)
                        .clickable { second = !second }.semantics { contentDescription = more },
                    contentAlignment = Alignment.Center,
                ) {
                    TextMMD(text = "⋯", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                // The second page has seven: an empty place keeps the keys the same width.
                if (second) Spacer(Modifier.weight(1f))
            }
        }
        if (symbols == Symbols.OneRow) {
            // Held across, all fifteen fit on one row and leave the height for the text.
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                SYMBOLS.forEach { SymbolKey(it, value, insert, Modifier.weight(1f)) }
            }
        }
        if (symbols == Symbols.TwoRows) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                SYMBOLS.take(8).forEach { SymbolKey(it, value, insert, Modifier.weight(1f)) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                SYMBOLS.drop(8).forEach { SymbolKey(it, value, insert, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SymbolKey(symbol: String, value: TextFieldValue, onValue: (TextFieldValue) -> Unit, modifier: Modifier) {
    val said = stringResource(R.string.cd_insert, symbol)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .border(1.dp, MaterialTheme.colorScheme.onSurface)
            .clickable {
                val start = minOf(value.selection.start, value.selection.end)
                val end = maxOf(value.selection.start, value.selection.end)
                val t = value.text.replaceRange(start, end, symbol)
                onValue(TextFieldValue(t, TextRange(start + symbol.length)))
            }
            .semantics { contentDescription = said },
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(text = symbol, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
    }
}

/** LilyPond's messages; a line that points into this score takes the reader there. */
@Composable
private fun LogPane(log: List<LogLine>, fileName: String, onJump: (LogLine) -> Unit) {
    if (log.isEmpty()) {
        TextMMD(text = stringResource(R.string.log_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(20.dp))
        return
    }
    PagedColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp), showCutRow = true) {
        items(log.size) { i ->
            val line = log[i]
            val here = line.file != null && (line.file == fileName || line.file.endsWith("/$fileName"))
            TextMMD(
                text = line.text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (line.kind == LogLine.Kind.Plain) FontWeight.Normal else FontWeight.Bold,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (here) Modifier.clickable { onJump(line) } else Modifier)
                    .padding(vertical = if (here) 10.dp else 3.dp),
            )
        }
    }
}

/**
 * The engraved score, pushed to black and white, drawn the full width of the pane. A page that is
 * taller than the pane (an A4 page on the Kompakt) is read a screen at a time, each screen
 * repeating the last strip of the one before, as PagedColumn reads a tall row; Next goes on to
 * the next page after its last screen. Nothing scrolls.
 */
@Composable
private fun ScorePane(pdf: File?, stamp: Long, page: Int, onPage: (Int) -> Unit, onOpen: (() -> Unit)? = null) {
    if (pdf == null) {
        TextMMD(text = stringResource(R.string.no_score_yet), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(20.dp))
        return
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Which screen of the page; -1 means the last one (after turning back from the next page).
    var screen by remember(pdf, stamp, page) { mutableIntStateOf(if (backwards) -1 else 0) }
    val pages by produceState(0, pdf, stamp) { value = withContext(Dispatchers.IO) { pageCount(pdf) } }
    val bitmap by produceState<android.graphics.Bitmap?>(null, pdf, stamp, page, size.width) {
        value = if (size.width == 0) null else withContext(Dispatchers.IO) { renderPage(pdf, page, size.width) }
    }
    val bmp = bitmap
    val screens = if (bmp == null || size.height == 0) 1 else screenStarts(bmp.height, size.height).size
    val at = if (screen < 0) screens - 1 else screen.coerceIn(0, screens - 1)
    val slice = remember(bmp, at, size) {
        if (bmp == null || size.height == 0) null else {
            val top = screenStarts(bmp.height, size.height)[at]
            android.graphics.Bitmap.createBitmap(bmp, 0, top, bmp.width, minOf(size.height, bmp.height - top))
        }
    }
    fun next() { if (at < screens - 1) screen = at + 1 else if (page < pages - 1) { backwards = false; onPage(page + 1) } }
    fun previous() { if (at > 0) screen = at - 1 else if (page > 0) { backwards = true; onPage(page - 1) } }
    Column(Modifier.fillMaxSize()) {
        val open = stringResource(R.string.cd_score_full)
        Box(
            Modifier.weight(1f).fillMaxWidth().onSizeChanged { size = it }
                .then(if (onOpen != null) Modifier.clickable(onClick = onOpen).semantics { contentDescription = open } else Modifier),
        ) {
            slice?.let {
                Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
            }
        }
        if (pages > 1 || screens > 1) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HouseButton(stringResource(R.string.page_previous), ::previous, enabled = page > 0 || at > 0, compact = true)
                TextMMD(
                    text = stringResource(R.string.page_of, page + 1, pages.coerceAtLeast(1)),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                HouseButton(stringResource(R.string.page_next), ::next, enabled = page < pages - 1 || at < screens - 1, compact = true)
            }
        }
    }
}

/**
 * The score on the whole screen: the title bar with Back, a Play button when LilyPond wrote MIDI
 * (the score has a midi block), and the pages, turned as in the pane. Played by Android's own
 * MIDI synthesizer; nothing leaves the phone. Leaving stops the sound.
 */
@Composable
private fun FullScore(name: String, pdf: File, stamp: Long, page: Int, onPage: (Int) -> Unit, midi: File?, onClose: () -> Unit) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    // The volume keys turn the media volume here, as in a music player, before Play as well.
    val activity = LocalContext.current as? android.app.Activity
    DisposableEffect(activity) {
        val before = activity?.volumeControlStream
        activity?.volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        onDispose { if (before != null) activity.volumeControlStream = before }
    }
    // Stopping and releasing happen off the main thread: without a working audio output
    // MediaPlayer.stop() can block, and the screen must still answer Back.
    fun stop() {
        val p = player ?: return
        player = null
        Thread { runCatching { p.stop() }; runCatching { p.release() } }.start()
    }
    DisposableEffect(midi) { onDispose { stop() } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { Bar(name, onClose, null, null) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (midi != null) {
                    val playing = player != null
                    HouseButton(stringResource(if (playing) R.string.stop else R.string.play), {
                        if (playing) stop() else player = runCatching {
                            MediaPlayer().apply {
                                setAudioAttributes(
                                    android.media.AudioAttributes.Builder()
                                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                        .build(),
                                )
                                setDataSource(midi.path)
                                setOnPreparedListener { if (player === it) it.start() }
                                setOnCompletionListener { if (player === it) stop() }
                                setOnErrorListener { mp, what, extra -> Log.e(TAG, "play error $what/$extra"); if (player === mp) stop(); true }
                                prepareAsync()
                            }
                        }.onFailure { Log.e(TAG, "play failed", it) }.getOrNull()
                    }, on = true, compact = true)
                } else {
                    TextMMD(text = stringResource(R.string.no_midi), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface))
            Box(Modifier.weight(1f).fillMaxWidth()) { ScorePane(pdf, stamp, page, onPage) }
        }
    }
}

/** Set when the page was turned back, so the new page opens on its last screen. */
private var backwards = false

/** Where each screen of a page [pageHeight] tall starts, in a pane [paneHeight] tall; screens overlap by an eighth. */
internal fun screenStarts(pageHeight: Int, paneHeight: Int): List<Int> {
    if (pageHeight <= paneHeight || paneHeight <= 0) return listOf(0)
    val step = (paneHeight * 7 / 8).coerceAtLeast(1)
    val out = mutableListOf<Int>()
    var top = 0
    while (top + paneHeight < pageHeight) { out += top; top += step }
    out += pageHeight - paneHeight
    return out
}

private const val TAG = "Josho"

/**
 * Whether the on-screen keyboard is showing, asked of the window itself after every layout. The
 * app is not drawn edge to edge (the window is resized for the keyboard instead), and then
 * Compose's own keyboard inset stays at zero; the window's insets still say.
 */
@Composable
private fun rememberKeyboardUp(): Boolean {
    val view = LocalView.current
    var up by remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            up = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return up
}
