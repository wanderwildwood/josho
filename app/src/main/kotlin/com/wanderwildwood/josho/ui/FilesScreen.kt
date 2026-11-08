package com.wanderwildwood.josho.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.josho.R
import com.wanderwildwood.josho.engine.Doc

/** The top bar of the home and settings screens: a title, Back where there is one, cog and info. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(title: String, onBack: (() -> Unit)?, onSettings: (() -> Unit)?, onAbout: () -> Unit, extra: @Composable () -> Unit = {}) {
    TopAppBarMMD(
        title = { TextMMD(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (onBack != null) BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
        actions = {
            extra()
            if (onSettings != null) BarButton(Icons.Settings, stringResource(R.string.cd_settings), onSettings)
            BarButton(Icons.Info, stringResource(R.string.cd_about), onAbout)
        },
    )
}

/** The folder's scores. With none chosen yet, the sample in the app's own folder, and the way to choose one. */
@Composable
fun FilesScreen(
    title: String,
    chosen: Boolean,
    scores: List<Doc>?,
    lost: Boolean,
    onChoose: () -> Unit,
    onOpen: (Doc) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { Bar(title, null, onSettings, onAbout) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!chosen || lost) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextMMD(
                        text = stringResource(if (lost) R.string.files_lost else R.string.files_none),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    HouseButton(stringResource(R.string.files_choose), onChoose, Modifier.fillMaxWidth(), on = true)
                }
            } else {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    HouseButton(stringResource(R.string.files_new), onNew, Modifier.fillMaxWidth())
                }
            }
            when {
                scores == null -> TextMMD(
                    text = stringResource(R.string.files_reading),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp),
                )
                scores.isEmpty() && chosen && !lost -> TextMMD(
                    text = stringResource(R.string.files_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp),
                )
                else -> PagedColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 12.dp),
                    showCutRow = true,
                ) {
                    items(scores.size) { i ->
                        val doc = scores[i]
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(doc) }.padding(vertical = 12.dp),
                        ) {
                            TextMMD(text = doc.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (doc.path != doc.name) {
                                TextMMD(text = doc.path.substringBeforeLast('/'), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (i < scores.lastIndex) DottedRule()
                    }
                }
            }
        }
    }
}
