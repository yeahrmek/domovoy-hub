package ru.domovoy.panel

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** Wall names for the apps a fresh panel shows; any other app uses its installed label. */
private val DEFAULT_QUICK_APPS =
    linkedMapOf(
        "com.yandex.iot" to "Дом с Алисой",
        "com.google.android.apps.bard" to "Gemini",
        "com.openai.chatgpt" to "ChatGPT",
    )

/**
 * The household's own header row: which apps, in which order. Every app in it is also allowed in
 * protected mode, without PIN, by the household's explicit choice (docs/kiosk.md).
 */
class QuickAppStore(private val preferences: SharedPreferences) {
    private val state = MutableStateFlow(load())
    val packages: StateFlow<List<String>> = state.asStateFlow()

    /** Adds an app to the end of the row, or removes it if it is already shown. */
    fun toggle(packageName: String) {
        val current = state.value
        save(if (packageName in current) current - packageName else current + packageName)
    }

    fun reorder(packages: List<String>) {
        // A drag only permutes; ignore a stale order that would add or drop apps.
        if (packages.toSet() == state.value.toSet()) save(packages.distinct())
    }

    private fun load(): List<String> = preferences.getString(KEY, null)
        ?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() }
        ?: DEFAULT_QUICK_APPS.keys.toList()

    private fun save(packages: List<String>) {
        state.value = packages
        preferences.edit().putString(KEY, Json.encodeToString(packages)).apply()
    }

    private companion object {
        const val KEY = "packages"
    }
}

/** [label] is the installed app's label, or null when the tablet cannot open the package. */
fun quickApps(packages: List<String>, label: (String) -> String?): List<LauncherTileState> = packages.map { packageName ->
    val installed = label(packageName)
    LauncherTileState(
        packageName = packageName,
        name = DEFAULT_QUICK_APPS[packageName] ?: installed ?: packageName,
        room = null,
        openable = installed != null,
    )
}

internal fun <T> List<T>.moved(from: Int, to: Int): List<T> = if (from == to) {
    this
} else {
    toMutableList().apply { add(to, removeAt(from)) }
}

data class InstalledApp(val packageName: String, val name: String)

/** Apps with a launcher icon, visible through the manifest's MAIN/LAUNCHER `<queries>` intent. */
internal fun installedApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.name.lowercase() }
}

internal fun installedLabel(context: Context, packageName: String): String? {
    val pm = context.packageManager
    if (pm.getLaunchIntentForPackage(packageName) == null) return null
    return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
}

@Composable
internal fun QuickAppPicker(
    installed: List<InstalledApp>,
    shown: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Приложения в строке") },
        text = {
            if (installed.isEmpty()) {
                Text("Не удалось получить список приложений планшета.")
            } else {
                LazyColumn(Modifier.heightIn(max = 560.dp)) {
                    items(installed, key = { it.packageName }) { app ->
                        val checked = app.packageName in shown
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MIN_TOUCH)
                                .clickable { onToggle(app.packageName) }
                                .semantics(mergeDescendants = true) {
                                    role = Role.Checkbox
                                    toggleableState = ToggleableState(checked)
                                },
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(app.name, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH)) { Text("Готово") }
        },
    )
}
