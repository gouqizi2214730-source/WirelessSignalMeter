package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog

/**
 * Dialog explaining Bluetooth permission requirements.
 */
@Composable
fun BluetoothPermissionRationaleDialog(
    onRequestPermissions: () -> Unit,
    onDismiss: () -> Unit,
) {
    NsConfirmationDialog(
        title = stringResource(R.string.bluetooth_permissions_rationale_title),
        message = stringResource(R.string.bluetooth_permissions_rationale),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = onRequestPermissions,
        onDismiss = onDismiss,
        dismissText = null,
    )
}

/**
 * Dialog explaining required CDR permissions.
 */
@Composable
fun CdrRequiredPermissionDialog(
    onRequestPermissions: () -> Unit,
    onDismiss: () -> Unit,
) {
    NsConfirmationDialog(
        title = stringResource(R.string.cdr_required_permissions_rationale_title),
        message = stringResource(R.string.cdr_required_permissions_rationale),
        confirmText = stringResource(R.string.request),
        onConfirm = onRequestPermissions,
        onDismiss = onDismiss,
        dismissText = null,
    )
}

/**
 * Dialog explaining optional CDR permissions with an option to ignore.
 */
@Composable
fun CdrOptionalPermissionDialog(
    onRequestPermissions: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit,
) {
    NsConfirmationDialog(
        title = stringResource(R.string.cdr_optional_permissions_rationale_title),
        message = stringResource(R.string.cdr_optional_permissions_rationale),
        confirmText = stringResource(R.string.request),
        onConfirm = onRequestPermissions,
        onDismiss = onDismiss,
        dismissText = stringResource(R.string.ignore),
        onCancel = onIgnore,
    )
}