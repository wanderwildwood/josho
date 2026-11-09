package com.wanderwildwood.josho.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.josho.R
import com.wanderwildwood.josho.Turn

/** The settings: the folder, which way round, how large the editor's text is, and the bars while typing. No separator lines (house rule). */
@Composable
fun SettingsScreen(
    folderName: String?,
    textSize: Int,
    onTextSize: (Int) -> Unit,
    turn: Turn,
    onTurn: (Turn) -> Unit,
    foldBars: Boolean,
    onFoldBars: (Boolean) -> Unit,
    onChooseFolder: () -> Unit,
    onBack: () -> Unit,
    onAbout: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { Bar(stringResource(R.string.settings_title), onBack, null, onAbout) },
    ) { padding ->
        PagedColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onChooseFolder).padding(vertical = 8.dp)) {
                    TextMMD(text = stringResource(R.string.settings_folder), style = MaterialTheme.typography.bodyLarge)
                    TextMMD(text = folderName ?: stringResource(R.string.settings_folder_none), style = MaterialTheme.typography.labelSmall)
                }
            }
            item {
                // One press steps to the next way round, as Typewriter's row does.
                val all = Turn.entries
                Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onTurn(all[(turn.ordinal + 1) % all.size]) }.padding(vertical = 8.dp)) {
                    TextMMD(text = stringResource(R.string.settings_turn), style = MaterialTheme.typography.bodyLarge)
                    TextMMD(
                        text = stringResource(when (turn) {
                            Turn.UPRIGHT -> R.string.settings_turn_upright
                            Turn.ACROSS -> R.string.settings_turn_across
                            Turn.AROUND -> R.string.settings_turn_around
                            Turn.DEVICE -> R.string.settings_turn_device
                        }),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextMMD(text = stringResource(R.string.settings_text_size), style = MaterialTheme.typography.bodyLarge)
                    HouseChoiceRow(
                        options = listOf(
                            stringResource(R.string.settings_text_size_small),
                            stringResource(R.string.settings_text_size_medium),
                            stringResource(R.string.settings_text_size_house),
                            stringResource(R.string.settings_text_size_large),
                        ),
                        selected = textSize,
                        onSelect = onTextSize,
                        compact = true,
                    )
                    TextMMD(text = stringResource(R.string.settings_text_size_note), style = MaterialTheme.typography.labelSmall)
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextMMD(text = stringResource(R.string.settings_fold_bars), style = MaterialTheme.typography.bodyLarge)
                    HouseChoiceRow(
                        options = listOf(stringResource(R.string.settings_fold_bars_fold), stringResource(R.string.settings_fold_bars_stay)),
                        selected = if (foldBars) 0 else 1,
                        onSelect = { onFoldBars(it == 0) },
                        compact = true,
                    )
                    TextMMD(text = stringResource(R.string.settings_fold_bars_note), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
