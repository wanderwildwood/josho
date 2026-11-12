package com.wanderwildwood.josho.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.wanderwildwood.josho.R
import com.wanderwildwood.josho.engine.NameProblem
import com.wanderwildwood.josho.engine.nameProblem
import com.wanderwildwood.josho.engine.scoreName

/**
 * A short menu of things to do, dropped from what it sits beside in a Box: white, a thin black
 * rim, no shadow, and MMD's menu under it, which neither animates nor ripples. Its rows are
 * [MenuRow]s, with the dotted rule between them (STYLE.md).
 */
@Composable
fun HouseMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenuMMD(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
        content = content,
    )
}

/** One row of a [HouseMenu]; all but the [first] draw the rule above them. Grayed when not [enabled]. */
@Composable
fun MenuRow(label: String, first: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    if (!first) DottedRule(start = 12.dp)
    val ink = MaterialTheme.colorScheme.onSurface
    DropdownMenuItemMMD(
        text = {
            TextMMD(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) ink else ink.copy(alpha = 0.38f),
            )
        },
        onClick = onClick,
        enabled = enabled,
    )
}

/**
 * A row that asks before it acts (STYLE.md, "the row asks, not a dialog"): the first tap changes
 * what it says to [armedLabel], the second does it. It stays armed until the person does
 * something else, and everything else here closes the menu, which forgets the question.
 */
@Composable
fun ArmedMenuRow(label: String, armedLabel: String, first: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    MenuRow(if (armed) armedLabel else label, first = first, enabled = enabled) {
        if (armed) onClick() else armed = true
    }
}

/**
 * Asks for the name of a new score (Save as, Copy). [initial] comes with its stem selected, so
 * typing replaces "song 2" and leaves ".ly"; a name typed without the ending gets it. The
 * button is off for a name that is empty or among [taken], and a line under the field says which.
 */
@Composable
fun ScoreNameDialog(
    title: String,
    confirm: String,
    initial: String,
    taken: List<String>,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initial) {
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.substringBeforeLast('.').length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val problem = nameProblem(value.text, taken)
    val commit: () -> Unit = {
        if (problem == null) {
            onDismiss()
            onDone(scoreName(value.text))
        }
    }
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        TextFieldMMD(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            singleLine = true,
            // Done on the keyboard finishes it: the keyboard up over a small panel covers the
            // dialog's own buttons.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { commit() }),
        )
        if (problem != null) {
            TextMMD(
                text = stringResource(
                    when (problem) {
                        NameProblem.Empty -> R.string.name_empty
                        NameProblem.Taken -> R.string.name_taken
                        NameProblem.Unusable -> R.string.name_unusable
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HouseButton(stringResource(R.string.cancel), onDismiss, Modifier.weight(1f))
            HouseButton(confirm, commit, Modifier.weight(1f), on = true, enabled = problem == null)
        }
    }
}
