package com.culturedcode.things3.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.culturedcode.things3.R
import com.culturedcode.things3.data.backup.BackupInspectionResult
import com.culturedcode.things3.ui.theme.ThingsTheme
import com.culturedcode.things3.ui.viewmodel.ThingsbookStatistics
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class PasswordValidationResult(
    val isValid: Boolean,
    val passwordOrNull: String?,
    val errorResId: Int?
)

private fun validateBackupPasswordInput(
    usePassword: Boolean,
    password: String,
    confirmation: String
): PasswordValidationResult {
    if (!usePassword) {
        return PasswordValidationResult(isValid = true, passwordOrNull = null, errorResId = null)
    }
    if (password.length < 4) {
        return PasswordValidationResult(
            isValid = false,
            passwordOrNull = null,
            errorResId = R.string.backup_password_err_too_short
        )
    }
    if (password != confirmation) {
        return PasswordValidationResult(
            isValid = false,
            passwordOrNull = null,
            errorResId = R.string.backup_password_err_mismatch
        )
    }
    return PasswordValidationResult(isValid = true, passwordOrNull = password, errorResId = null)
}

/**
 * Settings section rendered inside `ThingsSettingsModal` for Full Backup & Restore (`.tbbackup`).
 */
@Composable
fun BackupAndRestoreSettingsSection(
    isBackupBusy: Boolean,
    backupStatusMessage: String?,
    onOpenCreateBackupDialog: () -> Unit,
    onInspectBackupFromUri: (Uri) -> Unit,
    onClearBackupStatusMessage: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    val openBackupDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onInspectBackupFromUri(uri)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = colors.thingsBlue,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = stringResource(R.string.backup_section_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.primaryText
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.backup_section_desc),
            style = MaterialTheme.typography.labelSmall,
            color = colors.secondaryText
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenCreateBackupDialog()
                },
                enabled = !isBackupBusy,
                colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SaveAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.backup_create_button),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
            }

            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    runCatching { openBackupDocumentLauncher.launch(arrayOf("*/*")) }
                },
                enabled = !isBackupBusy,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = null,
                    tint = colors.thingsBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.backup_restore_button),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.thingsBlue
                )
            }
        }

        if (!backupStatusMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            val isError = backupStatusMessage.startsWith("❌")
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = if (isError) colors.deadlineUrgentRed.copy(alpha = 0.12f) else colors.thingsBlueSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClearBackupStatusMessage() }
            ) {
                Text(
                    text = backupStatusMessage,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = if (isError) colors.deadlineUrgentRed else colors.thingsBlue,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Dialog for creating a full Thingsbook backup file (`.tbbackup`) with optional AES-256-GCM password protection.
 */
@Composable
fun ThingsBackupCreateDialog(
    statistics: ThingsbookStatistics,
    isBusy: Boolean,
    onSaveToFile: (password: String?) -> Unit,
    onShareBackupFile: (password: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current

    var usePassword by remember { mutableStateOf(false) }
    var passwordField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }
    var confirmField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }
    var showPassword by remember { mutableStateOf(false) }
    var localValidationErrorResId by remember { mutableStateOf<Int?>(null) }

    val passwordFocusRequester = remember { FocusRequester() }

    LaunchedEffect(usePassword) {
        localValidationErrorResId = null
        if (usePassword) {
            delay(60)
            runCatching { passwordFocusRequester.requestFocus() }
        } else {
            passwordField = TextFieldValue(text = "", selection = TextRange(0))
            confirmField = TextFieldValue(text = "", selection = TextRange(0))
        }
    }

    val totalBackupTasks = statistics.openTasksCount + statistics.completedTasksCount + statistics.canceledTasksCount

    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(20.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.thingsBlueSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (usePassword) Icons.Outlined.Lock else Icons.Outlined.SaveAlt,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.backup_create_dialog_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.primaryText
                            )
                            Text(
                                text = stringResource(
                                    R.string.backup_create_dialog_summary,
                                    totalBackupTasks,
                                    statistics.activeProjectsCount,
                                    statistics.areasCount,
                                    statistics.tagsCount
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                    }

                    ThingsHoverTooltip(text = stringResource(R.string.backup_close_dialog_tooltip)) {
                        IconButton(
                            onClick = { if (!isBusy) onDismiss() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Toggle Card: Optional Password Protection
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (usePassword) colors.thingsBlueSoft else colors.tagPillBg.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (usePassword) colors.thingsBlue else colors.hairlineDivider,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = !isBusy) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            usePassword = !usePassword
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (usePassword) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                            contentDescription = null,
                            tint = if (usePassword) colors.thingsBlue else colors.secondaryText,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.backup_password_protect_title),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.primaryText
                            )
                            Text(
                                text = if (usePassword) {
                                    stringResource(R.string.backup_password_protect_on)
                                } else {
                                    stringResource(R.string.backup_password_protect_off)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (usePassword) colors.thingsBlue else colors.secondaryText
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (usePassword) colors.thingsBlue else colors.cardSurface)
                                .border(
                                    width = 1.dp,
                                    color = if (usePassword) colors.thingsBlue else colors.secondaryText.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (usePassword) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = usePassword,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = passwordField,
                            onValueChange = {
                                passwordField = it
                                localValidationErrorResId = null
                            },
                            label = { Text(stringResource(R.string.backup_password_label)) },
                            singleLine = true,
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            trailingIcon = {
                                ThingsHoverTooltip(
                                    text = if (showPassword) {
                                        stringResource(R.string.backup_password_hide)
                                    } else {
                                        stringResource(R.string.backup_password_show)
                                    }
                                ) {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            imageVector = if (showPassword) {
                                                Icons.Outlined.VisibilityOff
                                            } else {
                                                Icons.Outlined.Visibility
                                            },
                                            contentDescription = null,
                                            tint = colors.secondaryText
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(passwordFocusRequester)
                                .onFocusChanged { fs ->
                                    if (fs.isFocused) {
                                        val txt = passwordField.text
                                        passwordField = passwordField.copy(
                                            selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                        )
                                    }
                                }
                        )

                        OutlinedTextField(
                            value = confirmField,
                            onValueChange = {
                                confirmField = it
                                localValidationErrorResId = null
                            },
                            label = { Text(stringResource(R.string.backup_password_confirm_label)) },
                            singleLine = true,
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val res = validateBackupPasswordInput(
                                        usePassword = usePassword,
                                        password = passwordField.text,
                                        confirmation = confirmField.text
                                    )
                                    localValidationErrorResId = res.errorResId
                                    if (res.isValid) {
                                        onSaveToFile(res.passwordOrNull)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { fs ->
                                    if (fs.isFocused) {
                                        val txt = confirmField.text
                                        confirmField = confirmField.copy(
                                            selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                        )
                                    }
                                }
                        )

                        Text(
                            text = stringResource(R.string.backup_password_warning),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = colors.secondaryText
                        )
                    }
                }

                localValidationErrorResId?.let { errRes ->
                    Surface(
                        color = colors.deadlineUrgentRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = colors.deadlineUrgentRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(errRes),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.deadlineUrgentRed
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val res = validateBackupPasswordInput(
                                usePassword = usePassword,
                                password = passwordField.text,
                                confirmation = confirmField.text
                            )
                            localValidationErrorResId = res.errorResId
                            if (res.isValid) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSaveToFile(res.passwordOrNull)
                            }
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.SaveAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = stringResource(R.string.backup_save_to_file),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }

                    ThingsHoverTooltip(text = stringResource(R.string.backup_share_tooltip)) {
                        OutlinedButton(
                            onClick = {
                                val res = validateBackupPasswordInput(
                                    usePassword = usePassword,
                                    password = passwordField.text,
                                    confirmation = confirmField.text
                                )
                                localValidationErrorResId = res.errorResId
                                if (res.isValid) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onShareBackupFile(res.passwordOrNull)
                                }
                            },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(11.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.backup_share_tooltip),
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isBusy
                    ) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            color = colors.secondaryText
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog shown after selecting a `.tbbackup` file to restore:
 * - If the backup is password-protected (`BackupInspectionResult.PasswordProtected`), prompts for the password
 *   and allows immediate retries on mistyped passwords without touching current data.
 * - If unencrypted (`BackupInspectionResult.UnencryptedPreview`), shows a complete summary preview before restoring.
 */
@Composable
fun ThingsBackupRestoreDialog(
    inspection: BackupInspectionResult,
    fileNameHint: String?,
    isBusy: Boolean,
    errorMessage: String?,
    onConfirmRestore: (password: String?) -> Unit,
    onCancel: () -> Unit
) {
    val colors = ThingsTheme.colors
    val haptics = LocalHapticFeedback.current
    val isPasswordProtected = inspection is BackupInspectionResult.PasswordProtected
    val unknownDateLabel = stringResource(R.string.backup_unknown_date)

    var passwordField by remember { mutableStateOf(TextFieldValue(text = "", selection = TextRange(0))) }
    var showPassword by remember { mutableStateOf(false) }
    val passwordFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isPasswordProtected) {
        if (isPasswordProtected) {
            delay(60)
            runCatching { passwordFocusRequester.requestFocus() }
        }
    }

    val formattedDate = remember(inspection.createdAt, unknownDateLabel) {
        if (inspection.createdAt <= 0L) {
            unknownDateLabel
        } else {
            runCatching {
                val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy • HH:mm", Locale.getDefault())
                Instant.ofEpochMilli(inspection.createdAt).atZone(ZoneId.systemDefault()).format(fmt)
            }.getOrDefault(unknownDateLabel)
        }
    }

    Dialog(
        onDismissRequest = { if (!isBusy) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(20.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.thingsBlueSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPasswordProtected) Icons.Outlined.Lock else Icons.Outlined.Restore,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isPasswordProtected) {
                                    stringResource(R.string.backup_restore_encrypted_title)
                                } else {
                                    stringResource(R.string.backup_restore_plain_title)
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.primaryText
                            )
                            Text(
                                text = buildString {
                                    if (!fileNameHint.isNullOrBlank()) append("$fileNameHint • ")
                                    append(formattedDate)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                    }

                    ThingsHoverTooltip(text = stringResource(R.string.action_cancel)) {
                        IconButton(
                            onClick = { if (!isBusy) onCancel() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.action_cancel),
                                tint = colors.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                when (inspection) {
                    is BackupInspectionResult.PasswordProtected -> {
                        Text(
                            text = stringResource(R.string.backup_restore_encrypted_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryText
                        )

                        OutlinedTextField(
                            value = passwordField,
                            onValueChange = { passwordField = it },
                            label = { Text(stringResource(R.string.backup_restore_enter_password)) },
                            singleLine = true,
                            enabled = !isBusy,
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (passwordField.text.isNotEmpty() && !isBusy) {
                                        onConfirmRestore(passwordField.text)
                                    }
                                }
                            ),
                            trailingIcon = {
                                ThingsHoverTooltip(
                                    text = if (showPassword) {
                                        stringResource(R.string.backup_password_hide)
                                    } else {
                                        stringResource(R.string.backup_password_show)
                                    }
                                ) {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            imageVector = if (showPassword) {
                                                Icons.Outlined.VisibilityOff
                                            } else {
                                                Icons.Outlined.Visibility
                                            },
                                            contentDescription = null,
                                            tint = colors.secondaryText
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(passwordFocusRequester)
                                .onFocusChanged { fs ->
                                    if (fs.isFocused) {
                                        val txt = passwordField.text
                                        passwordField = passwordField.copy(
                                            selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                        )
                                    }
                                }
                        )
                    }

                    is BackupInspectionResult.UnencryptedPreview -> {
                        val s = inspection.summary
                        Surface(
                            color = colors.tagPillBg.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.backup_preview_header),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.primaryText
                                )
                                Text(
                                    text = stringResource(
                                        R.string.backup_preview_details,
                                        s.tasksCount,
                                        s.checklistItemsCount,
                                        s.projectsCount,
                                        s.headingsCount,
                                        s.areasCount,
                                        s.tagsCount
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryText
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = colors.deadlineUrgentRed.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.backup_restore_replace_notice),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = colors.deadlineUrgentRed.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = colors.deadlineUrgentRed,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.deadlineUrgentRed
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConfirmRestore(if (isPasswordProtected) passwordField.text else null)
                    },
                    enabled = !isBusy && (!isPasswordProtected || passwordField.text.isNotEmpty()),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Restore,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isPasswordProtected) {
                            stringResource(R.string.backup_restore_confirm_decrypt)
                        } else {
                            stringResource(R.string.backup_restore_confirm_plain)
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onCancel,
                        enabled = !isBusy
                    ) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            color = colors.secondaryText
                        )
                    }
                }
            }
        }
    }
}
