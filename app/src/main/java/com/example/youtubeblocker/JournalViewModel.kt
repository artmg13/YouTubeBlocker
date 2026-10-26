package com.example.youtubeblocker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.youtubeblocker.data.BlockerApplication
import com.example.youtubeblocker.data.Entry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModel(application: Application) : AndroidViewModel(application) {
    private val data = (application as BlockerApplication).data
    private val limit = MutableStateFlow(40)
    private val refresh = MutableStateFlow(0)
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val minimum = MutableStateFlow<Int?>(null)
    val settingsError = MutableStateFlow(false)
    val entries = combine(limit, refresh) { count, _ -> count }
        .flatMapLatest { count ->
            data.entries.observe(count + 1).map { rows -> JournalPage(rows.take(count), rows.size > count) }
                .onStart { error.value = null }
                .catch { error.value = "Не удалось прочитать журнал. Попробуйте ещё раз." }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JournalPage())

    init { loadSettings() }

    fun loadSettings() {
        viewModelScope.launch {
            settingsError.value = false
            try { data.minimum.collect { minimum.value = it } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { settingsError.value = true }
        }
    }

    fun more() { limit.value += 40 }
    fun retry() { refresh.value++ }

    fun saveMinimum(value: Int, done: () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try { data.setMinimum(value); done() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error.value = "Не удалось сохранить настройку. Попробуйте ещё раз." }
            finally { busy.value = false }
        }
    }

    fun clear() {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try { data.entries.clear(); limit.value = 40 }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error.value = "Не удалось очистить журнал. Попробуйте ещё раз." }
            finally { busy.value = false }
        }
    }
}

data class JournalPage(val rows: List<Entry> = emptyList(), val hasMore: Boolean = false)
