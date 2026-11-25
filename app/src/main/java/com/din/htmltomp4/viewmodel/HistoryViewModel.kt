package com.din.htmltomp4.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.din.htmltomp4.data.ConversionHistoryItem
import com.din.htmltomp4.data.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Single shared history list for BOTH conversion directions (HTML→MP4 and
 * Video→HTML). Deliberately its own ViewModel rather than living inside
 * ConverterViewModel — that would leave VideoToHtmlViewModel's output with
 * nowhere to register, which is exactly why Video→HTML conversions weren't
 * showing up on the Dashboard before this.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(application)

    private val _history = MutableStateFlow(
        repository.load().filter { File(it.outputPath).exists() }
    )
    val history: StateFlow<List<ConversionHistoryItem>> = _history.asStateFlow()

    fun addItem(item: ConversionHistoryItem) {
        _history.value = listOf(item) + _history.value
        repository.save(_history.value)
    }

    fun deleteItem(item: ConversionHistoryItem) {
        runCatching { File(item.outputPath).delete() }
        _history.value = _history.value.filterNot { it.id == item.id }
        repository.save(_history.value)
    }
}
