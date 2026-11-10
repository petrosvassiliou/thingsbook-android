package com.culturedcode.things3.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.culturedcode.things3.BuildConfig
import com.culturedcode.things3.R
import com.culturedcode.things3.data.repository.CloudSyncStatus
import com.culturedcode.things3.data.repository.ThingsRepository
import com.culturedcode.things3.data.update.AppReleaseInfo
import com.culturedcode.things3.data.update.AppUpdateUiState
import com.culturedcode.things3.ui.theme.ThingsTheme
import com.culturedcode.things3.ui.viewmodel.ThingsbookStatistics
import java.time.LocalDate
import java.util.Locale

/**
 * Comprehensive Thingsbook Settings Modal (`Settings` / `Einstellungen` ⚙️):
 * 1. Database Mode: Local Room DB (Offline-Only) vs. Cloud Sync (Firebase Firestore) + Sample Tasks Cleanup
 * 2. Account Management: Full email address display, Sync Now, Login/Switch Account, Sign Out
 * 3. App Language Selection & Daily Notification Settings
 * 4. App Auto-Update & Manual "Search for Updates" (GitHub Releases + One-Click APK Download & Install)
 * 5. Live Task & Productivity Statistics: Open, Today, Upcoming, Anytime, Inbox, Someday, Recurring, Completed, Canceled, Deleted, Projects, Areas (X/8), Tags
 * 6. Full Backup & Restore (`.tbbackup` with optional AES-256-GCM password protection)
 * 7. CSV Import & Export: Export all tasks to `.csv` file or share via Intent; Import tasks from `.csv` file
 * 8. App Version Info
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThingsSettingsModal(
    isLocalOnlyDbMode: Boolean,
    syncStatus: CloudSyncStatus,
    currentUserIdentifier: String?,
    statistics: ThingsbookStatistics,
    csvStatusMessage: String?,
    isBackupBusy: Boolean = false,
    backupStatusMessage: String? = null,
    hasSampleTasks: Boolean = false,
    autoCheckUpdatesEnabled: Boolean = true,
    appUpdateState: AppUpdateUiState = AppUpdateUiState.Idle,
    onSetAutoCheckUpdatesEnabled: (Boolean) -> Unit = {},
    onSearchForUpdates: () -> Unit = {},
    onDownloadAndInstallUpdate: (AppReleaseInfo) -> Unit = {},
    onInstallDownloadedUpdate: () -> Unit = {},
    onCancelUpdateDownload: () -> Unit = {},
    onOpenUpdateReleasePage: (String) -> Unit = {},
    onDeleteSampleTasksNow: () -> Unit = {},
    onSetLocalOnlyDbMode: (Boolean) -> Unit,
    onOpenAuthAndSyncModal: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onTriggerImmediateSync: () -> Unit,
    onSignOutCloud: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onOpenCreateBackupDialog: () -> Unit = {},
    onInspectBackupFromUri: (Uri) -> Unit = {},
    onClearBackupStatusMessage: () -> Unit = {},
    onExportCsvToUri: (Uri) -> Unit,
    onShareCsv: () -> Unit,
    onImportCsvFromUri: (Uri) -> Unit,
    onClearCsvMessage: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    // Android Storage Access Framework Launcher for saving CSV file (I/O runs on Dispatchers.IO in ViewModel)
    val createCsvDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            onExportCsvToUri(uri)
        }
    }

    // Android Storage Access Framework Launcher for importing CSV file (I/O runs on Dispatchers.IO in ViewModel)
    val openCsvDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportCsvFromUri(uri)
        }
    }

    val currentLocaleTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(22.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.thingsBlueSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.settings_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.primaryText
                            )
                            Text(
                                text = stringResource(R.string.settings_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                    }

                    ThingsHoverTooltip(text = stringResource(R.string.settings_close_tooltip)) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = colors.hairlineDivider)
                Spacer(modifier = Modifier.height(14.dp))

                // =============================================================
                // 1. Database Mode (Local DB vs. Cloud Sync) & Account
                // =============================================================
                SettingsSectionHeader(
                    icon = Icons.Outlined.Storage,
                    title = stringResource(R.string.settings_db_sync_section)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option A: Local Database (Offline)
                    val localSelected = isLocalOnlyDbMode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (localSelected) 1.5.dp else 1.dp,
                                color = if (localSelected) colors.thingsBlue else colors.hairlineDivider,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSetLocalOnlyDbMode(true)
                            },
                        color = if (localSelected) colors.thingsBlueSoft else colors.tagPillBg.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudOff,
                                    contentDescription = null,
                                    tint = if (localSelected) colors.thingsBlue else colors.secondaryText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.settings_local_db_title),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (localSelected) colors.thingsBlue else colors.primaryText
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = stringResource(R.string.settings_local_db_desc),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                color = colors.secondaryText
                            )
                        }
                    }

                    // Option B: Cloud Sync (Firestore + Local DB)
                    val cloudSelected = !isLocalOnlyDbMode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (cloudSelected) 1.5.dp else 1.dp,
                                color = if (cloudSelected) colors.thingsBlue else colors.hairlineDivider,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSetLocalOnlyDbMode(false)
                            },
                        color = if (cloudSelected) colors.thingsBlueSoft else colors.tagPillBg.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudDone,
                                    contentDescription = null,
                                    tint = if (cloudSelected) colors.thingsBlue else colors.secondaryText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.settings_cloud_sync_title),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (cloudSelected) colors.thingsBlue else colors.primaryText
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = stringResource(R.string.settings_cloud_sync_desc),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                color = colors.secondaryText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Account Card (displays full email address without truncation!)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.tagPillBg.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_account_status_label),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.secondaryText
                                )
                                Text(
                                    text = when {
                                        isLocalOnlyDbMode -> stringResource(R.string.settings_local_mode_active)
                                        !currentUserIdentifier.isNullOrBlank() -> currentUserIdentifier
                                        else -> stringResource(R.string.settings_not_signed_in)
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.primaryText
                                )
                            }
                        }

                        if (!isLocalOnlyDbMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onOpenAuthAndSyncModal()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                                    shape = RoundedCornerShape(9.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudSync,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (currentUserIdentifier.isNullOrBlank()) {
                                            stringResource(R.string.settings_sign_in_choose_account)
                                        } else {
                                            stringResource(R.string.settings_open_account_sync)
                                        },
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White
                                    )
                                }

                                if (!currentUserIdentifier.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onTriggerImmediateSync()
                                        },
                                        shape = RoundedCornerShape(9.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_sync_short),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.thingsBlue
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSignOutCloud()
                                        },
                                        shape = RoundedCornerShape(9.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.auth_sign_out),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.deadlineUrgentRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colors.hairlineDivider)
                Spacer(modifier = Modifier.height(14.dp))

                // =============================================================
                // 2. App Language & Notifications
                // =============================================================
                SettingsSectionHeader(
                    icon = Icons.Outlined.Language,
                    title = stringResource(R.string.settings_language)
                )
                Spacer(modifier = Modifier.height(8.dp))

                val languageOptions = listOf(
                    "" to stringResource(R.string.settings_system_default),
                    "de" to stringResource(R.string.lang_de),
                    "en" to stringResource(R.string.lang_en),
                    "it" to stringResource(R.string.lang_it),
                    "fr" to stringResource(R.string.lang_fr),
                    "es" to stringResource(R.string.lang_es)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    languageOptions.forEach { (tag, label) ->
                        val isSelected = if (tag.isEmpty()) {
                            currentLocaleTag.isEmpty()
                        } else {
                            currentLocaleTag.startsWith(tag, ignoreCase = true)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) colors.thingsBlue else colors.tagPillBg)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectLanguage(tag)
                                }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) Color.White else colors.primaryText
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Shortcut to Daily Notification Settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.tagPillBg.copy(alpha = 0.6f))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenNotificationSettings()
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = stringResource(R.string.notifications_title),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = colors.primaryText
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_configure_arrow),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.thingsBlue
                    )
                }

                // Optional 1-click sample tasks removal when demo data is still present
                if (hasSampleTasks) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.deadlineUrgentRed.copy(alpha = 0.08f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = colors.deadlineUrgentRed.copy(alpha = 0.28f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_sample_tasks_title),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.primaryText
                                )
                                Text(
                                    text = stringResource(R.string.settings_sample_tasks_desc),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = colors.secondaryText
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDeleteSampleTasksNow()
                                },
                                shape = RoundedCornerShape(9.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = colors.deadlineUrgentRed,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.action_delete),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.deadlineUrgentRed
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colors.hairlineDivider)
                Spacer(modifier = Modifier.height(14.dp))

                // =============================================================
                // App Auto-Update & Manual "Search for Updates"
                // =============================================================
                AppUpdateSettingsSection(
                    autoCheckUpdatesEnabled = autoCheckUpdatesEnabled,
                    appUpdateState = appUpdateState,
                    onSetAutoCheckUpdatesEnabled = onSetAutoCheckUpdatesEnabled,
                    onSearchForUpdates = onSearchForUpdates,
                    onDownloadAndInstallUpdate = onDownloadAndInstallUpdate,
                    onInstallDownloadedUpdate = onInstallDownloadedUpdate,
                    onCancelUpdateDownload = onCancelUpdateDownload,
                    onOpenUpdateReleasePage = onOpenUpdateReleasePage
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colors.hairlineDivider)
                Spacer(modifier = Modifier.height(14.dp))

                // =============================================================
                // 3. Task & Productivity Statistics
                // =============================================================
                SettingsSectionHeader(
                    icon = Icons.Outlined.Analytics,
                    title = stringResource(R.string.settings_stats_section)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Primary KPI Row: Open, Completed, Deleted, Canceled
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatisticMetricCard(
                        label = stringResource(R.string.settings_stat_open),
                        value = statistics.openTasksCount.toString(),
                        accentColor = colors.thingsBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatisticMetricCard(
                        label = stringResource(R.string.settings_stat_completed),
                        value = statistics.completedTasksCount.toString(),
                        accentColor = colors.anytimeTeal,
                        modifier = Modifier.weight(1f)
                    )
                    StatisticMetricCard(
                        label = stringResource(R.string.settings_stat_deleted),
                        value = statistics.deletedTasksCount.toString(),
                        accentColor = colors.deadlineUrgentRed,
                        modifier = Modifier.weight(1f)
                    )
                    StatisticMetricCard(
                        label = stringResource(R.string.settings_stat_canceled),
                        value = statistics.canceledTasksCount.toString(),
                        accentColor = colors.secondaryText,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Breakdown of Open Tasks & Containers
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.tagPillBg.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        StatDetailRow(
                            label = stringResource(R.string.settings_stat_today_active),
                            value = stringResource(R.string.settings_stat_tasks_count, statistics.todayTasksCount)
                        )
                        StatDetailRow(
                            label = "📅 ${stringResource(R.string.view_upcoming)}",
                            value = stringResource(R.string.settings_stat_tasks_count, statistics.upcomingTasksCount)
                        )
                        StatDetailRow(
                            label = "📥 ${stringResource(R.string.view_inbox)}",
                            value = stringResource(R.string.settings_stat_tasks_count, statistics.inboxTasksCount)
                        )
                        StatDetailRow(
                            label = "🗂 ${stringResource(R.string.view_anytime)}",
                            value = stringResource(R.string.settings_stat_tasks_count, statistics.anytimeTasksCount)
                        )
                        StatDetailRow(
                            label = "📦 ${stringResource(R.string.view_someday)}",
                            value = stringResource(R.string.settings_stat_tasks_count, statistics.somedayTasksCount)
                        )
                        StatDetailRow(
                            label = stringResource(R.string.settings_stat_recurring),
                            value = stringResource(R.string.settings_stat_active_count, statistics.recurringTasksCount)
                        )
                        HorizontalDivider(
                            color = colors.hairlineDivider,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        StatDetailRow(
                            label = stringResource(R.string.settings_stat_active_projects),
                            value = stringResource(R.string.settings_stat_projects_count, statistics.activeProjectsCount)
                        )
                        StatDetailRow(
                            label = "📦 ${stringResource(R.string.entity_areas)}",
                            value = "${statistics.areasCount} / ${ThingsRepository.MAX_AREAS}"
                        )
                        StatDetailRow(
                            label = stringResource(R.string.settings_stat_saved_tags),
                            value = stringResource(R.string.settings_stat_tags_count, statistics.tagsCount)
                        )
                        StatDetailRow(
                            label = stringResource(R.string.settings_stat_total_records),
                            value = stringResource(R.string.settings_stat_records_count, statistics.totalRecordsCount)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colors.hairlineDivider)
                Spacer(modifier = Modifier.height(14.dp))

                // =============================================================
                // 4. Full Backup & Restore (.tbbackup + Password Protection)
                // =============================================================
                BackupAndRestoreSettingsSection(
                    isBackupBusy = isBackupBusy,
                    backupStatusMessage = backupStatusMessage,
                    onOpenCreateBackupDialog = onOpenCreateBackupDialog,
                    onInspectBackupFromUri = onInspectBackupFromUri,
                    onClearBackupStatusMessage = onClearBackupStatusMessage
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colors.hairlineDivider)
                Spacer(modifier = Modifier.height(14.dp))

                // =============================================================
                // 5. CSV Import & Export
                // =============================================================
                SettingsSectionHeader(
                    icon = Icons.Outlined.FileDownload,
                    title = stringResource(R.string.settings_csv_section)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_csv_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondaryText
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Save CSV to file
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val fileName = "thingsbook-export-${LocalDate.now()}.csv"
                            runCatching { createCsvDocumentLauncher.launch(fileName) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.settings_csv_export),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }

                    // Share CSV via Android Share Sheet
                    ThingsHoverTooltip(text = stringResource(R.string.settings_csv_share)) {
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onShareCsv()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.settings_csv_share),
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Import CSV from file
                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            runCatching {
                                openCsvDocumentLauncher.launch(
                                    arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileUpload,
                            contentDescription = null,
                            tint = colors.thingsBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.settings_csv_import),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.thingsBlue
                        )
                    }
                }

                if (!csvStatusMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val isCsvError = csvStatusMessage.startsWith("❌")
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (isCsvError) colors.deadlineUrgentRed.copy(alpha = 0.12f) else colors.thingsBlueSoft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClearCsvMessage() }
                    ) {
                        Text(
                            text = csvStatusMessage,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = if (isCsvError) colors.deadlineUrgentRed else colors.thingsBlue,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colors.hairlineDivider)
                Spacer(modifier = Modifier.height(10.dp))

                // Footer with Version & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Thingsbook v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.tertiaryText
                    )

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_done),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    val colors = ThingsTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.thingsBlue,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.primaryText
        )
    }
}

@Composable
private fun StatisticMetricCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        color = colors.tagPillBg.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                ),
                color = accentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.secondaryText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatDetailRow(
    label: String,
    value: String
) {
    val colors = ThingsTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.secondaryText
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.primaryText
        )
    }
}

@Composable
private fun AppUpdateSettingsSection(
    autoCheckUpdatesEnabled: Boolean,
    appUpdateState: AppUpdateUiState,
    onSetAutoCheckUpdatesEnabled: (Boolean) -> Unit,
    onSearchForUpdates: () -> Unit,
    onDownloadAndInstallUpdate: (AppReleaseInfo) -> Unit,
    onInstallDownloadedUpdate: () -> Unit,
    onCancelUpdateDownload: () -> Unit,
    onOpenUpdateReleasePage: (String) -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val isBusy = appUpdateState is AppUpdateUiState.Checking || appUpdateState is AppUpdateUiState.Downloading

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SettingsSectionHeader(
            icon = Icons.Outlined.SystemUpdateAlt,
            title = stringResource(R.string.settings_updates_section)
        )
        Surface(
            shape = RoundedCornerShape(7.dp),
            color = colors.thingsBlueSoft
        ) {
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.thingsBlue,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Auto-Update Toggle Row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(colors.tagPillBg.copy(alpha = 0.6f))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSetAutoCheckUpdatesEnabled(!autoCheckUpdatesEnabled)
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_updates_auto_title),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.primaryText
            )
            Text(
                text = stringResource(R.string.settings_updates_auto_desc),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = colors.secondaryText
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = autoCheckUpdatesEnabled,
            onCheckedChange = { enabled ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSetAutoCheckUpdatesEnabled(enabled)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.thingsBlue,
                uncheckedThumbColor = colors.secondaryText,
                uncheckedTrackColor = colors.hairlineDivider
            )
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // "Search for Updates" / "Nach Updates suchen" Button
    OutlinedButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onSearchForUpdates()
        },
        enabled = !isBusy,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (appUpdateState is AppUpdateUiState.Checking) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
                color = colors.thingsBlue
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                tint = colors.thingsBlue,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = if (appUpdateState is AppUpdateUiState.Checking) {
                stringResource(R.string.settings_updates_checking)
            } else {
                stringResource(R.string.settings_updates_search_btn)
            },
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.thingsBlue
        )
    }

    // Status / Action Card
    when (appUpdateState) {
        is AppUpdateUiState.Idle, is AppUpdateUiState.Checking -> Unit

        is AppUpdateUiState.UpToDate -> {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.thingsBlueSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_updates_up_to_date, appUpdateState.currentVersion),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.thingsBlue
                    )
                }
            }
        }

        is AppUpdateUiState.UpdateAvailable -> {
            val release = appUpdateState.release
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.thingsBlueSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.thingsBlue.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(
                            R.string.settings_updates_available_title,
                            release.versionName,
                            BuildConfig.VERSION_NAME
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.thingsBlue
                    )
                    if (release.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = release.releaseNotes,
                            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 15.sp),
                            color = colors.primaryText,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDownloadAndInstallUpdate(release)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SystemUpdateAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.settings_updates_download_install_btn),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            onClick = { onOpenUpdateReleasePage(release.releasePageUrl) },
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = stringResource(R.string.settings_updates_open_github_btn),
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        is AppUpdateUiState.Downloading -> {
            val pct = appUpdateState.progressPercent
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.thingsBlueSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (pct != null) {
                                stringResource(
                                    R.string.settings_updates_downloading,
                                    appUpdateState.release.versionName,
                                    pct
                                )
                            } else {
                                stringResource(
                                    R.string.settings_updates_downloading_indeterminate,
                                    appUpdateState.release.versionName
                                )
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.thingsBlue
                        )
                        val sizeLabel = formatDownloadBytes(
                            downloaded = appUpdateState.downloadedBytes,
                            total = appUpdateState.totalBytes
                        )
                        if (sizeLabel.isNotEmpty()) {
                            Text(
                                text = sizeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (pct != null) {
                        LinearProgressIndicator(
                            progress = { (pct / 100f).coerceIn(0f, 1f) },
                            color = colors.thingsBlue,
                            trackColor = colors.hairlineDivider,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    } else {
                        LinearProgressIndicator(
                            color = colors.thingsBlue,
                            trackColor = colors.hairlineDivider,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = onCancelUpdateDownload,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.secondaryText
                            )
                        }
                    }
                }
            }
        }

        is AppUpdateUiState.ReadyToInstall -> {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.thingsBlueSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.thingsBlue.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(
                            R.string.settings_updates_ready_title,
                            appUpdateState.release.versionName
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.thingsBlue
                    )
                    if (appUpdateState.needsInstallPermission) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = stringResource(R.string.settings_updates_permission_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primaryText
                        )
                    }
                    Spacer(modifier = Modifier.height(9.dp))
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onInstallDownloadedUpdate()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SystemUpdateAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.settings_updates_install_now_btn),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }

        is AppUpdateUiState.Error -> {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.deadlineUrgentRed.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = appUpdateState.message,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.deadlineUrgentRed
                    )
                    val fallbackUrl = appUpdateState.fallbackReleaseUrl
                    if (!fallbackUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onOpenUpdateReleasePage(fallbackUrl) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.settings_updates_open_github_btn),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.thingsBlue
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDownloadBytes(downloaded: Long, total: Long?): String {
    if (downloaded <= 0L && total == null) return ""
    val dlMb = downloaded.toDouble() / (1024.0 * 1024.0)
    return if (total != null && total > 0L) {
        val totalMb = total.toDouble() / (1024.0 * 1024.0)
        String.format(Locale.US, "%.1f / %.1f MB", dlMb, totalMb)
    } else {
        String.format(Locale.US, "%.1f MB", dlMb)
    }
}
