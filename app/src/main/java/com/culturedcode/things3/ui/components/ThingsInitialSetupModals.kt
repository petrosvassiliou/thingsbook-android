package com.culturedcode.things3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.culturedcode.things3.R
import com.culturedcode.things3.ui.theme.ThingsTheme
import kotlinx.coroutines.delay

/**
 * Initial App Setup Banner shown on fresh installs while seeded sample tasks still exist.
 * Lets the user either connect their Cloud Account (which asks whether to merge tasks and removes sample tasks)
 * or immediately delete all sample tasks for a clean local workspace.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThingsInitialSetupBanner(
    onConnectAccount: () -> Unit,
    onDeleteSampleTasksNow: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ThingsTheme.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)),
        color = colors.thingsBlueSoft,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = colors.thingsBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Ersteinrichtung — Beispielaufgaben vorhanden",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.primaryText
                    )
                }

                ThingsHoverTooltip(text = "Hinweis ausblenden") {
                    IconButton(
                        onClick = onDismissBanner,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = "Möchtest du dein Konto einrichten (inkl. Auswahl zum Zusammenführen deiner Aufgaben) oder direkt ohne die vorinstallierten Beispielaufgaben starten?",
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryText
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onConnectAccount,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Konto verbinden & einrichten",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                OutlinedButton(
                    onClick = onDeleteSampleTasksNow,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = colors.deadlineUrgentRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Beispielaufgaben löschen",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.primaryText
                    )
                }
            }
        }
    }
}

/**
 * Confirmation dialog displayed during Initial Account Setup / Login ONLY when sample tasks
 * still exist in the local database:
 * 1. Deletes sample tasks automatically so demo items never pollute the user's account.
 * 2. Asks the user FIRST whether to merge their current non-sample tasks with the tasks stored
 *    on their cloud account, or discard local tasks and load strictly the account's tasks.
 */
@Composable
fun SampleTasksAccountMergeDialog(
    accountLabel: String?,
    nonSampleTaskCount: Int,
    onConfirmMergeWithAccount: () -> Unit,
    onConfirmReplaceWithAccountOnly: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = ThingsTheme.colors
    val displayAccount = accountLabel?.takeIf { it.isNotBlank() } ?: "deinem Cloud-Konto"

    Dialog(
        onDismissRequest = onCancel,
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
                                imageVector = Icons.AutoMirrored.Outlined.MergeType,
                                contentDescription = null,
                                tint = colors.thingsBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Aufgaben zusammenführen?",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.primaryText
                        )
                    }

                    IconButton(
                        onClick = onCancel,
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

                Text(
                    text = "In der App sind noch die anfänglichen Beispielaufgaben vorhanden. Diese Beispielaufgaben werden bei der Einrichtung automatisch gelöscht, damit sie nicht in dein Konto übernommen werden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText
                )

                val detailHint = if (nonSampleTaskCount > 0) {
                    "Du hast zusätzlich $nonSampleTaskCount eigene Aufgabe(n) lokal angelegt. Möchtest du diese aktuellen Aufgaben mit den auf $displayAccount gespeicherten Aufgaben zusammenführen?"
                } else {
                    "Möchtest du deine aktuellen lokalen Einträge (ohne Beispielaufgaben) mit den auf $displayAccount gespeicherten Aufgaben zusammenführen, oder ausschließlich die Aufgaben von deinem Konto laden?"
                }

                Surface(
                    color = colors.tagPillBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = detailHint,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.primaryText,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Button(
                    onClick = onConfirmMergeWithAccount,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue)
                ) {
                    Text(
                        text = "Ja, mit Konto zusammenführen (Beispiele löschen)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }

                OutlinedButton(
                    onClick = onConfirmReplaceWithAccountOnly,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Nein — nur Konto-Aufgaben laden (lokale löschen)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.primaryText,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
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
 * Quick 1-step Area Creation Dialog opened from the expandable Magic Plus (+) context menu.
 * Starts completely empty with cursor positioned at the far left (`TextRange(0)`).
 */
@Composable
fun QuickCreateAreaDialog(
    currentAreaCount: Int,
    maxAreas: Int,
    onCreateArea: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ThingsTheme.colors
    val focusRequester = remember { FocusRequester() }
    var areaTitleField by remember {
        mutableStateOf(TextFieldValue(text = "", selection = TextRange(0)))
    }

    LaunchedEffect(Unit) {
        delay(50)
        runCatching { focusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(18.dp)),
            color = colors.cardSurface,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Inventory2,
                            contentDescription = null,
                            tint = colors.anytimeTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Neuer Bereich",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.primaryText
                            )
                            Text(
                                text = stringResource(R.string.areas_active_count, currentAreaCount, maxAreas),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryText
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = areaTitleField,
                    onValueChange = { areaTitleField = it },
                    placeholder = {
                        Text(text = stringResource(R.string.entity_area))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val trimmed = areaTitleField.text.trim()
                            if (trimmed.isNotEmpty()) {
                                onCreateArea(trimmed)
                                onDismiss()
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                val txt = areaTitleField.text
                                areaTitleField = areaTitleField.copy(
                                    selection = if (txt.isEmpty()) TextRange(0) else TextRange(0, txt.length)
                                )
                            }
                        }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            color = colors.secondaryText
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = areaTitleField.text.trim()
                            if (trimmed.isNotEmpty()) {
                                onCreateArea(trimmed)
                                onDismiss()
                            }
                        },
                        enabled = areaTitleField.text.isNotBlank() && currentAreaCount < maxAreas,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.thingsBlue)
                    ) {
                        Text(text = stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}
