package ru.domovoy.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One tile that only opens somebody else's app: its name, and one line saying either that it opens
 * or which package is missing.
 *
 * No switch, no slider and no age — there is nothing to drive and nothing that goes stale. The one
 * gesture is the tap, and it is *disabled* when the app is not installed rather than being accepted
 * and quietly doing nothing: a tile that looks tappable and swallows the tap is the dead tap this
 * tile exists not to be. The line under the name says which package would have to be there, so the
 * refusal comes with its reason.
 *
 * [now] is not a parameter and deliberately so — every other tile takes one to work out its age,
 * and this one has no reading to age.
 */
@Composable
fun LauncherTile(
    tile: LauncherTileState,
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit = {},
) {
    Card(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable(enabled = tile.openable) { onOpen(tile.packageName) },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(tile.name, style = MaterialTheme.typography.titleMedium)
            Text(text = statusLine(tile), style = MaterialTheme.typography.bodySmall)
        }
    }
}
