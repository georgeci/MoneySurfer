package com.georgeci.moneysurfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

data class DebugLogEntry(
    val severity: Severity,
    val tag: String,
    val message: String,
    val throwable: Throwable?,
)

object DebugErrors {
    private const val MaxEntries = 100

    private var installed = false
    private val _entries = MutableStateFlow<List<DebugLogEntry>>(emptyList())

    val entries: StateFlow<List<DebugLogEntry>> = _entries

    fun installKermitWriter() {
        if (!isDebugBinary() || installed) return

        installed = true
        Logger.addLogWriter(DebugErrorsLogWriter)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private fun record(entry: DebugLogEntry) {
        _entries.value = listOf(entry) + _entries.value.take(MaxEntries - 1)
    }

    private object DebugErrorsLogWriter : LogWriter() {
        override fun isLoggable(tag: String, severity: Severity): Boolean {
            return severity >= Severity.Warn
        }

        override fun log(
            severity: Severity,
            message: String,
            tag: String,
            throwable: Throwable?,
        ) {
            record(
                DebugLogEntry(
                    severity = severity,
                    tag = tag,
                    message = message,
                    throwable = throwable,
                ),
            )
        }
    }
}

@Composable
fun DebugErrorOverlay(content: @Composable () -> Unit) {
    if (!isDebugBinary()) {
        content()
        return
    }

    val entries by DebugErrors.entries.collectAsState()
    var isOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        content()

        if (entries.isNotEmpty()) {
            FloatingActionButton(
                onClick = { isOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Text("ERR ${entries.size}")
            }
        }
    }

    if (isOpen) {
        AlertDialog(
            onDismissRequest = { isOpen = false },
            title = { Text("Kermit warnings/errors") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(entries) { entry ->
                        DebugLogEntryRow(entry)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isOpen = false }) {
                    Text("Close")
                }
            },
            dismissButton = {
                TextButton(onClick = DebugErrors::clear) {
                    Text("Clear")
                }
            },
        )
    }
}

@Composable
private fun DebugLogEntryRow(entry: DebugLogEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = entry.severity.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = entry.tag.ifBlank { "NoTag" },
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Text(entry.message, style = MaterialTheme.typography.bodySmall)

        entry.throwable?.let { throwable ->
            Text(
                text = throwable.stackTraceToString(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }

        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalNativeApi::class)
private fun isDebugBinary(): Boolean = Platform.isDebugBinary
