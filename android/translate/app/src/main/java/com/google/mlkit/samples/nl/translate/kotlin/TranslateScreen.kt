package com.google.mlkit.samples.nl.translate.kotlin

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.nl.translate.TranslateLanguage

@Composable
fun TranslateScreen(viewModel: TranslateViewModel) {
    val sourceText by viewModel.sourceText.observeAsState("")
    val translatedTextState by viewModel.translatedText.observeAsState()
    val availableModels by viewModel.availableModels.observeAsState(emptyList())
    val selectedSourceLang by viewModel.sourceLang.observeAsState()
    val selectedTargetLang by viewModel.targetLang.observeAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = sourceText,
                onValueChange = { viewModel.sourceText.postValue(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                placeholder = { Text("Enter source text") },
                label = { Text("Source Text") }
            )
            IconButton(
                onClick = { 
                    selectedSourceLang?.let { viewModel.speak(sourceText, it) }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp, end = 8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Speak Source")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LanguageSelector(
                languages = viewModel.availableLanguages,
                selectedLanguage = selectedSourceLang,
                onLanguageSelected = { viewModel.sourceLang.value = it },
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = {
                val tempLang = viewModel.sourceLang.value
                viewModel.sourceLang.value = viewModel.targetLang.value
                viewModel.targetLang.value = tempLang
                
                val currentTargetText = translatedTextState?.result ?: ""
                viewModel.sourceText.value = currentTargetText
            }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Switch Languages")
            }

            LanguageSelector(
                languages = viewModel.availableLanguages,
                selectedLanguage = selectedTargetLang,
                onLanguageSelected = { viewModel.targetLang.value = it },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ModelToggleButton(
                language = selectedSourceLang,
                isDownloaded = !viewModel.requiresModelDownload(selectedSourceLang ?: TranslateViewModel.Language("en"), availableModels),
                onDownload = { viewModel.downloadLanguage(it) },
                onDelete = { viewModel.deleteLanguage(it) }
            )
            ModelToggleButton(
                language = selectedTargetLang,
                isDownloaded = !viewModel.requiresModelDownload(selectedTargetLang ?: TranslateViewModel.Language("es"), availableModels),
                onDownload = { viewModel.downloadLanguage(it) },
                onDelete = { viewModel.deleteLanguage(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloaded Models: ${availableModels.joinToString()}",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                val intent = Intent("com.android.settings.TTS_SETTINGS")
                context.startActivity(intent)
            }) {
                Icon(Icons.Default.Settings, contentDescription = "TTS Settings")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp),
            elevation = 4.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp).padding(bottom = 40.dp)) {
                    Text(
                        text = translatedTextState?.result ?: if (translatedTextState?.error != null) "Error: ${translatedTextState?.error?.localizedMessage}" else "",
                        style = MaterialTheme.typography.body1
                    )
                }
                IconButton(
                    onClick = { 
                        selectedTargetLang?.let { lang ->
                            translatedTextState?.result?.let { text ->
                                viewModel.speak(text, lang)
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Speak Target")
                }
            }
        }
    }
}

@Composable
fun LanguageSelector(
    languages: List<TranslateViewModel.Language>,
    selectedLanguage: TranslateViewModel.Language?,
    onLanguageSelected: (TranslateViewModel.Language) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(text = selectedLanguage?.toString() ?: "Select Language")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languages.forEach { language ->
                DropdownMenuItem(onClick = {
                    onLanguageSelected(language)
                    expanded = false
                }) {
                    Text(text = language.toString())
                }
            }
        }
    }
}

@Composable
fun ModelToggleButton(
    language: TranslateViewModel.Language?,
    isDownloaded: Boolean,
    onDownload: (TranslateViewModel.Language) -> Unit,
    onDelete: (TranslateViewModel.Language) -> Unit
) {
    language?.let {
        Button(
            onClick = {
                if (isDownloaded) onDelete(it) else onDownload(it)
            },
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (isDownloaded) MaterialTheme.colors.secondary else MaterialTheme.colors.primary
            )
        ) {
            Text(if (isDownloaded) "Delete ${it.code}" else "Download ${it.code}")
        }
    }
}
