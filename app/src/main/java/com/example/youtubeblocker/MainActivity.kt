package com.example.youtubeblocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.youtubeblocker.data.Entry
import com.example.youtubeblocker.protection.ProtectionStatus
import com.example.youtubeblocker.ui.theme.YouTubeBlockerTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { YouTubeBlockerTheme { JournalApp() } }
    }
}

private fun Entry.formatted(pattern: String): String = SimpleDateFormat(pattern, Locale.forLanguageTag("ru")).apply {
    timeZone = TimeZone.getTimeZone(zoneId)
}.format(Date(confirmedAt))

@Composable
private fun JournalApp(model: JournalViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(ProtectionStatus.enabled(context)) }
    var installed by remember { mutableStateOf(ProtectionStatus.youtubeInstalled(context)) }
    val connected by ProtectionStatus.connected.collectAsStateWithLifecycle()
    val failure by ProtectionStatus.failure.collectAsStateWithLifecycle()
    var settings by rememberSaveable { mutableStateOf(false) }
    var setup by rememberSaveable { mutableStateOf(false) }
    var clearDialog by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Entry?>(null) }
    val page by model.entries.collectAsStateWithLifecycle()
    val minimum by model.minimum.collectAsStateWithLifecycle()
    val settingsError by model.settingsError.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = ProtectionStatus.enabled(context)
                installed = ProtectionStatus.youtubeInstalled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(settings) { settings = false }

    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (settings) "Настройки" else "Журнал", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { settings = !settings }) { Text(if (settings) "Готово" else "Настройки") }
            }
            if (settings) {
                Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("Пауза перед просмотром", style = MaterialTheme.typography.titleLarge)
                    Text("Чем подробнее причина, тем больше времени подумать о цели входа.")
                    if (minimum != null) {
                        MinimumSetting(minimum!!, busy) { value -> model.saveMinimum(value) { settings = false } }
                    } else if (settingsError) {
                        Text("Не удалось прочитать настройку.")
                        OutlinedButton(onClick = model::loadSettings) { Text("Повторить") }
                    } else { CircularProgressIndicator() }
                    HorizontalDivider()
                    ProtectionCard(enabled, connected, installed, failure) { setup = true }
                    TextButton(onClick = { ProtectionStatus.openSettings(context) }) {
                        Text("Системные настройки защиты")
                    }
                    Text("Ваши причины хранятся только на этом устройстве. При удалении приложения журнал удалится.",
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { clearDialog = true }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) { Text("Очистить журнал") }
                }
            } else {
                LazyColumn(Modifier.widthIn(max = 680.dp).fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        ProtectionCard(enabled, connected, installed, failure) { setup = true }
                    }
                    if (page.rows.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 64.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Каждый вход — с целью", style = MaterialTheme.typography.headlineSmall)
                                Text("Здесь появятся причины ваших входов в YouTube.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    itemsIndexed(page.rows, key = { _, row -> row.id }) { index, row ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val day = row.formatted("d MMMM yyyy")
                            if (index == 0 || day != page.rows[index - 1].formatted("d MMMM yyyy")) {
                                Text(day, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedCard(onClick = { selected = row }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(row.formatted("HH:mm"), style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary)
                                    Text(row.reason, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    if (page.hasMore) item {
                        OutlinedButton(onClick = model::more, modifier = Modifier.fillMaxWidth()) { Text("Показать ещё") }
                    }
                }
            }
        }
    }
    if (setup) AlertDialog(
        onDismissRequest = { setup = false },
        title = { Text("Включите защиту") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("В настройках специальных возможностей выберите «Осознанный YouTube» и включите сервис.")
            Text("Он определяет активное приложение и показывает вопрос поверх YouTube. Содержимое экранов не читается, причины остаются на устройстве.")
            Text("Защита рассчитана на полноэкранный YouTube. Браузер, разделённый экран и «картинка в картинке» не поддерживаются. Перед вопросом YouTube может ненадолго появиться или начать воспроизведение.")
            Text("Если Android запрещает включить сервис после установки APK: откройте сведения об этом приложении → меню ⋮ → «Разрешить ограниченные настройки», если этот пункт доступен.")
        } },
        confirmButton = { TextButton(onClick = { setup = false; ProtectionStatus.openSettings(context) }) { Text("Открыть настройки") } },
        dismissButton = { TextButton(onClick = { setup = false }) { Text("Позже") } },
    )
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text("Удалить все записи?") },
        text = { Text("Это действие нельзя отменить.") },
        confirmButton = { TextButton(onClick = { clearDialog = false; model.clear() }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Отмена") } })
    selected?.let { row ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(row.formatted("d MMMM yyyy, HH:mm")) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(row.zoneId, style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(row.reason) }
            } }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Закрыть") } })
    }
    error?.let { message ->
        AlertDialog(onDismissRequest = { model.error.value = null }, title = { Text("Не удалось выполнить действие") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { model.error.value = null; model.retry() }) { Text("Понятно") } })
    }
}

@Composable
private fun ProtectionCard(enabled: Boolean, connected: Boolean, installed: Boolean, failure: String?, onSetup: () -> Unit) {
    val active = enabled && connected && failure == null && installed
    Card(colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(when {
                !installed -> "YouTube не установлен"
                failure != null -> "Защита недоступна"
                active -> "Защита включена"
                enabled -> "Сервис не подключён"
                else -> "Защита не включена"
            }, style = MaterialTheme.typography.titleMedium)
            Text(failure ?: if (active) "Перед новым входом в YouTube появится вопрос о вашей цели."
                else if (enabled) "Откройте системные настройки и переподключите сервис."
                else "Добавьте короткую паузу, чтобы решить, зачем открывать YouTube.",
                style = MaterialTheme.typography.bodyMedium)
            if (!active) TextButton(onClick = onSetup) { Text(if (enabled) "Проверить настройки" else "Включить защиту") }
        }
    }
}

@Composable
private fun MinimumSetting(current: Int, busy: Boolean, save: (Int) -> Unit) {
    var draft by rememberSaveable(current) { mutableStateOf(current.toString()) }
    val value = draft.toIntOrNull()
    val valid = value != null && value in ReasonRules.MINIMUM..ReasonRules.MAXIMUM
    OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("Минимальная длина причины") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
        isError = !valid, supportingText = { Text(if (valid) "От 50 до 5000. Пробелы и переносы строк не учитываются."
            else "Введите целое число от 50 до 5000") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = { value?.let(save) }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) {
        Text(if (busy) "Сохранение…" else "Сохранить")
    }
}
