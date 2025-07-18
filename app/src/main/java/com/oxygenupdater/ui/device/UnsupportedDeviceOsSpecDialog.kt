package com.oxygenupdater.ui.device

import android.os.Build
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oxygenupdater.R
import com.oxygenupdater.models.DeviceOsSpec
import com.oxygenupdater.ui.common.CheckboxText
import com.oxygenupdater.ui.dialogs.AlertDialog
import com.oxygenupdater.ui.theme.PreviewAppTheme
import com.oxygenupdater.ui.theme.PreviewThemes

@Composable
fun UnsupportedDeviceOsSpecDialog(
    show: Boolean,
    hide: (Boolean) -> Unit,
    spec: DeviceOsSpec,
) {
    if (spec == DeviceOsSpec.SupportedDeviceAndOs) return

    var ignore by remember { mutableStateOf(false) }
    if (show) AlertDialog(
        action = { hide(ignore) },
        titleResId = R.string.unsupported_device_warning_title,
        text = if (spec == DeviceOsSpec.UnsupportedDevice) stringResource(
            R.string.unsupported_device_warning_message, Build.BRAND
        ) else stringResource(remember(spec) {
            when (spec) {
                DeviceOsSpec.CarrierExclusiveOxygenOs -> R.string.carrier_exclusive_device_warning_message
                DeviceOsSpec.UnsupportedDevice -> R.string.unsupported_device_warning_message
                DeviceOsSpec.UnsupportedDeviceAndOs -> R.string.unsupported_os_warning_message
                else -> R.string.unsupported_os_warning_message
            }
        }),
    ) {
        CheckboxText(
            checked = ignore, onCheckedChange = { ignore = it },
            textResId = R.string.do_not_show_again_checkbox,
            textColor = AlertDialogDefaults.textContentColor.copy(alpha = 0.87f),
            modifier = Modifier.offset((-12).dp) // bring in line with Text
        )
    }
}

@PreviewThemes
@Composable
fun PreviewCarrierExclusiveDialog() = PreviewAppTheme {
    UnsupportedDeviceOsSpecDialog(
        show = true,
        hide = {},
        spec = DeviceOsSpec.CarrierExclusiveOxygenOs,
    )
}

@PreviewThemes
@Composable
fun PreviewUnsupportedDeviceDialog() = PreviewAppTheme {
    UnsupportedDeviceOsSpecDialog(
        show = true,
        hide = {},
        spec = DeviceOsSpec.UnsupportedDevice,
    )
}

@PreviewThemes
@Composable
fun PreviewUnsupportedDeviceAndOsDialog() = PreviewAppTheme {
    UnsupportedDeviceOsSpecDialog(
        show = true,
        hide = {},
        spec = DeviceOsSpec.UnsupportedDeviceAndOs,
    )
}
