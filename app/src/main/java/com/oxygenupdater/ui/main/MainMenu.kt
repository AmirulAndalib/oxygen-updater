package com.oxygenupdater.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oxygenupdater.R
import com.oxygenupdater.icons.Announcement
import com.oxygenupdater.icons.CustomIcons
import com.oxygenupdater.models.ServerMessage
import com.oxygenupdater.ui.common.DropdownMenuItem
import com.oxygenupdater.ui.common.rememberSaveableState
import com.oxygenupdater.ui.dialogs.ContributorSheet
import com.oxygenupdater.ui.dialogs.ModalBottomSheet
import com.oxygenupdater.ui.dialogs.ServerMessagesSheet
import com.oxygenupdater.ui.theme.PreviewAppTheme
import com.oxygenupdater.ui.theme.PreviewThemes
import com.oxygenupdater.utils.ContributorUtils

@Composable
fun MainMenu(
    serverMessages: List<ServerMessage>,
    showMarkAllRead: Boolean,
    onMarkAllReadClick: () -> Unit,
    onContributorEnrollmentChange: (Boolean) -> Unit,
) {
    AnnouncementsMenuItem(serverMessages = serverMessages)

    // Don't show menu if there are no items in it
    val showBecomeContributor = ContributorUtils.isAtLeastQAndPossiblyRooted
    if (!showMarkAllRead && !showBecomeContributor) return

    // Box layout is required to make DropdownMenu position correctly (directly under icon)
    Box {
        // Hide other menu items behind overflow icon
        var showMenu by rememberSaveableState("showMenu", false)
        IconButton({ showMenu = true }, Modifier.requiredWidth(40.dp)) {
            Icon(Icons.Rounded.MoreVert, stringResource(androidx.compose.ui.R.string.dropdown_menu))
        }

        DropdownMenu(showMenu, { showMenu = false }) {
            // Mark all articles read
            if (showMarkAllRead) DropdownMenuItem(
                icon = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                textResId = R.string.news_mark_all_read,
            ) {
                onMarkAllReadClick()
                showMenu = false
            }

            // OTA URL contribution
            if (showBecomeContributor) ContributorMenuItem(
                onDismiss = { showMenu = false },
                onContributorEnrollmentChange = onContributorEnrollmentChange,
            )
        }
    }
}

/** Server-provided info & warning messages */
@Composable
private fun AnnouncementsMenuItem(serverMessages: List<ServerMessage>) {
    if (serverMessages.isEmpty()) return

    var showSheet by rememberSaveableState("showServerMessagesSheet", false)
    IconButton({ showSheet = true }, Modifier.requiredWidth(40.dp)) {
        Icon(CustomIcons.Announcement, stringResource(R.string.update_information_banner_server))
    }

    if (showSheet) ModalBottomSheet({ showSheet = false }) { ServerMessagesSheet(serverMessages) }
}

@Composable
private fun ContributorMenuItem(
    onDismiss: () -> Unit,
    onContributorEnrollmentChange: (Boolean) -> Unit,
) {
    var showSheet by rememberSaveableState("showContributorSheet", false)

    DropdownMenuItem(Icons.Outlined.GroupAdd, R.string.contribute) {
        showSheet = true
        onDismiss()
    }

    if (showSheet) ModalBottomSheet({ showSheet = false }) {
        ContributorSheet(
            hide = it,
            confirm = onContributorEnrollmentChange,
        )
    }
}

@PreviewThemes
@Composable
fun PreviewMainMenu() = PreviewAppTheme {
    Row {
        val message = "An unnecessarily long server message, to get an accurate understanding of how long titles are rendered"
        MainMenu(
            serverMessages = listOf(
                ServerMessage(
                    1L,
                    text = message,
                    deviceId = null,
                    updateMethodId = null,
                    priority = ServerMessage.ServerMessagePriority.LOW,
                ),
                ServerMessage(
                    2L,
                    text = message,
                    deviceId = null,
                    updateMethodId = null,
                    priority = ServerMessage.ServerMessagePriority.MEDIUM,
                ),
                ServerMessage(
                    3L,
                    text = message,
                    deviceId = null,
                    updateMethodId = null,
                    priority = ServerMessage.ServerMessagePriority.HIGH,
                ),
            ),
            showMarkAllRead = true,
            onMarkAllReadClick = {},
            onContributorEnrollmentChange = {},
        )
    }
}
