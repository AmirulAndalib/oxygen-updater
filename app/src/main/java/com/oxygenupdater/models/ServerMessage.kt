package com.oxygenupdater.models

import android.content.Context
import android.os.Parcelable
import androidx.core.content.ContextCompat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.oxygenupdater.R
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class ServerMessage(
    var id: Long = 0,
    var englishMessage: String? = null,
    var dutchMessage: String? = null,
    var deviceId: Long? = null,
    var updateMethodId: Long? = null,
    var priority: ServerMessagePriority? = null,
) : Banner, Parcelable {

    @IgnoredOnParcel
    @JsonIgnore
    val text = if (AppLocale.get() == AppLocale.NL) dutchMessage ?: englishMessage else englishMessage

    override fun getBannerText(context: Context) = if (AppLocale.get() == AppLocale.NL) dutchMessage ?: englishMessage else englishMessage

    override fun getColor(context: Context) = when (priority) {
        ServerMessagePriority.LOW -> ContextCompat.getColor(context, R.color.colorPositive)
        ServerMessagePriority.MEDIUM -> ContextCompat.getColor(context, R.color.colorWarn)
        ServerMessagePriority.HIGH -> ContextCompat.getColor(context, R.color.colorError)
        else -> ContextCompat.getColor(context, R.color.foreground)
    }

    override fun getDrawableRes(context: Context) = when (priority) {
        ServerMessagePriority.LOW -> R.drawable.info
        ServerMessagePriority.MEDIUM -> R.drawable.warning
        ServerMessagePriority.HIGH -> R.drawable.error
        else -> R.drawable.info
    }

    enum class ServerMessagePriority {
        LOW,
        MEDIUM,
        HIGH
    }
}
