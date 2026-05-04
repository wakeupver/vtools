package com.omarea.vtools.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.AppRegistration
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omarea.vtools.R

data class OverviewNavItem(
    val id: Int,
    val titleRes: Int,
    val icon: ImageVector,
    val requiresRoot: Boolean
)

data class OverviewSection(
    val titleRes: Int,
    val items: List<OverviewNavItem>
)

@Composable
fun OverviewMenu(
    isRootAvailable: Boolean,
    onItemClick: (Int) -> Unit
) {
    val sections = listOf(
        OverviewSection(
            titleRes = R.string.menu_section_performance,
            items = listOf(
                OverviewNavItem(R.id.nav_core_control, R.string.menu_core_control, Icons.Outlined.Memory,          true),
                OverviewNavItem(R.id.nav_swap,         R.string.menu_swap,         Icons.Outlined.Storage,         true),
                OverviewNavItem(R.id.nav_processes,    R.string.menu_processes,    Icons.Outlined.Apps,            true),
                OverviewNavItem(R.id.nav_fps_chart,    R.string.menu_fps_chart,    Icons.Outlined.Speed,           true)
            )
        ),
        OverviewSection(
            titleRes = R.string.menu_section_power,
            items = listOf(
                OverviewNavItem(R.id.nav_charge,           R.string.menu_charge,           Icons.Outlined.BatteryChargingFull, false),
                OverviewNavItem(R.id.nav_power_utilization, R.string.menu_power_utilization, Icons.Outlined.Analytics,          false)
            )
        ),
        OverviewSection(
            titleRes = R.string.menu_section_advanced,
            items = listOf(
                OverviewNavItem(R.id.nav_applictions,  R.string.menu_applictions,  Icons.Outlined.AppRegistration, true),
                OverviewNavItem(R.id.nav_img,          R.string.menu_img,          Icons.Outlined.ImageSearch,     true),
                OverviewNavItem(R.id.nav_additional,   R.string.menu_sundry,       Icons.Outlined.Shield,          true),
                OverviewNavItem(R.id.nav_additional_all, R.string.menu_additional, Icons.Outlined.Terminal,        true),
                OverviewNavItem(R.id.nav_app_magisk,   R.string.menu_app_magisk,   Icons.Outlined.Extension,       true),
                OverviewNavItem(R.id.nav_miui_thermal, R.string.menu_miui_thermal, Icons.Outlined.Whatshot,        false),
                OverviewNavItem(R.id.nav_modules,      R.string.menu_modules,      Icons.Outlined.Build,           true)
            )
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        sections.forEach { section ->
            Text(
                text = stringResource(section.titleRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )
            section.items.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { item ->
                        OverviewMenuItem(
                            item = item,
                            enabled = isRootAvailable || !item.requiresRoot,
                            onClick = onItemClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun OverviewMenuItem(
    item: OverviewNavItem,
    enabled: Boolean,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .heightIn(min = 64.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled) { onClick(item.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.padding(PaddingValues(horizontal = 14.dp, vertical = 14.dp))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
