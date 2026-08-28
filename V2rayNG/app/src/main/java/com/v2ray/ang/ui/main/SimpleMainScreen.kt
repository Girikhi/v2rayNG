package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.util.Utils

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
    val selectedServer = servers.firstOrNull { it.guid == uiState.selectedGuid }
        ?: servers.firstOrNull()
    val statusText = mainViewModel.formatStatus(uiState.status)
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(servers, uiState.selectedGuid) {
        val firstServer = servers.firstOrNull() ?: return@LaunchedEffect
        if (servers.none { it.guid == uiState.selectedGuid }) {
            onAction(MainAction.SelectServer(firstServer.guid))
        }
    }

    Scaffold(
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
                            contentDescription = stringResource(R.string.simple_manage_subscriptions)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            ConnectionPanel(
                isRunning = uiState.isRunning,
                isLoading = isLoading,
                canConnect = uiState.isRunning || selectedServer != null,
                statusText = statusText,
                serverName = selectedServer?.profile?.remarks,
                onToggle = { onAction(MainAction.ToggleService) }
            )
            Spacer(Modifier.height(28.dp))

            if (uiState.groups.isEmpty()) {
                EmptySubscriptionPanel(onAdd = { showAddDialog = true })
            } else {
                SubscriptionPanel(
                    groups = uiState.groups,
                    selectedGroupId = uiState.selectedGroupId,
                    servers = servers,
                    selectedServer = selectedServer,
                    isLoading = isLoading,
                    onSelectGroup = { onAction(MainAction.SelectGroup(it)) },
                    onSelectServer = { onAction(MainAction.SelectServer(it)) },
                    onAdd = { showAddDialog = true },
                    onRefresh = { onAction(MainAction.UpdateSubscriptions) }
                )
            }
            NavigationBarsSpacer(Modifier.height(24.dp))
        }
    }

    if (showAddDialog) {
        AddSubscriptionDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { remarks, url ->
                showAddDialog = false
                onAction(MainAction.AddSubscription(remarks, url))
            }
        )
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
        MaterialTheme.colorScheme.secondary
    }
    val buttonContentColor = if (isRunning) {
        MaterialTheme.colorScheme.onTertiary
    } else {
        MaterialTheme.colorScheme.onSecondary
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(116.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = canConnect && !isLoading,
                    onClick = onToggle
                ),
            shape = CircleShape,
            color = if (canConnect) buttonColor else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (canConnect) buttonContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            shadowElevation = if (canConnect) 8.dp else 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(42.dp),
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
                        modifier = Modifier.size(46.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
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
        Spacer(Modifier.height(6.dp))
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
private fun EmptySubscriptionPanel(onAdd: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_subscriptions_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(36.dp)
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
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.simple_add_subscription))
            }
        }
    }
}

@Composable
private fun SubscriptionPanel(
    groups: List<GroupMapItem>,
    selectedGroupId: String,
    servers: List<ServersCache>,
    selectedServer: ServersCache?,
    isLoading: Boolean,
    onSelectGroup: (String) -> Unit,
    onSelectServer: (String) -> Unit,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
) {
    val selectedGroup = groups.firstOrNull { it.id == selectedGroupId } ?: groups.first()
    var showGroups by remember { mutableStateOf(false) }
    var showServers by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.simple_connection_setup),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))

            Box {
                SelectorField(
                    label = stringResource(R.string.simple_subscription),
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
                            text = { Text(group.remarks, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                showGroups = false
                                onSelectGroup(group.id)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (servers.isNotEmpty()) {
                Box {
                    SelectorField(
                        label = stringResource(R.string.simple_server),
                        value = selectedServer?.profile?.remarks.orEmpty(),
                        supportingText = stringResource(R.string.simple_servers_available, servers.size),
                        enabled = servers.size > 1,
                        onClick = { showServers = true }
                    )
                    DropdownMenu(
                        expanded = showServers,
                        onDismissRequest = { showServers = false }
                    ) {
                        servers.forEach { server ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = server.profile.remarks,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    showServers = false
                                    onSelectServer(server.guid)
                                }
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Text(
                        text = stringResource(R.string.simple_no_servers),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.simple_add))
                }
                FilledTonalButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(painterResource(R.drawable.ic_restore_24dp), contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.simple_refresh))
                }
            }
        }
    }
}

@Composable
private fun SelectorField(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    supportingText: String? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (supportingText != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
private fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
) {
    var remarks by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var showUrlError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.simple_add_subscription)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.simple_add_subscription_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.simple_subscription_name)) },
                    placeholder = { Text(stringResource(R.string.simple_subscription_name_optional)) },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        showUrlError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.simple_subscription_url)) },
                    placeholder = { Text("https://example.com/sub") },
                    supportingText = {
                        Text(
                            if (showUrlError) {
                                stringResource(R.string.simple_subscription_url_required)
                            } else {
                                stringResource(R.string.simple_subscription_url_hint)
                            }
                        )
                    },
                    isError = showUrlError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cleanUrl = url.trim()
                if (!Utils.isValidUrl(cleanUrl) || !Utils.isValidSubUrl(cleanUrl)) {
                    showUrlError = true
                } else {
                    onAdd(remarks, cleanUrl)
                }
            }) {
                Text(stringResource(R.string.simple_add_and_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
