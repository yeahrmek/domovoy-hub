package ru.domovoy.kiosk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun KioskSettings(state: KioskState, onEnable: () -> Unit, onExit: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Защита панели") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    when {
                        state.locked -> "Планшет закреплён за панелью. Выход и ручное открытие приложений требуют PIN-кода или отпечатка."
                        state.enabled -> "Защита включена, но ограничение приложений сейчас не действует."
                        else -> "Защита выключена. Другие приложения и настройки планшета доступны."
                    },
                )
                Text("Входящие звонки Domonap доступны без проверки. Для выхода используется PIN-код блокировки экрана планшета или зарегистрированный отпечаток.")
                state.problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        },
        confirmButton = {
            if (state.enabled || state.locked) {
                TextButton(onClick = onExit, modifier = Modifier.heightIn(min = 64.dp)) { Text("Выйти из панели") }
            } else {
                TextButton(onClick = onEnable, enabled = state.problem == null, modifier = Modifier.heightIn(min = 64.dp)) { Text("Включить защиту") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 64.dp)) { Text("Закрыть") }
        },
    )
}
