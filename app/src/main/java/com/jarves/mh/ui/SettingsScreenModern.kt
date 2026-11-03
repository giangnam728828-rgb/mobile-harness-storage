package com.jarves.mh.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarves.mh.BuildConfig
import com.jarves.mh.access.ScreenReaderService
import com.jarves.mh.model.PluginManifest
import com.jarves.mh.model.PluginToolRisk
import com.jarves.mh.data.ApiKeyInfo
import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.DEEPSEEK_HARNESS_PROVIDERS
import com.jarves.mh.model.DSH_PROTOCOL_PROVIDERS
import com.jarves.mh.model.DevStack
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.ProviderProfile
import com.jarves.mh.model.providersForAgent
import com.jarves.mh.network.ConnectionValidation
import com.jarves.mh.network.DiscoveredModel
import com.jarves.mh.network.ModelDiscoveryResult
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.res.stringResource
import com.jarves.mh.R
import com.jarves.mh.runtime.AntigravityAuthStatus
import com.jarves.mh.ui.theme.AppLanguage
import com.jarves.mh.ui.theme.AppThemeMode
import com.jarves.mh.ui.theme.PocketOrange
import kotlinx.coroutines.launch

private enum class SettingsSection { APPEARANCE, LANGUAGE, TOOLS, RUNTIME, PLUGINS, SCREEN_READER, SCREEN_CONTROL, DIAGNOSTICS, BACKUP, UPDATE_CHANNEL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppUiState,
    onSaveProvider: (ProviderProfile, String) -> Unit,
    onDiscoverModels: suspend (ProviderProfile, String) -> ModelDiscoveryResult,
    onValidateProvider: suspend (ProviderProfile, String, List<DiscoveredModel>) -> ConnectionValidation,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetLanguage: (String) -> Unit = {},
    onPing: () -> Unit,
    onClearTerminal: () -> Unit,
    getSavedApiKey: (ProviderKind) -> String,
    getSavedApiKeys: (ProviderKind) -> List<ApiKeyInfo>,
    onAddApiKey: (ProviderKind, String, String) -> List<ApiKeyInfo>,
    onActivateApiKey: (ProviderKind, String) -> List<ApiKeyInfo>,
    onRemoveApiKey: (ProviderKind, String) -> List<ApiKeyInfo>,
    onInstallDevStack: (DevStack) -> Unit = {},
    onRemoveDevStack: (DevStack) -> Unit = {},
    onInstallAgent: (AgentKind) -> Unit = {},
    onCheckAgentUpdates: () -> Unit = {},
    onUpdateAgent: (AgentKind) -> Unit = {},
    onStartAntigravityLogin: () -> Unit = {},
    onSubmitAntigravityCode: (String) -> Unit = {},
    onLogoutAntigravity: () -> Unit = {},
    onRefreshAntigravityModels: () -> Unit = {},
    onSetAntigravityModel: (String) -> Unit = {},
    onSetAntigravityEffort: (String) -> Unit = {},
    initialDebugUpdateManifestUrl: String = "",
    onSetDebugUpdateManifestUrl: (String) -> Unit = {},
    onClearDebugUpdateManifestUrl: () -> Unit = {},
    onRefreshDiagnostics: () -> Unit = {},
    onStopInactiveProcesses: () -> Unit = {},
    onClearRuntimeCache: () -> Unit = {},
    showBackupDot: Boolean = false,
    onBackupOpened: () -> Unit = {},
    openBackup: Boolean = false,
    onTogglePlugin: (String) -> Unit = {},
    onDeletePlugin: (String) -> Unit = {},
    onAddPlugin: (String) -> Unit = {},
    onRunPluginTool: (String, String) -> Unit = { _, _ -> },
    onShareScreenContext: (Boolean) -> Unit = {},
    onCleanEmptyProjects: () -> Unit = {},
    onShareScreenControl: (Boolean) -> Unit = {},
    onAddScreenBlock: (String) -> Unit = {},
    onRemoveScreenBlock: (String) -> Unit = {},
    onScreenBlocklist: () -> List<String> = { emptyList() },
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    LaunchedEffect(openBackup) {
        if (openBackup) {
            expanded = SettingsSection.BACKUP
            onBackupOpened()
        }
    }
    var terminalCleared by remember { mutableStateOf(false) }
    var showReliabilityHelp by rememberSaveable { mutableStateOf(false) }
    var stackPendingRemoval by remember { mutableStateOf<DevStack?>(null) }

    stackPendingRemoval?.let { stack ->
        AlertDialog(
            onDismissRequest = { stackPendingRemoval = null },
            title = { Text(stringResource(R.string.tools_remove_dialog_title, stack.localizedLabel())) },
            text = {
                Text(stringResource(R.string.tools_remove_dialog_desc))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        stackPendingRemoval = null
                        onRemoveDevStack(stack)
                    },
                ) { Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { stackPendingRemoval = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    fun toggle(section: SettingsSection) {
        expanded = if (expanded == section) null else section
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(top = 8.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(9.dp),
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                                    shape = RoundedCornerShape(9.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.settings_subtitle), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            item {
                SettingsAccordion(
                    title = stringResource(R.string.settings_appearance),
                    subtitle = when (state.themeMode) {
                        AppThemeMode.DARK -> stringResource(R.string.theme_dark_subtitle)
                        AppThemeMode.LIGHT -> stringResource(R.string.theme_light_subtitle)
                        AppThemeMode.SYSTEM -> stringResource(R.string.theme_system_subtitle)
                    },
                    icon = Icons.Default.Tune,
                    expanded = expanded == SettingsSection.APPEARANCE,
                    onClick = { toggle(SettingsSection.APPEARANCE) },
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModernThemeChoice(stringResource(R.string.theme_dark), Icons.Default.DarkMode, state.themeMode == AppThemeMode.DARK, { onSetThemeMode(AppThemeMode.DARK) }, Modifier.weight(1f))
                        ModernThemeChoice(stringResource(R.string.theme_light), Icons.Default.LightMode, state.themeMode == AppThemeMode.LIGHT, { onSetThemeMode(AppThemeMode.LIGHT) }, Modifier.weight(1f))
                        ModernThemeChoice(stringResource(R.string.theme_system), Icons.Default.PhoneAndroid, state.themeMode == AppThemeMode.SYSTEM, { onSetThemeMode(AppThemeMode.SYSTEM) }, Modifier.weight(1f))
                    }
                }
            }

            item {
                SettingsAccordion(
                    title = stringResource(R.string.backup_title),
                    subtitle = stringResource(R.string.backup_subtitle),
                    icon = Icons.Default.Backup,
                    expanded = expanded == SettingsSection.BACKUP,
                    onClick = {
                        toggle(SettingsSection.BACKUP)
                        onBackupOpened()
                    },
                    showDot = showBackupDot,
                ) {
                    BackupRestoreSection(
                        busy = state.isRunning || state.projectTerminalRunning || state.androidBuildRunning ||
                            state.projectExportRunning || state.agentInstalling != null || state.devStackInstalling != null,
                    )
                }
            }

            item {
                SettingsAccordion(
                    title = stringResource(R.string.diagnostics_title),
                    subtitle = stringResource(R.string.diagnostics_subtitle),
                    icon = Icons.Default.Memory,
                    expanded = expanded == SettingsSection.DIAGNOSTICS,
                    onClick = {
                        val opening = expanded != SettingsSection.DIAGNOSTICS
                        toggle(SettingsSection.DIAGNOSTICS)
                        if (opening) onRefreshDiagnostics()
                    },
                ) {
                    DeveloperDiagnosticsContent(
                        state = state,
                        onRefresh = onRefreshDiagnostics,
                        onStopInactiveProcesses = onStopInactiveProcesses,
                        onClearRuntimeCache = onClearRuntimeCache,
                    )
                }
            }

            item {
                val pluginCount = state.plugins.count { it.enabled }
                SettingsAccordion(
                    title = stringResource(R.string.plugins_title),
                    subtitle = if (state.plugins.isEmpty()) {
                        stringResource(R.string.plugins_subtitle_empty)
                    } else {
                        stringResource(R.string.plugins_subtitle_count, pluginCount, state.plugins.size)
                    },
                    icon = Icons.Default.Extension,
                    expanded = expanded == SettingsSection.PLUGINS,
                    onClick = { toggle(SettingsSection.PLUGINS) },
                ) {
                    PluginsSection(
                        plugins = state.plugins,
                        onToggle = onTogglePlugin,
                        onDelete = onDeletePlugin,
                        onAdd = onAddPlugin,
                        onRun = onRunPluginTool,
                        runningToolId = state.pluginRunToolId,
                        runTitle = state.pluginRunTitle,
                        runOutput = state.pluginRunOutput,
                    )
                }
            }

            item {
                val screenReaderOn = ScreenReaderService.isEnabled(context)
                SettingsAccordion(
                    title = stringResource(R.string.screen_reader_title),
                    subtitle = if (screenReaderOn) {
                        stringResource(R.string.screen_reader_subtitle_on)
                    } else {
                        stringResource(R.string.screen_reader_subtitle_off)
                    },
                    icon = Icons.Default.Visibility,
                    expanded = expanded == SettingsSection.SCREEN_READER,
                    onClick = { toggle(SettingsSection.SCREEN_READER) },
                ) {
                    ScreenReaderSection(
                        shareWithAgent = state.screenContextForAgent,
                        onShareWithAgent = onShareScreenContext,
                    )
                }
            }

            item {
                SettingsAccordion(
                    title = stringResource(R.string.screen_control_title),
                    subtitle = if (state.screenControlEnabled) {
                        stringResource(R.string.screen_control_subtitle_on)
                    } else {
                        stringResource(R.string.screen_control_subtitle_off)
                    },
                    icon = Icons.Default.TouchApp,
                    expanded = expanded == SettingsSection.SCREEN_CONTROL,
                    onClick = { toggle(SettingsSection.SCREEN_CONTROL) },
                ) {
                    ScreenControlSection(
                        state = state,
                        onToggleMaster = onShareScreenControl,
                        onAddBlock = onAddScreenBlock,
                        onRemoveBlock = onRemoveScreenBlock,
                        blocklist = onScreenBlocklist(),
                    )
                }
            }

            item {
                val currentLang = AppLanguage.fromCode(state.languageCode)
                val langSubtitle = if (currentLang == AppLanguage.SYSTEM) {
                    stringResource(R.string.language_system_default)
                } else {
                    "${currentLang.displayName} (${currentLang.nativeName})"
                }
                SettingsAccordion(
                    title = stringResource(R.string.settings_language),
                    subtitle = langSubtitle,
                    icon = Icons.Default.Language,
                    expanded = expanded == SettingsSection.LANGUAGE,
                    onClick = { toggle(SettingsSection.LANGUAGE) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppLanguage.entries.forEachIndexed { index, lang ->
                            val isSelected = (state.languageCode == lang.code) ||
                                (state.languageCode.isBlank() && lang == AppLanguage.SYSTEM)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSetLanguage(lang.code) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (lang == AppLanguage.SYSTEM) {
                                            stringResource(R.string.language_system_default)
                                        } else {
                                            lang.nativeName
                                        },
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                    )
                                    if (lang != AppLanguage.SYSTEM) {
                                        Text(
                                            text = lang.displayName,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                SelectionDot(isSelected)
                            }
                            if (index != AppLanguage.entries.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }

            item {
                val installedCount = state.installedDevStacks.count { it != DevStack.WEB }
                SettingsAccordion(
                    title = stringResource(R.string.settings_tools),
                    subtitle = stringResource(R.string.tools_subtitle, installedCount),
                    icon = Icons.Default.Code,
                    expanded = expanded == SettingsSection.TOOLS,
                    onClick = { toggle(SettingsSection.TOOLS) },
                ) {
                    Text(stringResource(R.string.st_tools_included_note), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    DevStack.entries.forEachIndexed { index, stack ->
                        val installed = stack in state.installedDevStacks
                        val installing = state.devStackInstalling == stack
                        val removing = installing && state.devStackRemoving
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stack.localizedLabel(), fontWeight = FontWeight.SemiBold)
                                Text(stack.localizedInstallsSummary(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            when {
                                removing -> Text(stringResource(R.string.tools_removing), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                installing -> Text("${(state.devStackProgress * 100).toInt()}%", color = PocketOrange, fontWeight = FontWeight.Bold)
                                installed && stack == DevStack.WEB -> Text(stringResource(R.string.tools_included), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                installed -> TextButton(
                                    onClick = { stackPendingRemoval = stack },
                                    enabled = state.devStackInstalling == null,
                                ) { Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error) }
                                else -> OutlinedButton(onClick = { onInstallDevStack(stack) }, enabled = state.devStackInstalling == null) { Text(stringResource(R.string.action_add)) }
                            }
                        }
                        if (installing) {
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { state.devStackProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(7.dp),
                                color = PocketOrange,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Spacer(Modifier.height(9.dp))
                            state.devStackBytes?.let { (downloaded, total) ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                ) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                stringResource(R.string.st_transfer_progress, formatTransferMb(downloaded), formatTransferMb(total)),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = FontFamily.Monospace,
                                            )
                                            state.devStackBytesPerSecond?.takeIf { it > 0L }?.let { speed ->
                                                Text(
                                                    stringResource(R.string.st_transfer_speed_eta, formatTransferSpeed(speed), formatTransferEta(downloaded, total, speed)),
                                                    fontSize = 11.sp,
                                                    color = PocketOrange,
                                                    fontFamily = FontFamily.Monospace,
                                                )
                                            }
                                        }
                                        Text(
                                            state.devStackMessage ?: stringResource(R.string.st_downloading),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            } ?: Text(
                                state.devStackMessage ?: stringResource(R.string.st_processing),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (index != DevStack.entries.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.st_clean_empty_projects), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.st_clean_empty_projects_desc),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = onCleanEmptyProjects,
                            enabled = !state.isRunning && !state.projectTerminalRunning && !state.androidBuildRunning,
                        ) {
                            Text(stringResource(R.string.st_clean_empty_projects))
                        }
                    }
                }
            }

            item {
                SettingsAccordion(
                    title = stringResource(R.string.settings_runtime),
                    subtitle = "Ubuntu 20.04 PRoot · ARM64",
                    icon = Icons.Default.Terminal,
                    expanded = expanded == SettingsSection.RUNTIME,
                    onClick = { toggle(SettingsSection.RUNTIME) },
                ) {
                    RuntimeInfoRow(stringResource(R.string.runtime_architecture), "ARM64 (aarch64)")
                    RuntimeInfoRow(stringResource(R.string.runtime_environment), "Ubuntu 20.04 PRoot")
                    RuntimeInfoRow(
                        stringResource(R.string.runtime_active_agent),
                        state.agentKind.localizedTitle() + if (state.installedAgentVersions.containsKey(state.agentKind)) "" else " · " + stringResource(R.string.st_not_installed),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        stringResource(R.string.runtime_installed_agents),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.installedAgentVersions.isEmpty()) {
                        RuntimeInfoRow(stringResource(R.string.st_status), stringResource(R.string.st_no_verified_agent))
                    } else {
                        AgentKind.entries.forEach { agent ->
                            state.installedAgentVersions[agent]?.let { version ->
                                RuntimeInfoRow(agent.localizedTitle(), "v$version")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onClearTerminal(); terminalCleared = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(if (terminalCleared) stringResource(R.string.runtime_history_cleared) else stringResource(R.string.runtime_clear_history))
                    }
                    OutlinedButton(
                        onClick = { showReliabilityHelp = !showReliabilityHelp },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.runtime_advanced_reliability))
                    }
                    AnimatedVisibility(showReliabilityHelp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.st_reliability_help),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                                        .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.st_open_developer_options)) }
                        }
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                item {
                    DebugUpdateChannelSection(
                        initialUrl = initialDebugUpdateManifestUrl,
                        onSave = onSetDebugUpdateManifestUrl,
                        onClear = onClearDebugUpdateManifestUrl,
                    )
                }
            }

            item {
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, Modifier.size(20.dp), tint = PocketOrange)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Mobile Harness", fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.settings_app_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("v${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)),
                                )
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.PrivacyTip,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_privacy), fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.settings_privacy_desc),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.settings_privacy),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun DeveloperDiagnosticsContent(
    state: AppUiState,
    onRefresh: () -> Unit,
    onStopInactiveProcesses: () -> Unit,
    onClearRuntimeCache: () -> Unit,
) {
    val diagnostics = state.diagnostics
    val busy = state.isRunning || state.projectTerminalRunning || state.androidBuildRunning
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onRefresh, enabled = !diagnostics.loading) {
            if (diagnostics.loading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, null, Modifier.size(17.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.diagnostics_refresh))
        }
    }
    DiagnosticsCard(stringResource(R.string.diagnostics_memory)) {
        RuntimeInfoRow(stringResource(R.string.diagnostics_app_memory), formatDiagnosticBytes(diagnostics.appPssBytes))
        RuntimeInfoRow(
            stringResource(R.string.diagnostics_java_heap),
            "${formatDiagnosticBytes(diagnostics.javaHeapBytes)} / ${formatDiagnosticBytes(diagnostics.javaHeapMaxBytes)}",
        )
    }
    DiagnosticsCard(stringResource(R.string.diagnostics_processes)) {
        Text(
            if (diagnostics.activeProcesses.isEmpty()) stringResource(R.string.diagnostics_no_processes)
            else diagnostics.activeProcesses.joinToString(" · "),
            fontSize = 13.sp,
            color = if (diagnostics.activeProcesses.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onStopInactiveProcesses, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.diagnostics_stop_inactive))
        }
    }
    DiagnosticsCard(stringResource(R.string.diagnostics_buffers)) {
        RuntimeInfoRow(stringResource(R.string.diagnostics_messages), diagnostics.messageCount.toString())
        RuntimeInfoRow(stringResource(R.string.diagnostics_live_activity), diagnostics.liveActivityCount.toString())
        RuntimeInfoRow(stringResource(R.string.diagnostics_terminal_buffer), formatDiagnosticBytes(diagnostics.terminalBufferBytes))
        RuntimeInfoRow(stringResource(R.string.diagnostics_build_log), formatDiagnosticBytes(diagnostics.buildLogBytes))
        RuntimeInfoRow(stringResource(R.string.diagnostics_logcat_lines), diagnostics.logcatLines.toString())
        RuntimeInfoRow(stringResource(R.string.diagnostics_file_entries), diagnostics.fileEntries.toString())
    }
    DiagnosticsCard(stringResource(R.string.diagnostics_runtime_cache)) {
        RuntimeInfoRow(stringResource(R.string.diagnostics_cached_output), formatDiagnosticBytes(diagnostics.runtimeCacheBytes))
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onClearRuntimeCache,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.DeleteSweep, null, Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(stringResource(R.string.diagnostics_clear_cache))
        }
        if (busy) {
            Text(
                stringResource(R.string.diagnostics_cache_disabled),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    DiagnosticsCard(stringResource(R.string.diagnostics_recent_exit)) {
        Text(
            diagnostics.recentExit ?: stringResource(R.string.diagnostics_no_recent_exit),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiagnosticsCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

private fun formatDiagnosticBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

private fun formatTransferMb(bytes: Long): String = "%.1f MB".format(bytes.coerceAtLeast(0L) / 1_048_576.0)

private fun formatTransferSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1_048_576L -> "%.1f MB/s".format(bytesPerSecond / 1_048_576.0)
    else -> "%.0f KB/s".format(bytesPerSecond / 1_024.0)
}

private fun formatTransferEta(downloaded: Long, total: Long, bytesPerSecond: Long): String {
    val seconds = ((total - downloaded).coerceAtLeast(0L) / bytesPerSecond.coerceAtLeast(1L)).coerceAtLeast(1L)
    return if (seconds >= 60L) "${seconds / 60}m ${seconds % 60}s" else "${seconds}s"
}


private const val MAX_VISIBLE_PLUGIN_TOOLS = 4

@Composable
private fun PluginsSection(
    plugins: List<PluginManifest>,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRun: (String, String) -> Unit = { _, _ -> },
    runningToolId: String? = null,
    runTitle: String = "",
    runOutput: String = "",
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var adding by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    // Tool lists longer than the preview cap get an expander, so every tool stays reachable.
    val expandedPlugins = remember { mutableStateMapOf<String, Boolean>() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.plugins_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (plugins.isEmpty()) {
            Text(
                stringResource(R.string.plugins_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ) {
                Column {
                    plugins.forEachIndexed { index, plugin ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 13.dp, top = 9.dp, bottom = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(plugin.displayName, fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(
                                        R.string.plugins_tool_count,
                                        plugin.toolCount,
                                        plugin.version,
                                    ),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val isExpanded = expandedPlugins[plugin.id] == true
                                val visibleTools = if (isExpanded) {
                                    plugin.tools
                                } else {
                                    plugin.tools.take(MAX_VISIBLE_PLUGIN_TOOLS)
                                }
                                visibleTools.forEach { tool ->
                                    val toolKey = "${plugin.id}/${tool.id}"
                                    val isRunning = runningToolId == toolKey
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable(enabled = plugin.enabled && runningToolId == null) {
                                                onRun(plugin.id, tool.id)
                                            }
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = stringResource(R.string.plugins_run),
                                            modifier = Modifier.size(13.dp),
                                            tint = if (plugin.enabled) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            if (isRunning) {
                                                stringResource(R.string.plugins_running)
                                            } else {
                                                tool.name
                                            },
                                            fontSize = 11.sp,
                                            color = when (tool.risk) {
                                                PluginToolRisk.LOW -> PocketOrange
                                                PluginToolRisk.MEDIUM -> MaterialTheme.colorScheme.onSurfaceVariant
                                                PluginToolRisk.HIGH -> MaterialTheme.colorScheme.error
                                            },
                                        )
                                    }
                                }
                                if (plugin.toolCount > MAX_VISIBLE_PLUGIN_TOOLS) {
                                    Text(
                                        if (isExpanded) {
                                            stringResource(R.string.plugins_show_less)
                                        } else {
                                            stringResource(
                                                R.string.plugins_more_tools,
                                                plugin.toolCount - MAX_VISIBLE_PLUGIN_TOOLS,
                                            )
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                expandedPlugins[plugin.id] = !isExpanded
                                            }
                                            .padding(vertical = 4.dp, horizontal = 2.dp),
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val body = plugin.tools.joinToString("\n") { it.command }
                                    clipboard.setText(AnnotatedString(body))
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.plugins_commands_copied),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    stringResource(R.string.plugins_copy_commands),
                                    Modifier.size(18.dp),
                                )
                            }
                            Switch(
                                checked = plugin.enabled,
                                onCheckedChange = { onToggle(plugin.id) },
                            )
                            IconButton(onClick = { onDelete(plugin.id) }) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    stringResource(R.string.plugins_delete),
                                    Modifier.size(18.dp),
                                )
                            }
                        }
                        if (index != plugins.lastIndex) HorizontalDivider(Modifier.padding(start = 13.dp))
                    }
                }
            }
        }

        // Three short lines beat a paragraph here: the format is the confusing part, not the
        // concept, so say exactly where to start and where each value goes.
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.plugins_howto_1), fontSize = 12.sp)
                Text(stringResource(R.string.plugins_howto_2), fontSize = 12.sp)
                Text(stringResource(R.string.plugins_howto_3), fontSize = 12.sp)
                Text(
                    stringResource(R.string.plugins_howto_4),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { adding = !adding }, modifier = Modifier.weight(1f)) {
                Text(if (adding) stringResource(R.string.action_cancel) else stringResource(R.string.plugins_add))
            }
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(PluginManifest.sampleTemplate()))
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.plugins_template_copied),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
            ) {
                Text(stringResource(R.string.plugins_copy_template))
            }
        }

        if (runOutput.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    runTitle.ifBlank { stringResource(R.string.plugins_output_title) },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Text(
                        runOutput,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        AnimatedVisibility(adding) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    draft,
                    { draft = it },
                    label = { Text(stringResource(R.string.plugins_manifest_label)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 260.dp),
                )
                Button(
                    onClick = {
                        val manifest = PluginManifest.fromJson(draft)
                        if (manifest == null) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.plugins_invalid),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            onAdd(draft)
                            draft = ""
                            adding = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.plugins_install))
                }
            }
        }
    }
}

@Composable
private fun ScreenControlSection(
    state: AppUiState,
    onToggleMaster: (Boolean) -> Unit,
    onAddBlock: (String) -> Unit,
    onRemoveBlock: (String) -> Unit,
    blocklist: List<String>,
) {
    val context = LocalContext.current
    val serviceOn = ScreenReaderService.isEnabled(context)
    var newBlock by rememberSaveable { mutableStateOf("") }
    var showAdd by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.screen_control_master_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 13.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.screen_control_master), fontWeight = FontWeight.Medium)
                    if (!serviceOn) {
                        Text(
                            stringResource(R.string.screen_control_needs_service),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Switch(
                    checked = state.screenControlEnabled && serviceOn,
                    enabled = serviceOn,
                    onCheckedChange = onToggleMaster,
                )
            }
        }

        Text(
            stringResource(R.string.screen_control_usage) + com.jarves.mh.access.ScreenBridgeInstaller.USAGE_HINT,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            stringResource(R.string.screen_control_blocklist),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
        Text(
            stringResource(R.string.screen_control_blocklist_desc),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        com.jarves.mh.access.ScreenControlPolicy.DEFAULT_BLOCKLIST.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Text(tag, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }

        if (blocklist.isNotEmpty()) {
            blocklist.forEach { entry ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemoveBlock(entry) }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            stringResource(R.string.action_remove),
                            Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        OutlinedButton(onClick = { showAdd = !showAdd }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showAdd) stringResource(R.string.action_cancel) else stringResource(R.string.screen_control_add_block))
        }
        AnimatedVisibility(showAdd) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    newBlock,
                    { newBlock = it },
                    label = { Text(stringResource(R.string.screen_control_add_block)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onAddBlock(newBlock)
                        newBlock = ""
                        showAdd = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.screen_control_approve))
                }
            }
        }
    }
}

@Composable
private fun ScreenReaderSection(
    shareWithAgent: Boolean,
    onShareWithAgent: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var enabled by remember { mutableStateOf(ScreenReaderService.isEnabled(context)) }
    val liveSnapshot by ScreenReaderService.screenSnapshot.collectAsState()
    var captured by remember { mutableStateOf("") }

    // The user can flip the service off in system settings while this screen is open, so
    // re-read the state whenever the section is composed back into view.
    LaunchedEffect(Unit) {
        enabled = ScreenReaderService.isEnabled(context)
        if (enabled && captured.isBlank()) captured = ScreenReaderService.currentText()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.screen_reader_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 13.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.screen_reader_share_title), fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.screen_reader_share_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = shareWithAgent && enabled,
                    enabled = enabled,
                    onCheckedChange = onShareWithAgent,
                )
            }
        }

        if (!enabled) {
            Button(
                onClick = {
                    runCatching { context.startActivity(ScreenReaderService.settingsIntent()) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.screen_reader_enable))
            }
            OutlinedButton(
                onClick = { enabled = ScreenReaderService.isEnabled(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.screen_reader_recheck))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        captured = liveSnapshot.text.ifBlank { ScreenReaderService.currentText() }
                        if (captured.isBlank()) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.screen_reader_empty),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.screen_reader_capture))
                }
                OutlinedButton(
                    onClick = {
                        val text = captured.ifBlank { liveSnapshot.text }
                        if (text.isNotBlank()) {
                            clipboard.setText(AnnotatedString(text))
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.screen_reader_copied),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) {
                    Text(stringResource(R.string.screen_reader_copy))
                }
            }
        }

        val preview = captured.ifBlank { liveSnapshot.text }
        if (liveSnapshot.packageName.isNotBlank() && captured.isBlank()) {
            Text(
                stringResource(R.string.screen_reader_source, liveSnapshot.packageName),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (preview.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                ) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsAccordion(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onClick: () -> Unit,
    showDot: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        if (showDot) {
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                        }
                    }
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    if (expanded) stringResource(R.string.st_collapse) else stringResource(R.string.st_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(expanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
                }
            }
        }
    }
}

@Composable
private fun AntigravityConnectionSettings(
    state: AppUiState,
    code: String,
    onCode: (String) -> Unit,
    onStartLogin: () -> Unit,
    onSubmitCode: () -> Unit,
    onLogout: () -> Unit,
    onRefreshModels: () -> Unit,
    onSetModel: (String) -> Unit,
    onSetEffort: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val auth = state.antigravityAuth
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.st_official_antigravity_cli), fontWeight = FontWeight.SemiBold)
            Text(
                auth.message ?: if (auth.status == AntigravityAuthStatus.SIGNED_IN) {
                    auth.accountEmail?.let { stringResource(R.string.st_connected_as, it) } ?: stringResource(R.string.st_google_account_connected)
                } else stringResource(R.string.st_sign_in_google_flow),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (auth.status) {
                AntigravityAuthStatus.SIGNED_IN -> OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.st_log_out_antigravity))
                }
                AntigravityAuthStatus.STARTING, AntigravityAuthStatus.COMPLETING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                AntigravityAuthStatus.AWAITING_CODE -> {
                    auth.authorizationUrl?.let { url ->
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(url)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.st_copy_google_signin_url)) }
                    }
                    OutlinedTextField(
                        value = code,
                        onValueChange = onCode,
                        label = { Text(stringResource(R.string.st_one_time_auth_code)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = onSubmitCode, enabled = code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.st_complete_signin))
                    }
                }
                AntigravityAuthStatus.SIGNED_OUT, AntigravityAuthStatus.ERROR -> Button(
                    onClick = onStartLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (auth.status == AntigravityAuthStatus.ERROR) stringResource(R.string.st_reconnect_google) else stringResource(R.string.agent_google_signin)) }
            }
        }
    }

    if (auth.status == AntigravityAuthStatus.SIGNED_IN) {
        Text(stringResource(R.string.st_model), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = state.antigravityModel,
            onValueChange = onSetModel,
            label = { Text(stringResource(R.string.st_antigravity_model_id)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = onRefreshModels,
            enabled = !state.antigravityModelsLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.antigravityModelsLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(stringResource(R.string.provider_refresh_models))
        }
        state.antigravityModels.forEach { model ->
            Row(
                Modifier.fillMaxWidth().clickable { onSetModel(model) }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(model, Modifier.weight(1f), fontSize = 12.sp)
                SelectionDot(state.antigravityModel == model)
            }
        }
        Text(stringResource(R.string.st_reasoning_effort), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("low", "medium", "high").forEach { effort ->
                OutlinedButton(onClick = { onSetEffort(effort) }, modifier = Modifier.weight(1f)) {
                    Text(
                        when (effort) {
                            "low" -> stringResource(R.string.st_effort_low)
                            "medium" -> stringResource(R.string.st_effort_medium)
                            "high" -> stringResource(R.string.st_effort_high)
                            else -> effort
                        },
                    )
                }
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f), shape = RoundedCornerShape(12.dp)) {
        Text(
            stringResource(R.string.st_antigravity_approval_warning),
            Modifier.fillMaxWidth().padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ConnectionSettings(
    state: AppUiState,
    selectedKind: ProviderKind,
    baseUrl: String,
    model: String,
    dshApi: String,
    apiKey: String,
    models: List<DiscoveredModel>,
    isDiscovering: Boolean,
    isValidating: Boolean,
    status: String?,
    statusOk: Boolean,
    savedKeys: List<ApiKeyInfo>,
    newKeyName: String,
    newApiKey: String,
    newKeyVisible: Boolean,
    onPing: () -> Unit,
    onProvider: (ProviderKind) -> Unit,
    onBaseUrl: (String) -> Unit,
    onModel: (String) -> Unit,
    onDshApi: (String) -> Unit,
    onNewKeyName: (String) -> Unit,
    onNewApiKey: (String) -> Unit,
    onToggleNewKey: () -> Unit,
    onAddKey: () -> Unit,
    onActivateKey: (String) -> Unit,
    onRemoveKey: (String) -> Unit,
    onModels: () -> Unit,
    onValidate: () -> Unit,
) {
    val visibleKinds = remember(state.agentKind) { providersForAgent(state.agentKind) }
    var providerExpanded by rememberSaveable { mutableStateOf(false) }
    var addKeyExpanded by rememberSaveable(savedKeys.isEmpty()) { mutableStateOf(savedKeys.isEmpty()) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(
                when (state.apiPingStatus) {
                    ApiPingStatus.OK -> Color(0xFF58C9A3)
                    ApiPingStatus.FAILED -> MaterialTheme.colorScheme.error
                    ApiPingStatus.PINGING -> PocketOrange
                    ApiPingStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                }, CircleShape,
            ))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.provider_active_connection), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.provider.model.ifBlank { stringResource(R.string.st_not_configured) }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                state.activeApiKeyName?.let { name ->
                    Text(stringResource(R.string.st_key_label, name), fontSize = 11.sp, color = PocketOrange, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                state.apiPingMessage?.let {
                    Text(it, fontSize = 11.sp, color = if (state.apiPingStatus == ApiPingStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedButton(onClick = onPing, enabled = state.apiPingStatus != ApiPingStatus.PINGING, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text(if (state.apiPingStatus == ApiPingStatus.PINGING) stringResource(R.string.provider_testing) else stringResource(R.string.action_test))
            }
        }
    }

    Text(stringResource(R.string.provider_label), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { providerExpanded = !providerExpanded },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, if (providerExpanded) PocketOrange else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(selectedKind.localizedTitle(), fontWeight = FontWeight.SemiBold)
                Text(selectedKind.localizedSubtitle(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Icon(if (providerExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, stringResource(R.string.st_choose_provider))
        }
    }
    AnimatedVisibility(providerExpanded) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)) {
            Column {
                visibleKinds.forEachIndexed { index, kind ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onProvider(kind)
                        providerExpanded = false
                    }.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(kind.localizedTitle(), fontWeight = FontWeight.Medium)
                        Text(kind.localizedSubtitle(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    SelectionDot(selectedKind == kind)
                }
                if (index != visibleKinds.lastIndex) HorizontalDivider(Modifier.padding(start = 13.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            }
            }
        }
    }

    if (selectedKind.fixedBaseUrl) {
        Text(
            selectedKind.defaultBaseUrl,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        OutlinedTextField(baseUrl, onBaseUrl, label = { Text(stringResource(R.string.provider_base_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    if (state.agentKind == AgentKind.DEEPSEEK_HARNESS && selectedKind in DSH_PROTOCOL_PROVIDERS) {
        Text(stringResource(R.string.provider_gateway_protocol), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
            Column {
                listOf("anthropic-messages", "openai-completions", "openai-responses").forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onDshApi(option) }.padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option, Modifier.weight(1f), fontSize = 13.sp)
                        SelectionDot(dshApi == option)
                    }
                }
            }
        }
    }
    Text(stringResource(R.string.st_model), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(model, onModel, label = { Text(stringResource(R.string.provider_model_id)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = onModels, enabled = baseUrl.isNotBlank() && apiKey.isNotBlank() && !isDiscovering, modifier = Modifier.fillMaxWidth().height(50.dp)) {
        if (isDiscovering) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Icon(if (models.isEmpty()) Icons.Default.Search else Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(if (models.isEmpty()) stringResource(R.string.provider_find_models) else stringResource(R.string.provider_available_models_count, models.size))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.provider_api_keys), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.st_saved_keys_failover, savedKeys.size), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { addKeyExpanded = !addKeyExpanded }) {
            Text(if (addKeyExpanded) stringResource(R.string.action_cancel) else stringResource(R.string.provider_add_key))
        }
    }
    if (savedKeys.isNotEmpty()) {
        Text(stringResource(R.string.st_saved_api_keys), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
            Column {
                savedKeys.forEachIndexed { index, key ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onActivateKey(key.id) }.padding(start = 13.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(key.name, fontWeight = FontWeight.Medium)
                            Text(
                                if (key.isActive) stringResource(R.string.st_active_now_switch) else stringResource(R.string.st_tap_to_make_active),
                                fontSize = 11.sp,
                                color = if (key.isActive) PocketOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SelectionDot(key.isActive)
                        IconButton(onClick = { onRemoveKey(key.id) }) {
                            Icon(Icons.Default.DeleteSweep, stringResource(R.string.st_remove_named, key.name), Modifier.size(18.dp))
                        }
                    }
                    if (index != savedKeys.lastIndex) HorizontalDivider(Modifier.padding(start = 13.dp))
                }
            }
        }
    }
    AnimatedVisibility(addKeyExpanded) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                newKeyName,
                onNewKeyName,
                label = { Text(stringResource(R.string.provider_key_name)) },
                placeholder = { Text(stringResource(R.string.st_key_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                newApiKey,
                onNewApiKey,
                label = { Text(stringResource(R.string.provider_api_key)) },
                singleLine = true,
                visualTransformation = if (newKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = onToggleNewKey) {
                        Icon(if (newKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, stringResource(R.string.st_show_hide_new_key))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onAddKey()
                    addKeyExpanded = false
                },
                enabled = newKeyName.isNotBlank() && newApiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(stringResource(R.string.st_save_api_key))
            }
        }
    }
    if (status != null) {
        Text(status, fontSize = 12.sp, color = if (statusOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
    }
    Button(
        onClick = onValidate,
        enabled = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank() && !isDiscovering && !isValidating,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (isValidating) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(if (isValidating) stringResource(R.string.st_checking_connection_plain) else stringResource(R.string.st_test_connection_and_save))
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    Box(
        Modifier.size(20.dp).border(if (selected) 2.dp else 1.dp, if (selected) PocketOrange else MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(9.dp).background(PocketOrange, CircleShape))
    }
}

@Composable
private fun ModernThemeChoice(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) PocketOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) PocketOrange else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, title, Modifier.size(20.dp), tint = if (selected) PocketOrange else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(5.dp))
            Text(title, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun RuntimeInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
private fun DebugUpdateChannelSection(
    initialUrl: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    val isOverridden = initialUrl.isNotBlank()
    SettingsAccordion(
        title = stringResource(R.string.st_update_channel),
        subtitle = if (isOverridden) stringResource(R.string.st_update_channel_overridden) else stringResource(R.string.st_update_channel_default),
        icon = Icons.Default.Tune,
        expanded = expanded,
        onClick = { expanded = !expanded },
    ) {
        Text(
            stringResource(R.string.st_update_channel_help),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.st_manifest_url)) },
            placeholder = { Text("https://your-tunnel.example/mobile-harness-update.json") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onSave(url) },
                enabled = url.startsWith("https://"),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isOverridden) stringResource(R.string.st_replace) else stringResource(R.string.st_use_and_check))
            }
            OutlinedButton(
                onClick = onClear,
                enabled = isOverridden,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.st_reset))
            }
        }
        if (isOverridden) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.st_current_url, initialUrl),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
