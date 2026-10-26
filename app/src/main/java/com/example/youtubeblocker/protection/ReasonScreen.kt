package com.example.youtubeblocker.protection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.youtubeblocker.ReasonRules

@Composable
fun ReasonScreen(text: String, minimum: Int?, saving: Boolean, error: String?,
                 onText: (String) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit,
                 onRetry: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("ПАУЗА ПЕРЕД ПРОСМОТРОМ", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text("Зачем вы открываете YouTube?", style = MaterialTheme.typography.headlineMedium)
                Text("Опишите, что хотите посмотреть и зачем вам это сейчас.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (minimum == null) {
                    if (error == null) CircularProgressIndicator()
                    else {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = onRetry) { Text("Повторить") }
                    }
                } else {
                    val length = ReasonRules.length(text)
                    val tooLong = text.codePointCount(0, text.length) > ReasonRules.MAX_TEXT
                    OutlinedTextField(value = text, onValueChange = onText, enabled = !saving,
                        label = { Text("Моя причина") }, minLines = 5, maxLines = 10,
                        isError = tooLong, modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(when {
                                tooLong -> "Не больше 10 000 символов вместе с пробелами"
                                length < minimum -> "$length из $minimum символов · без пробелов"
                                else -> "Минимальная длина достигнута · $length символов"
                            })
                        })
                    if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onConfirm, enabled = !saving && ReasonRules.valid(text, minimum),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (saving) "Сохранение…" else "Открыть YouTube")
                    }
                }
                TextButton(onClick = onCancel, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Передумал")
                }
            }
        }
    }
}
