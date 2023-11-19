package com.oxygenupdater.dialogs

import android.app.Activity
import android.content.DialogInterface.BUTTON_NEGATIVE
import android.content.DialogInterface.BUTTON_NEUTRAL
import android.content.DialogInterface.BUTTON_POSITIVE
import androidx.annotation.StringRes
import com.oxygenupdater.R
import com.oxygenupdater.internal.KotlinCallback
import com.oxygenupdater.utils.LocalNotifications

object Dialogs {

    /**
     * Shows a [MessageDialog] with the occurred download error.
     *
     * @param title   Title of the error message
     * @param message Contents of the error message
     */
    fun showDownloadError(
        activity: Activity?,
        isResumable: Boolean,
        @StringRes title: Int,
        @StringRes message: Int,
        callback: KotlinCallback<Boolean>? = null,
    ) = checkPreconditions(activity) {
        showDownloadError(
            activity!!,
            isResumable,
            activity.getString(title),
            activity.getString(message),
            callback
        )
    }

    /**
     * Shows a [MessageDialog] with the occurred download error.
     *
     * @param title   Title of the error message
     * @param message Contents of the error message
     */
    fun showDownloadError(
        activity: Activity?,
        isResumable: Boolean,
        title: String?,
        message: CharSequence?,
        callback: KotlinCallback<Boolean>? = null,
    ) = checkPreconditions(activity) {
        MessageDialog(
            activity!!,
            title = title,
            message = message,
            positiveButtonText = when {
                callback == null -> null
                isResumable -> activity.getString(R.string.download_error_resume)
                else -> activity.getString(R.string.download_error_retry)
            },
            negativeButtonText = activity.getString(R.string.download_error_close),
            positiveButtonIcon = when {
                callback == null -> null
                isResumable -> R.drawable.download
                else -> R.drawable.auto
            },
            cancellable = true
        ) {
            when (it) {
                BUTTON_POSITIVE -> {
                    LocalNotifications.hideDownloadCompleteNotification()
                    callback?.invoke(isResumable)
                }

                BUTTON_NEGATIVE -> LocalNotifications.hideDownloadCompleteNotification()
                BUTTON_NEUTRAL -> {} // no-op
            }
        }.show()
    }

    private fun checkPreconditions(activity: Activity?, callback: () -> Unit) {
        if (activity?.isFinishing == false) {
            callback.invoke()
        }
    }
}
