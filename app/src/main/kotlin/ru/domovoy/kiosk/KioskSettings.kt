package ru.domovoy.kiosk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
internal fun KioskSettings(
    state: KioskState,
    onEnable: () -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit,
    onOpenApp: (String) -> Unit = {},
    onRemoveOwner: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Приложения и защита") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        when {
                            state.locked -> "Защита включена. Эти приложения доступны без PIN-кода. Для выхода нужен PIN-код планшета."
                            state.enabled -> "Защита включена, но ограничение приложений сейчас не действует."
                            else -> "Защита выключена. Другие приложения и настройки планшета доступны."
                        },
                    )
                    Column {
                        kioskApps.forEach { (packageName, name) ->
                            TextButton(onClick = { onOpenApp(packageName) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                                Text(name)
                            }
                        }
                    }
                    Text("Входящие звонки Domonap доступны без PIN-кода. Включение защиты и выход подтверждаются PIN-кодом блокировки экрана планшета.")
                    state.problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.deviceOwner) {
                        Text("Приложения, данные и PIN-код сохранятся. После снятия прав выберите домашний экран в настройках Android. Повторное включение потребует настройки через компьютер.")
                        TextButton(
                            onClick = onRemoveOwner,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                        ) {
                            Text("Снять управление планшетом")
                        }
                    }
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
