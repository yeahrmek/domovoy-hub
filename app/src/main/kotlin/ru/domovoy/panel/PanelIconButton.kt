package ru.domovoy.panel

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Quiet utility actions use the same wall-sized target as device power. */
@Composable
internal fun PanelIconButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.size(MIN_TOUCH).semantics {
            contentDescription = label
            if (busy) stateDescription = "Обновление выполняется"
            if (busy) liveRegion = LiveRegionMode.Polite
        },
        shape = RoundedCornerShape(10.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(26.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, strokeWidth = 2.dp)
        } else {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(26.dp))
        }
    }
}
