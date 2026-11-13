package com.craxiom.networksurvey.ui.nsanalytics

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.craxiom.networksurvey.ui.component.material.ExpressiveSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.theme.onPrimaryDark
import com.craxiom.networksurvey.ui.util.copyTextToClipboard
import com.craxiom.networksurvey.util.NsAnalyticsUtils
import timber.log.Timber
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen for managing NS Analytics connection and settings.
 */
@Composable
fun NsAnalyticsConnectionScreen(
    viewModel: NsAnalyticsConnectionViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showClearQueueDialog by remember { mutableStateOf(false) }
    var showDeregistrationDialog by remember { mutableStateOf(false) }

    // Bluetooth permission launcher for Android 12+
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(
                context,
                "Bluetooth permissions are required to collect Bluetooth data",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    // Check for QR data when returning from scanner
    LaunchedEffect(Unit) {
        viewModel.checkAndProcessQrData()
    }

    // Handle service connection with proper lifecycle management
    NsAnalyticsServiceConnectionHandler(viewModel)

    // Handle Bluetooth toggle with permission check for Android 12+
    val handleBluetoothToggle: (Boolean) -> Unit = handleBluetoothToggle@{ enabled ->
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val missingPermissions = NetworkSurveyActivity.BLUETOOTH_PERMISSIONS.any { permission ->
                ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            }
            if (missingPermissions) {
                viewModel.showBluetoothPermissionRationale()
                return@handleBluetoothToggle
            }
        }
        viewModel.toggleBluetoothProtocol(enabled)
    }

    NsAnalyticsConnectionContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onToggleAutoUpload = { viewModel.toggleAutoUpload(it) },
        onUploadNowClick = { viewModel.uploadNow() },
        onDisconnectClick = { showDisconnectDialog = true },
        onClearQueueClick = { showClearQueueDialog = true },
        onToggleCellular = { viewModel.toggleCellularProtocol(it) },
        onToggleWifi = { viewModel.toggleWifiProtocol(it) },
        onToggleBluetooth = handleBluetoothToggle,
        onToggleGnss = { viewModel.toggleGnssProtocol(it) },
        onTogglePhoneState = { viewModel.togglePhoneStateProtocol(it) },
        onToggleSurvey = { viewModel.toggleSurvey() },
    )

    // Show deregistration dialog when device is detected as deregistered
    LaunchedEffect(uiState.deregistrationInfo) {
        if (uiState.deregistrationInfo != null) {
            showDeregistrationDialog = true
        }
    }

    if (showDisconnectDialog) {
        DisconnectConfirmationDialog(
            workspaceName = uiState.workspaceName ?: "Unknown Workspace",
            onDismiss = { showDisconnectDialog = false },
            onConfirm = {
                viewModel.unregisterDevice()
                showDisconnectDialog = false
            }
        )
    }

    if (showDeregistrationDialog && uiState.deregistrationInfo != null) {
        DeviceDeregisteredDialog(
            deregistrationInfo = uiState.deregistrationInfo!!,
            workspaceName = uiState.workspaceName ?: "the workspace",
            onDismiss = {
                showDeregistrationDialog = false
                viewModel.clearDeregistrationInfo()
            },
            onRegisterAgain = {
                showDeregistrationDialog = false
                viewModel.clearDeregistrationInfo()
                openUrlInBrowser(context, "https://analytics.networksurvey.app")
            }
        )
    }

    if (uiState.showQuotaExceededDialog) {
        QuotaExceededDialog(
            currentUsage = uiState.quotaCurrentUsage,
            maxRecords = uiState.quotaMaxRecords,
            quotaMessage = uiState.quotaMessage,
            nsAnalyticsUrl = uiState.quotaWebUrl,
            onDismiss = {
                viewModel.dismissQuotaDialog()
            },
            onOpenNsAnalytics = {
                viewModel.dismissQuotaDialog()
                uiState.quotaWebUrl?.let { url ->
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    )
                }
            }
        )
    }

    if (showClearQueueDialog) {
        ClearQueueConfirmationDialog(
            queueSize = uiState.queuedRecords,
            onDismiss = { showClearQueueDialog = false },
            onConfirm = {
                viewModel.clearQueue()
                showClearQueueDialog = false
            }
        )
    }

    // Registration confirmation dialog
    uiState.pendingQrData?.let { qrData ->
        if (uiState.showRegistrationConfirmDialog) {
            RegistrationConfirmationDialog(
                qrData = qrData,
                onDismiss = { viewModel.cancelRegistration() },
                onConfirm = { viewModel.confirmRegistration() }
            )
        }
    }

    // Registration success dialog
    if (uiState.showRegistrationSuccessDialog) {
        RegistrationSuccessDialog(
            workspaceName = uiState.workspaceName ?: "Unknown Workspace",
            onDismiss = { viewModel.dismissSuccessDialog() }
        )
    }

    // Registration error dialog
    uiState.registrationError?.let { errorMessage ->
        RegistrationErrorDialog(
            errorMessage = errorMessage,
            onDismiss = { viewModel.dismissRegistrationError() },
            onRetry = { viewModel.retryRegistration() }
        )
    }

    // Already registered dialog - when user scans QR while registered
    if (uiState.showAlreadyRegisteredDialog && uiState.pendingQrData != null) {
        AlreadyRegisteredDialog(
            currentWorkspaceName = uiState.workspaceName ?: "Unknown Workspace",
            newWorkspaceName = uiState.pendingQrData?.workspaceName,
            onDismiss = { viewModel.dismissAlreadyRegisteredDialog() }
        )
    }

    // Bluetooth permission rationale dialog
    if (uiState.showBluetoothPermissionDialog) {
        BluetoothPermissionRationaleDialog(
            onDismiss = { viewModel.dismissBluetoothPermissionDialog() },
            onConfirm = {
                viewModel.dismissBluetoothPermissionDialog()
                bluetoothPermissionLauncher.launch(NetworkSurveyActivity.BLUETOOTH_PERMISSIONS)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NsAnalyticsConnectionContent(
    uiState: NsAnalyticsConnectionUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onToggleAutoUpload: (Boolean) -> Unit,
    onUploadNowClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onClearQueueClick: () -> Unit,
    onToggleCellular: (Boolean) -> Unit,
    onToggleWifi: (Boolean) -> Unit,
    onToggleBluetooth: (Boolean) -> Unit,
    onToggleGnss: (Boolean) -> Unit,
    onTogglePhoneState: (Boolean) -> Unit,
    onToggleSurvey: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NS Analytics 详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!uiState.isRegistered) {
                        // Not registered - show connection setup
                        NotConnectedCard()
                    } else {
                        // Registered - show new design
                        WorkspaceStatusCard(
                            workspaceName = uiState.workspaceName,
                            workspaceId = uiState.workspace
                        )

                        NsAnalyticsStatusCard(
                            isSurveyActive = uiState.isSurveyActive,
                            surveyStartTime = uiState.surveyStartTime,
                            cellularCount = uiState.cellularRecordCount,
                            wifiCount = uiState.wifiRecordCount,
                            bluetoothCount = uiState.bluetoothRecordCount,
                            gnssCount = uiState.gnssRecordCount,
                            phoneStateCount = uiState.phoneStateRecordCount,
                            onToggleSurvey = onToggleSurvey,
                            showDetailedInfo = true
                        )

                        UploadSettingsCard(
                            uploadState = uiState.uploadState,
                            uploadStatusMessage = uiState.uploadStatusMessage,
                            uploadProgress = uiState.uploadProgress,
                            uploadedRecords = uiState.uploadedRecords,
                            totalRecordsToUpload = uiState.totalRecordsToUpload,
                            autoUploadEnabled = uiState.autoUploadEnabled,
                            uploadFrequencyMinutes = uiState.uploadFrequencyMinutes,
                            lastUploadTime = uiState.lastUploadTime,
                            lastUploadResult = uiState.lastUploadResult,
                            uploadPausedForQuota = uiState.uploadPausedForQuota,
                            quotaCurrentUsage = uiState.quotaCurrentUsage,
                            quotaMaxRecords = uiState.quotaMaxRecords,
                            quotaMessage = uiState.quotaMessage,
                            quotaWebUrl = uiState.quotaWebUrl,
                            onToggleAutoUpload = onToggleAutoUpload,
                            onUploadNowClick = onUploadNowClick
                        )

                        ActiveProtocolsCard(
                            cellularEnabled = uiState.cellularEnabled,
                            wifiEnabled = uiState.wifiEnabled,
                            bluetoothEnabled = uiState.bluetoothEnabled,
                            gnssEnabled = uiState.gnssEnabled,
                            phoneStateEnabled = uiState.phoneStateEnabled,
                            isSurveyActive = uiState.isSurveyActive,
                            onToggleCellular = onToggleCellular,
                            onToggleWifi = onToggleWifi,
                            onToggleBluetooth = onToggleBluetooth,
                            onToggleGnss = onToggleGnss,
                            onTogglePhoneState = onTogglePhoneState
                        )

                        DangerZoneCard(
                            queuedRecords = uiState.queuedRecords,
                            onClearQueueClick = onClearQueueClick,
                            onDisconnectClick = onDisconnectClick
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun NotConnectedCard() {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        NsAnalyticsHeroSection()

        SetupGuideSection()

        SetupTipBanner()

        CallToActionButtons(
            onGetStartedClick = {
                openUrlInBrowser(context, "https://analytics.networksurvey.app")
            },
            onLearnMoreClick = {
                openUrlInBrowser(context, "https://www.networksurvey.app/analytics")
            },
            onQrCodeUserManualClick = {
                openUrlInBrowser(
                    context,
                    "https://www.networksurvey.app/manual/ns-analytics/qr-code-registration/"
                )
            }
        )

        WhatHappensNextSection()
    }
}

/**
 * Hero section with NS Analytics branding and tagline
 */
@Composable
private fun NsAnalyticsHeroSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ns_analytics),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(R.string.ns_analytics),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = stringResource(R.string.ns_analytics_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/**
 * Step-by-step setup guide section
 */
@Composable
private fun SetupGuideSection() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.ns_analytics_setup_guide_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

            SetupStepItem(
                stepNumber = 1,
                title = stringResource(R.string.ns_analytics_step_1_title),
                description = stringResource(R.string.ns_analytics_step_1_desc)
            )

            SetupStepItem(
                stepNumber = 2,
                title = stringResource(R.string.ns_analytics_step_2_title),
                description = stringResource(R.string.ns_analytics_step_2_desc)
            )

            SetupStepItem(
                stepNumber = 3,
                title = stringResource(R.string.ns_analytics_step_3_title),
                description = stringResource(R.string.ns_analytics_step_3_desc)
            )

            SetupStepItem(
                stepNumber = 4,
                title = stringResource(R.string.ns_analytics_step_4_title),
                description = stringResource(R.string.ns_analytics_step_4_desc)
            )
        }
    }
}

/**
 * Individual setup step with number, title, and description
 */
@Composable
private fun SetupStepItem(
    stepNumber: Int,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Step number circle
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Setup tip banner recommending desktop setup
 */
@Composable
private fun SetupTipBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.ns_analytics_setup_tip_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Text(
                    text = stringResource(R.string.ns_analytics_setup_tip_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * Call-to-action buttons section
 */
@Composable
private fun CallToActionButtons(
    onGetStartedClick: () -> Unit,
    onLearnMoreClick: () -> Unit,
    onQrCodeUserManualClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Primary button - Get Started
        Button(
            onClick = onGetStartedClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(stringResource(R.string.ns_analytics_get_started))
            Icon(
                painter = painterResource(R.drawable.ic_open_details),
                tint = onPrimaryDark,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Text link - Learn More
        TextButton(
            onClick = onLearnMoreClick,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = stringResource(R.string.ns_analytics_learn_more),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Icon(
                painter = painterResource(R.drawable.ic_open_details),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Text link - User Manual QR Code Registration
        TextButton(
            onClick = onQrCodeUserManualClick,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = stringResource(R.string.ns_analytics_qr_user_manual),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Icon(
                painter = painterResource(R.drawable.ic_open_details),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * What happens next explanation section
 */
@Composable
private fun WhatHappensNextSection() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.ns_analytics_what_happens_next_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = stringResource(R.string.ns_analytics_what_happens_next_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
        }
    }
}

/**
 * Opens a URL in the default browser
 */
private fun openUrlInBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    } catch (e: Exception) {
        Timber.e(e, "Failed to open URL: $url")
        Toast.makeText(
            context,
            "Unable to open web browser",
            Toast.LENGTH_SHORT
        ).show()
    }
}

@Composable
private fun WorkspaceStatusCard(
    workspaceName: String?,
    workspaceId: String?
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "工作区已注册",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = workspaceName ?: "未知工作区",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                workspaceId?.let { id ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            copyTextToClipboard(
                                context,
                                context.getString(R.string.workspace_id_clip_label),
                                id,
                                context.getString(R.string.workspace_id_copied),
                            )
                        }
                    ) {
                                  Text(
                    text = "ID: ${id.take(8)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = "复制 ID",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "可开始采集并上传测量数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                workspaceId?.let { id ->
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            val encodedId = Uri.encode(id)
                            openUrlInBrowser(
                                context,
                                "https://analytics.networksurvey.app?w=$encodedId"
                            )
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.ns_analytics_open_dashboard),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_open_details),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun UploadSettingsCard(
    uploadState: UploadState,
    uploadStatusMessage: String,
    uploadProgress: Float,
    uploadedRecords: Int,
    totalRecordsToUpload: Int,
    autoUploadEnabled: Boolean,
    uploadFrequencyMinutes: Int,
    lastUploadTime: Long,
    lastUploadResult: String?,
    uploadPausedForQuota: Boolean,
    quotaCurrentUsage: Int,
    quotaMaxRecords: Int,
    quotaMessage: String?,
    quotaWebUrl: String?,
    onToggleAutoUpload: (Boolean) -> Unit,
    onUploadNowClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "上传设置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Upload Status Section
            UploadStatusRow(
                state = uploadState,
                statusMessage = uploadStatusMessage
            )

            // Quota paused banner: explains why automatic uploads stopped and how to resume.
            if (uploadPausedForQuota) {
                Spacer(modifier = Modifier.height(12.dp))
                QuotaPausedBanner(
                    currentUsage = quotaCurrentUsage,
                    maxRecords = quotaMaxRecords,
                    quotaMessage = quotaMessage,
                    quotaWebUrl = quotaWebUrl
                )
            }

            // Last upload info with result
            if (lastUploadTime > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val timeAgo = getTimeAgo(lastUploadTime)
                val exactTime = formatUploadTimestamp(lastUploadTime)

                // Primary line: relative time + result
                val resultText = if (lastUploadResult != null) {
                    "Last Success: $timeAgo \u2022 $lastUploadResult"
                } else {
                    "Last Success: $timeAgo"
                }
                Text(
                    text = resultText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Secondary line: exact timestamp
                Text(
                    text = exactTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Inline Progress (only when uploading)
            if (uploadState == UploadState.UPLOADING) {
                Spacer(modifier = Modifier.height(12.dp))

                val animatedProgress by animateFloatAsState(
                    targetValue = uploadProgress,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                    label = "upload_progress"
                )

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {}
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "$uploadedRecords / $totalRecordsToUpload records",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Auto Upload toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "自动上传",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (autoUploadEnabled) "每 $uploadFrequencyMinutes 分钟" else "已关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ExpressiveSwitch(
                    checked = autoUploadEnabled,
                    onCheckedChange = onToggleAutoUpload
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Upload Now button with state-aware text. While quota-paused, keep it enabled so the
            // manual upload can resume automatic uploads (it bypasses the pause server-side).
            val buttonEnabled = uploadState == UploadState.IDLE ||
                    (uploadPausedForQuota && uploadState != UploadState.UPLOADING)
            val buttonText = when {
                uploadState == UploadState.UPLOADING -> stringResource(R.string.ns_analytics_upload_button_uploading)
                uploadPausedForQuota -> stringResource(R.string.ns_analytics_upload_button_resume)
                uploadState == UploadState.EMPTY -> stringResource(R.string.ns_analytics_upload_button_no_records)
                uploadState == UploadState.UNAVAILABLE -> stringResource(R.string.ns_analytics_upload_button_unavailable)
                else -> stringResource(R.string.ns_analytics_upload_button_upload_now)
            }

            Button(
                onClick = onUploadNowClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = buttonEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            ) {
                if (uploadState == UploadState.UPLOADING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(buttonText)
            }
        }
    }
}

/**
 * Displays the current upload status with a colored indicator dot.
 */
@Composable
private fun UploadStatusRow(
    state: UploadState,
    statusMessage: String
) {
    val statusColor = when (state) {
        UploadState.IDLE -> Color(0xFF4CAF50)  // Green
        UploadState.UPLOADING -> Color(0xFFFFC107)  // Yellow/Amber
        UploadState.EMPTY -> MaterialTheme.colorScheme.onSurfaceVariant
        UploadState.UNAVAILABLE -> Color(0xFFF44336)  // Red
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = statusColor,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Banner shown when automatic uploads are paused because the workspace record quota was exceeded.
 * Explains why uploads stopped, shows current usage, and offers a link to manage the subscription.
 * The "Upload now to resume" action lives in the parent card's button.
 */
@Composable
private fun QuotaPausedBanner(
    currentUsage: Int,
    maxRecords: Int,
    quotaMessage: String?,
    quotaWebUrl: String?
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.ns_analytics_upload_paused_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = quotaMessage?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.ns_analytics_upload_paused_banner_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
            )

            if (maxRecords > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.ns_analytics_quota_usage,
                        NsAnalyticsUtils.formatNumberWithThousandsSeparator(currentUsage),
                        NsAnalyticsUtils.formatNumberWithThousandsSeparator(maxRecords)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }

            if (!quotaWebUrl.isNullOrBlank()) {
                TextButton(onClick = { openUrlInBrowser(context, quotaWebUrl) }) {
                    Text(stringResource(R.string.ns_analytics_manage_subscription))
                }
            }
        }
    }
}

@Composable
private fun ActiveProtocolsCard(
    cellularEnabled: Boolean,
    wifiEnabled: Boolean,
    bluetoothEnabled: Boolean,
    gnssEnabled: Boolean,
    phoneStateEnabled: Boolean,
    isSurveyActive: Boolean,
    onToggleCellular: (Boolean) -> Unit,
    onToggleWifi: (Boolean) -> Unit,
    onToggleBluetooth: (Boolean) -> Unit,
    onToggleGnss: (Boolean) -> Unit,
    onTogglePhoneState: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "启用协议",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            ProtocolToggleRow(
                label = "蜂窝网络",
                description = "GSM、CDMA、UMTS、LTE、NR",
                isEnabled = cellularEnabled,
                enabled = !isSurveyActive,
                onToggle = onToggleCellular
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProtocolToggleRow(
                label = "电话状态",
                description = "主服务小区、注册与 SIM 变更",
                isEnabled = phoneStateEnabled,
                enabled = !isSurveyActive,
                onToggle = onTogglePhoneState
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProtocolToggleRow(
                label = "Wi-Fi",
                description = "802.11 网络与接入点",
                isEnabled = wifiEnabled,
                enabled = !isSurveyActive,
                onToggle = onToggleWifi
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProtocolToggleRow(
                label = "蓝牙",
                description = "BLE 与经典设备",
                isEnabled = bluetoothEnabled,
                enabled = !isSurveyActive,
                onToggle = onToggleBluetooth
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProtocolToggleRow(
                label = "GPS/GNSS",
                description = "卫星定位数据",
                isEnabled = gnssEnabled,
                enabled = !isSurveyActive,
                onToggle = onToggleGnss
            )
        }
    }
}

@Composable
private fun ProtocolToggleRow(
    label: String,
    description: String,
    isEnabled: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ExpressiveSwitch(
            checked = isEnabled,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun DangerZoneCard(
    queuedRecords: Int,
    onClearQueueClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onClearQueueClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = queuedRecords > 0,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFE57373),
                disabledContentColor = Color(0xFFE57373).copy(alpha = 0.5f)
            ),
            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE57373))
            )
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("清空队列（$queuedRecords 条记录）")
        }

        OutlinedButton(
            onClick = onDisconnectClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFE57373)
            ),
            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE57373))
            )
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(stringResource(R.string.unregister_device))
        }
    }
}


private fun getTimeAgo(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val elapsed = System.currentTimeMillis() - timestamp
    val minutes = (elapsed / 1000 / 60).toInt()
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "$days days ago"
        hours > 0 -> "$hours hours ago"
        minutes > 0 -> "$minutes minutes ago"
        else -> "Just now"
    }
}

/**
 * Formats a timestamp as a full date/time string using device locale.
 * Example output: "Dec 10, 2024, 3:45 PM"
 */
private fun formatUploadTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val date = Date(timestamp)
    val dateFormat = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,  // Dec 10, 2024
        DateFormat.SHORT,   // 3:45 PM
        Locale.getDefault()
    )
    return dateFormat.format(date)
}

@Composable
private fun DisconnectConfirmationDialog(
    workspaceName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unregister_device_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.unregister_device_message, workspaceName))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.unregister))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun DeviceDeregisteredDialog(
    deregistrationInfo: DeregistrationInfo,
    workspaceName: String,
    onDismiss: () -> Unit,
    onRegisterAgain: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_deregistered_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Handle different levels of detail in deregistration info
                when {
                    deregistrationInfo.source == "web" &&
                            (deregistrationInfo.deregisteredBy != null || deregistrationInfo.deregisteredAt != null) -> {
                        // We have details from web deregistration
                        val byPart = deregistrationInfo.deregisteredBy?.let {
                            stringResource(R.string.device_deregistered_by, it)
                        } ?: ""
                        val onPart = deregistrationInfo.deregisteredAt?.let { " on $it" } ?: ""
                        Text(stringResource(R.string.device_deregistered_from_web, byPart, onPart))
                    }

                    deregistrationInfo.source == "web" -> {
                        // Web deregistration detected but no details available (403 response)
                        Text(stringResource(R.string.device_deregistered_from_web_no_details))
                    }

                    else -> {
                        // Generic deregistration message
                        Text(stringResource(R.string.device_deregistered_generic, workspaceName))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.scan_qr_to_reregister))
            }
        },
        confirmButton = {
            TextButton(onClick = onRegisterAgain) {
                Text(stringResource(R.string.register_again))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Composable
private fun QuotaExceededDialog(
    currentUsage: Int,
    maxRecords: Int,
    quotaMessage: String?,
    nsAnalyticsUrl: String?,
    onDismiss: () -> Unit,
    onOpenNsAnalytics: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录配额已超限") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    quotaMessage ?: "您的工作区已达到记录上限。"
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "当前用量：${
                        NsAnalyticsUtils.formatNumberWithThousandsSeparator(currentUsage)
                    } / ${
                        NsAnalyticsUtils.formatNumberWithThousandsSeparator(maxRecords)
                    } 条记录",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "要继续上传记录，请：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• 删除 NS Analytics 中的现有记录，或",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• 升级到更高的订阅档位",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            if (nsAnalyticsUrl != null) {
                TextButton(onClick = onOpenNsAnalytics) {
                    Text("打开 NS Analytics")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun ClearQueueConfirmationDialog(
    queueSize: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空上传队列？") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("这将永久删除 $queueSize 条尚未上传的排队记录。此操作无法撤销。")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("清空队列")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Handles service connection lifecycle for the NS Analytics screen.
 * Binds to NetworkSurveyService when the composable enters composition
 * and unbinds when it leaves composition.
 */
@Composable
private fun NsAnalyticsServiceConnectionHandler(
    viewModel: NsAnalyticsConnectionViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isServiceBound by remember { mutableStateOf(false) }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val serviceBinder = binder as? NetworkSurveyService.SurveyServiceBinder
                val service = serviceBinder?.service as? NetworkSurveyService
                viewModel.setNetworkSurveyService(service)
                viewModel.onStart() // Start polling when service is connected
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                viewModel.onStop() // Stop polling when service is disconnected
                viewModel.setNetworkSurveyService(null)
            }
        }
    }

    // Get application context to avoid leaking activity context
    val applicationContext = remember { context.applicationContext }

    // Handle lifecycle events to unbind/rebind service when app goes to/from background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // App is going to background - unbind from service
                    if (isServiceBound) {
                        try {
                            // Stop polling
                            viewModel.onStop()
                            // Clear service reference in ViewModel
                            viewModel.setNetworkSurveyService(null)
                            // Unbind from the service
                            applicationContext.unbindService(serviceConnection)
                            isServiceBound = false
                        } catch (e: Exception) {
                            Timber.e(e, "Error unbinding service on background")
                        }
                    }
                }

                Lifecycle.Event.ON_START -> {
                    // App is returning to foreground - rebind to service
                    if (!isServiceBound) {
                        try {
                            val serviceIntent =
                                Intent(applicationContext, NetworkSurveyService::class.java)
                            val bound = applicationContext.bindService(
                                serviceIntent,
                                serviceConnection,
                                Context.BIND_AUTO_CREATE
                            )
                            if (bound) {
                                isServiceBound = true
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error rebinding service on foreground")
                        }
                    }
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(context) {
        // Initial binding to the service when the screen is first displayed
        if (!isServiceBound) {
            val serviceIntent = Intent(applicationContext, NetworkSurveyService::class.java)
            val bound = try {
                applicationContext.bindService(
                    serviceIntent,
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to bind to NetworkSurveyService")
                false
            }
            isServiceBound = bound
        }

        onDispose {
            // Stop polling and clean up service reference
            viewModel.onStop()
            viewModel.setNetworkSurveyService(null)

            // Unbind from service if still bound
            if (isServiceBound) {
                try {
                    applicationContext.unbindService(serviceConnection)
                    isServiceBound = false
                } catch (e: Exception) {
                    Timber.e(e, "Error unbinding service on disposal")
                }
            }
        }
    }
}

/**
 * Dialog shown to confirm workspace registration before proceeding.
 *
 * UX Design:
 * - Workspace name is prominently displayed as the focal point
 * - Technical details (API URL, Workspace ID) are in a collapsible section
 * - Fallback display when workspace_name is not provided in deep link
 */
@Composable
private fun RegistrationConfirmationDialog(
    qrData: com.craxiom.networksurvey.data.api.NsAnalyticsQrData,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var showTechnicalDetails by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "加入工作区？",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "您即将加入：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!qrData.workspaceName.isNullOrBlank()) {
                    Text(
                        text = qrData.workspaceName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    // Fallback when workspace_name is missing
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "工作区",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = qrData.workspaceId.take(12) +
                                    if (qrData.workspaceId.length > 12) "..." else "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "链接中未提供工作区名称",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontStyle = FontStyle.Italic
                        )
                    }
                }

                TechnicalDetailsSection(
                    isExpanded = showTechnicalDetails,
                    onToggle = { showTechnicalDetails = !showTechnicalDetails },
                    workspaceId = qrData.workspaceId,
                    apiUrl = qrData.apiUrl
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "加入工作区",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * Collapsible section showing technical details (API URL and Workspace ID).
 * Hidden by default to reduce visual clutter.
 */
@Composable
private fun TechnicalDetailsSection(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    workspaceId: String,
    apiUrl: String
) {
    // Smooth chevron rotation animation
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "chevron_rotation"
    )

    // Subtle background color change on expansion
    val toggleBackgroundColor by animateColorAsState(
        targetValue = if (isExpanded)
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.3f)
        else
            Color.Transparent,
        animationSpec = tween(300),
        label = "toggle_background"
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Toggle header with proper ripple feedback
        Surface(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(),
            color = toggleBackgroundColor
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "技术详情",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "收起技术详情" else "展开技术详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer(rotationZ = rotation)
                )
            }
        }

        // Expandable content with polished animations
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = tween(
                    durationMillis = 200,
                    easing = FastOutLinearInEasing
                )
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 100,
                    delayMillis = 100
                )
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = 150
                )
            ) + shrinkVertically(
                animationSpec = tween(
                    durationMillis = 250,
                    easing = FastOutSlowInEasing
                )
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.defaultColors(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TechnicalDetailItem(
                        label = "API 地址",
                        value = apiUrl
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    TechnicalDetailItem(
                        label = "工作区 ID",
                        value = workspaceId
                    )
                }
            }
        }
    }
}

/**
 * Single technical detail item with label above and full-width monospace value below.
 * Uses vertical layout so long URLs can wrap naturally without truncation.
 */
@Composable
private fun TechnicalDetailItem(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Dialog shown after successful workspace registration
 */
@Composable
private fun RegistrationSuccessDialog(
    workspaceName: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("注册成功！")
            }
        },
        text = {
            Column {
                Text(
                    text = "您已成功加入：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = workspaceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "启动测量即可开始采集并上传数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("完成")
            }
        }
    )
}

/**
 * Dialog shown when registration fails
 */
@Composable
private fun RegistrationErrorDialog(
    errorMessage: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("注册失败")
            }
        },
        text = {
            Column {
                Text(
                    text = "无法注册设备：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("重试")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * Dialog shown when user scans QR code while already registered to a workspace.
 * Informs the user they need to unregister first before registering with a new workspace.
 */
@Composable
private fun AlreadyRegisteredDialog(
    currentWorkspaceName: String,
    newWorkspaceName: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ns_analytics_qr_detected_title))
            }
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.ns_analytics_already_registered_message,
                        currentWorkspaceName
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (!newWorkspaceName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.ns_analytics_new_workspace_detected,
                            newWorkspaceName
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.ns_analytics_unregister_first_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

/**
 * Dialog shown to explain why Bluetooth permissions are needed.
 * Displayed when user tries to enable Bluetooth on Android 12+ without permissions.
 */
@Composable
private fun BluetoothPermissionRationaleDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bluetooth_permissions_rationale_title)) },
        text = { Text(stringResource(R.string.bluetooth_permissions_rationale)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Preview for the NS Analytics unregistered/not connected screen.
 * Shows the complete onboarding UI with hero section, setup guide, and CTAs.
 */
@Preview(
    name = "NS Analytics Not Connected - Dark",
    showBackground = true,
    backgroundColor = 0xFF121316,
    widthDp = 360,
    heightDp = 2000
)
@Composable
private fun NotConnectedCardPreview() {
    NsTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            NotConnectedCard()
        }
    }
}
