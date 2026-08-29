package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer

@Immutable
private data class SimpleGroupItem(
    val id: String,
    val remarks: String,
)

@Immutable
private data class SimpleServerItem(
    val guid: String,
    val name: String,
    val delayMillis: Long,
)

private val SimpleLightColorScheme = lightColorScheme(
    primary = Color(0xFF536D7A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6EA),
    onPrimaryContainer = Color(0xFF263A43),
    secondary = Color(0xFF667A83),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E9EC),
    onSecondaryContainer = Color(0xFF2C3D44),
    tertiary = Color(0xFF55766B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9E8E1),
    onTertiaryContainer = Color(0xFF263C35),
    error = Color(0xFF9A4C4C),
    background = Color(0xFFF4F6F5),
    onBackground = Color(0xFF202A2E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202A2E),
    surfaceVariant = Color(0xFFE8EEEC),
    onSurfaceVariant = Color(0xFF56646A),
    outline = Color(0xFF839197),
    outlineVariant = Color(0xFFD5DEDB),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FAF9),
    surfaceContainer = Color(0xFFF0F3F2),
    surfaceContainerHigh = Color(0xFFEAEFEC),
    surfaceContainerHighest = Color(0xFFE4EAE7),
)

@Composable
fun SimpleMainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onManageSubscriptions: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val serversFlow = remember(uiState.selectedGroupId) {
        mainViewModel.serversForGroup(uiState.selectedGroupId)
    }
    val servers by serversFlow.collectAsStateWithLifecycle()
    val groupItems = remember(uiState.groups) {
        uiState.groups.map { SimpleGroupItem(id = it.id, remarks = it.remarks) }
    }
    val serverItems = remember(servers) {
        servers.map {
            SimpleServerItem(
                guid = it.guid,
                name = it.profile.remarks,
                delayMillis = it.testDelayMillis,
            )
        }
    }
    val selectedServer = serverItems.firstOrNull { it.guid == uiState.selectedGuid }
        ?: serverItems.firstOrNull()
    val firstServerGuid = serverItems.firstOrNull()?.guid
    val selectedGuidIsVisible = uiState.selectedGuid != null &&
        serverItems.any { it.guid == uiState.selectedGuid }
    val statusText = mainViewModel.formatStatus(uiState.status)

    LaunchedEffect(firstServerGuid, selectedGuidIsVisible) {
        if (!selectedGuidIsVisible && firstServerGuid != null) {
            onAction(MainAction.SelectServer(firstServerGuid))
        }
    }

    MaterialTheme(
        colorScheme = SimpleLightColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            topBar = {
                AppTopBar(
                    title = stringResource(R.string.app_name),
                    onBackClick = {},
                    isLoading = isLoading,
                    navigationIcon = {},
                    actions = {
                        IconButton(onClick = onManageSubscriptions) {
                            Icon(
                                painter = painterResource(R.drawable.ic_subscriptions_24dp),
                                contentDescription = stringResource(
                                    R.string.simple_manage_subscriptions
                                )
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings_24dp),
                                contentDescription = stringResource(R.string.title_settings)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = 10.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    ConnectionPanel(
                        isRunning = uiState.isRunning,
                        isLoading = isLoading,
                        canConnect = uiState.isRunning || selectedServer != null,
                        statusText = statusText,
                        serverName = selectedServer?.name,
                        onToggle = { onAction(MainAction.ToggleService) }
                    )
                }
                item {
                    QuickActionsRow(
                        hasServers = serverItems.isNotEmpty(),
                        hasSubscriptions = groupItems.isNotEmpty(),
                        isLoading = isLoading,
                        isTesting = uiState.isTesting,
                        onClipboard = {
                            onAction(MainAction.AddSubscriptionFromClipboard)
                        },
                        onQrCode = { onAction(MainAction.AddSubscriptionFromQrCode) },
                        onPing = { onAction(MainAction.TestRealAllServers) },
                        onRefresh = { onAction(MainAction.UpdateSubscriptions) }
                    )
                }

                if (groupItems.isEmpty()) {
                    item { EmptySubscriptionPanel() }
                } else {
                    item {
                        SubscriptionPanel(
                            groups = groupItems,
                            selectedGroupId = uiState.selectedGroupId,
                            onSelectGroup = { onAction(MainAction.SelectGroup(it)) },
                        )
                    }

                    if (serverItems.isEmpty()) {
                        item { EmptyServersPanel() }
                    } else {
                        item {
                            ServerSectionHeader(
                                serverCount = serverItems.size,
                                isTesting = uiState.isTesting,
                            )
                        }
                        items(serverItems, key = { it.guid }) { server ->
                            ServerRow(
                                server = server,
                                selected = server.guid == selectedServer?.guid,
                                onSelect = { onAction(MainAction.SelectServer(server.guid)) },
                            )
                        }
                    }
                }

                item { NavigationBarsSpacer(Modifier.height(14.dp)) }
            }
        }
    }
}

@Composable
private fun ConnectionPanel(
    isRunning: Boolean,
    isLoading: Boolean,
    canConnect: Boolean,
    statusText: String,
    serverName: String?,
    onToggle: () -> Unit,
) {
    val buttonColor = if (isRunning) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val buttonContentColor = if (isRunning) {
        MaterialTheme.colorScheme.onTertiary
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = canConnect && !isLoading,
                    onClick = onToggle
                ),
            shape = CircleShape,
            color = if (canConnect) {
                buttonColor
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = if (canConnect) {
                buttonContentColor
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            shadowElevation = if (canConnect) 2.dp else 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = buttonContentColor,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(
                            if (isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp
                        ),
                        contentDescription = stringResource(
                            if (isRunning) R.string.acc_stop else R.string.acc_start
                        ),
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(
                when {
                    isLoading -> R.string.simple_preparing
                    isRunning -> R.string.simple_connected
                    canConnect -> R.string.simple_tap_to_connect
                    else -> R.string.simple_add_subscription_first
                }
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = serverName ?: statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickActionsRow(
    hasServers: Boolean,
    hasSubscriptions: Boolean,
    isLoading: Boolean,
    isTesting: Boolean,
    onClipboard: () -> Unit,
    onQrCode: () -> Unit,
    onPing: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AddSubscriptionButton(
            modifier = Modifier.weight(1f),
            onClipboard = onClipboard,
            onQrCode = onQrCode
        )
        FilledTonalButton(
            onClick = onPing,
            enabled = hasServers && !isLoading && !isTesting,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_speed_24dp),
                    contentDescription = null
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(if (isTesting) R.string.simple_testing else R.string.simple_ping),
                maxLines = 1
            )
        }
        FilledTonalButton(
            onClick = onRefresh,
            enabled = hasSubscriptions && !isLoading && !isTesting,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_restore_24dp), contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.simple_refresh), maxLines = 1)
        }
    }
}

@Composable
private fun EmptySubscriptionPanel() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_subscriptions_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.simple_no_subscription),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.simple_no_subscription_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SubscriptionPanel(
    groups: List<SimpleGroupItem>,
    selectedGroupId: String,
    onSelectGroup: (String) -> Unit,
) {
    val selectedGroup = groups.firstOrNull { it.id == selectedGroupId } ?: groups.first()
    var showGroups by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.simple_subscription),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Box {
                SelectorField(
                    value = selectedGroup.remarks,
                    enabled = groups.size > 1,
                    onClick = { showGroups = true }
                )
                DropdownMenu(
                    expanded = showGroups,
                    onDismissRequest = { showGroups = false }
                ) {
                    groups.forEach { group ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    group.remarks,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                showGroups = false
                                onSelectGroup(group.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyServersPanel() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = stringResource(R.string.simple_no_servers),
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ServerSectionHeader(
    serverCount: Int,
    isTesting: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.simple_servers),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.simple_configs_count, serverCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isTesting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ServerRow(
    server: SimpleServerItem,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onSelect),
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(6.dp))
            Text(
                text = server.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(12.dp))
            DelayText(server.delayMillis)
        }
    }
}

@Composable
private fun AddSubscriptionButton(
    modifier: Modifier = Modifier,
    onClipboard: () -> Unit,
    onQrCode: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.simple_add), maxLines = 1)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.simple_clipboard)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_copy), contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onClipboard()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.simple_qr_code)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_scan_24dp), contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onQrCode()
                }
            )
        }
    }
}

@Composable
private fun SelectorField(
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (enabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DelayText(delayMillis: Long) {
    Text(
        text = delayLabel(delayMillis),
        style = MaterialTheme.typography.bodySmall,
        color = when {
            delayMillis > 0L -> MaterialTheme.colorScheme.tertiary
            delayMillis < 0L -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1
    )
}

@Composable
private fun delayLabel(delayMillis: Long): String = when {
    delayMillis > 0L -> stringResource(R.string.server_test_delay_value, delayMillis)
    delayMillis < 0L -> stringResource(R.string.simple_ping_failed)
    else -> stringResource(R.string.simple_not_tested)
}
