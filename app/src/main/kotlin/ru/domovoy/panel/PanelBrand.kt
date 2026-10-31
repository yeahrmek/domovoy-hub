package ru.domovoy.panel

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.domovoy.R

/** The lime and peach app mark identifies the panel and its device details. */
@Composable
internal fun PanelBrand(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(
            painter = painterResource(if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) R.drawable.ic_domovoy_logo_dark else R.drawable.ic_domovoy_logo),
            contentDescription = "Логотип Домового",
            modifier = Modifier.size(if (compact) 36.dp else 56.dp),
        )
        Text(
            text = "Домовой",
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
