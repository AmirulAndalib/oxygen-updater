package com.oxygenupdater.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface.BUTTON_NEGATIVE
import android.content.DialogInterface.BUTTON_NEUTRAL
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AnimationDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.provider.Settings
import android.text.SpannableString
import android.text.format.DateUtils
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.work.WorkInfo
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.oxygenupdater.OxygenUpdater.Companion.DOWNLOAD_FILE_PERMISSION
import com.oxygenupdater.OxygenUpdater.Companion.VERIFY_FILE_PERMISSION
import com.oxygenupdater.R
import com.oxygenupdater.activities.MainActivity
import com.oxygenupdater.databinding.FragmentUpdateInformationBinding
import com.oxygenupdater.dialogs.Dialogs.showDownloadError
import com.oxygenupdater.dialogs.MessageDialog
import com.oxygenupdater.enums.DownloadFailure
import com.oxygenupdater.enums.DownloadStatus
import com.oxygenupdater.enums.DownloadStatus.DOWNLOADING
import com.oxygenupdater.enums.DownloadStatus.DOWNLOAD_COMPLETED
import com.oxygenupdater.enums.DownloadStatus.DOWNLOAD_FAILED
import com.oxygenupdater.enums.DownloadStatus.DOWNLOAD_PAUSED
import com.oxygenupdater.enums.DownloadStatus.DOWNLOAD_QUEUED
import com.oxygenupdater.enums.DownloadStatus.NOT_DOWNLOADING
import com.oxygenupdater.enums.DownloadStatus.VERIFICATION_COMPLETED
import com.oxygenupdater.enums.DownloadStatus.VERIFICATION_FAILED
import com.oxygenupdater.enums.DownloadStatus.VERIFYING
import com.oxygenupdater.extensions.formatFileSize
import com.oxygenupdater.extensions.setImageResourceWithTint
import com.oxygenupdater.extensions.startInstallActivity
import com.oxygenupdater.internal.DeviceInformationData
import com.oxygenupdater.internal.KotlinCallback
import com.oxygenupdater.internal.settings.PrefManager
import com.oxygenupdater.internal.settings.PrefManager.PROPERTY_UPDATE_CHECKED_DATE
import com.oxygenupdater.models.SystemVersionProperties
import com.oxygenupdater.models.UpdateData
import com.oxygenupdater.utils.LocalNotifications
import com.oxygenupdater.utils.Logger.logDebug
import com.oxygenupdater.utils.Logger.logError
import com.oxygenupdater.utils.Logger.logInfo
import com.oxygenupdater.utils.Logger.logWarning
import com.oxygenupdater.utils.UpdateDataVersionFormatter.canVersionInfoBeFormatted
import com.oxygenupdater.utils.UpdateDataVersionFormatter.getFormattedOxygenOsVersion
import com.oxygenupdater.utils.UpdateDataVersionFormatter.getFormattedVersionNumber
import com.oxygenupdater.utils.UpdateDescriptionParser
import com.oxygenupdater.utils.Utils
import com.oxygenupdater.utils.Utils.SERVER_TIME_ZONE
import com.oxygenupdater.viewmodels.MainViewModel
import com.oxygenupdater.workers.WORK_DATA_DOWNLOAD_BYTES_DONE
import com.oxygenupdater.workers.WORK_DATA_DOWNLOAD_ETA
import com.oxygenupdater.workers.WORK_DATA_DOWNLOAD_FAILURE_TYPE
import com.oxygenupdater.workers.WORK_DATA_DOWNLOAD_PROGRESS
import com.oxygenupdater.workers.WORK_DATA_DOWNLOAD_TOTAL_BYTES
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import java.io.IOException
import java.time.LocalDateTime
import java.util.*

class UpdateInformationFragment : Fragment() {

    private var updateData: UpdateData? = null
    private var isLoadedOnce = false

    private val crashlytics by inject<FirebaseCrashlytics>()
    private val mainViewModel by activityViewModel<MainViewModel>()

    /**
     * Android 6.0 run-time permissions
     */
    private val hasDownloadPermissions: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context?.let {
                ContextCompat.checkSelfPermission(
                    it,
                    VERIFY_FILE_PERMISSION
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    it,
                    DOWNLOAD_FILE_PERMISSION
                ) == PackageManager.PERMISSION_GRANTED
            } ?:
            // If context is somehow null, we can't check if permissions are granted.
            // So, make an educated guess: on API < 23 both READ & WRITE permissions
            // are granted automatically, while on API >= 23 only READ is granted
            // automatically. A simple SDK version check should suffice.
            (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M)
        }

    /**
     * Allows an already downloaded update file to be deleted to save storage space.
     */
    private val updateAlreadyDownloadedDialog by lazy(LazyThreadSafetyMode.NONE) {
        MessageDialog(
            requireActivity(),
            title = getString(R.string.delete_message_title),
            message = getString(R.string.delete_message_contents),
            positiveButtonText = getString(R.string.install),
            negativeButtonText = getString(R.string.delete_message_delete_button),
            positiveButtonIcon = R.drawable.install,
            cancellable = true
        ) {
            when (it) {
                BUTTON_POSITIVE -> {
                    if (!isAdded) {
                        return@MessageDialog
                    }

                    activity?.startInstallActivity(
                        true,
                        binding?.root?.findViewById(R.id.downloadActionButton)!!
                    )
                }
                BUTTON_NEGATIVE -> if (updateData != null) {
                    if (hasDownloadPermissions) {
                        mainViewModel.deleteDownloadedFile(requireContext(), updateData)
                    } else {
                        requestDownloadPermissions { granted ->
                            if (granted) {
                                mainViewModel.deleteDownloadedFile(
                                    requireContext(),
                                    updateData
                                )
                            }
                        }
                    }
                }
                BUTTON_NEUTRAL -> {
                    // no-op
                }
            }
        }
    }

    private val noSpaceForDownloadDialog by lazy(LazyThreadSafetyMode.NONE) {
        MessageDialog(
            requireActivity(),
            title = getString(R.string.download_notification_error_storage_full),
            message = getString(R.string.download_error_storage),
            positiveButtonText = getString(android.R.string.ok),
            negativeButtonText = getString(R.string.download_error_close),
            positiveButtonIcon = R.drawable.install,
            cancellable = true
        )
    }

    /**
     * Download button click listener. Performs these actions when the button is clicked.
     */
    private val downloadNotStartedOnClickListener = View.OnClickListener {
        val rootView = binding?.root
        if (isAdded && rootView?.findViewById<View>(R.id.updateInformationLayout) != null) {
            if (hasDownloadPermissions) {
                enqueueDownloadWork()

                rootView.findViewById<ProgressBar>(R.id.downloadProgressBar)?.isIndeterminate = true
                rootView.findViewById<TextView>(R.id.downloadDetailsTextView)?.setText(R.string.download_pending)

                // Pause is possible on first progress update
                rootView.findViewById<View>(R.id.downloadLayout)?.setOnClickListener { }
            } else {
                requestDownloadPermissions { granted ->
                    if (granted) {
                        enqueueDownloadWork()
                    }
                }
            }
        }
    }

    private var downloadPermissionCallback: KotlinCallback<Boolean>? = null

    /**
     * Although we request both the [VERIFY_FILE_PERMISSION] and the [DOWNLOAD_FILE_PERMISSION],
     * only the latter *needs* to be granted. Which is why this listener passes the grant status
     * of this permission only.
     */
    private val requestPermissionLauncher = registerForActivityResult(
        RequestMultiplePermissions()
    ) {
        logInfo(TAG, "Permissions granted: $it")

        downloadPermissionCallback?.invoke(it[DOWNLOAD_FILE_PERMISSION] == true)
    }

    private val manageStorageLauncher = registerForActivityResult(
        StartActivityForResult()
    ) {
        when (it.resultCode) {
            Activity.RESULT_OK -> logInfo(TAG, "User freed-up space").also {
                // Even though it's impossible for this `lateinit` property to be
                // uninitialized, it's better to guard against a crash just in case
                // future code changes create unintentional bugs (i.e. we may forget to
                // ensure that [setupDownloadWorkRequest] is always called before this).
                if (!mainViewModel.isDownloadWorkInitialized) {
                    mainViewModel.setupDownloadWorkRequest(updateData!!)
                }

                // Since the required space has been freed up, we can enqueue the download work
                mainViewModel.enqueueDownloadWork()
            }
            Activity.RESULT_CANCELED -> logInfo(TAG, "User didn't free-up space").also {
                showDownloadError(R.string.download_error_storage)
            }
            else -> logWarning(TAG, "Unhandled resultCode: ${it.resultCode}")
        }
    }

    /** Only valid between `onCreateView` and `onDestroyView` */
    private var binding: FragmentUpdateInformationBinding? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = FragmentUpdateInformationBinding.inflate(inflater, container, false).run {
        binding = this
        root
    }

    override fun onDestroyView() = super.onDestroyView().also {
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (PrefManager.checkIfSetupScreenHasBeenCompleted()) {
            val analytics by inject<FirebaseAnalytics>()

            binding?.swipeRefreshLayout?.apply {
                setOnRefreshListener { load() }
                setColorSchemeResources(R.color.colorPrimary)
            }

            analytics.setUserProperty(
                "device_name",
                SystemVersionProperties.oxygenDeviceName
            )

            setupServerResponseObservers()
            setupWorkObservers()

            mainViewModel.settingsChanged.observe(viewLifecycleOwner) {
                load()
            }

            load()
        }
    }

    override fun onResume() = super.onResume().also {
        if (isLoadedOnce) {
            if (mainViewModel.checkDownloadCompletionByFile(updateData)) {
                mainViewModel.updateDownloadStatus(DOWNLOAD_COMPLETED)
            } else {
                mainViewModel.updateDownloadStatus(NOT_DOWNLOADING)
            }
        }
    }

    override fun onDestroy() = super.onDestroy().also {
        mainViewModel.maybePruneWork()
    }

    private fun setupServerResponseObservers() {
        mainViewModel.serverStatus.observe(viewLifecycleOwner) { serverStatus ->
            // display server status banner if required
            val status = serverStatus.status
            if (status!!.isUserRecoverableError) {
                binding?.serverStatusTextView?.apply {
                    isVisible = true
                    text = serverStatus.getBannerText(requireContext())
                    setBackgroundColor(serverStatus.getColor(requireContext()))
                    setCompoundDrawablesRelativeWithIntrinsicBounds(serverStatus.getDrawableRes(requireContext()), 0, 0, 0)
                }
            } else {
                binding?.serverStatusTextView?.isVisible = false
            }
        }

        mainViewModel.updateData.observe(viewLifecycleOwner) {
            if (it == null) {
                inflateAndShowErrorState()
            } else {
                hideErrorStateIfInflated()

                this.updateData = it

                // If the activity is started with a download error (when clicked on a "download failed" notification), show it to the user.
                if (!isLoadedOnce && activity?.intent?.getBooleanExtra(KEY_HAS_DOWNLOAD_ERROR, false) == true) {
                    val intent = requireActivity().intent
                    showDownloadError(
                        activity,
                        intent.getBooleanExtra(KEY_DOWNLOAD_ERROR_RESUMABLE, false),
                        intent.getStringExtra(KEY_DOWNLOAD_ERROR_TITLE),
                        intent.getStringExtra(KEY_DOWNLOAD_ERROR_MESSAGE)
                    ) { isResumable ->
                        if (!isResumable) {
                            // Delete downloaded file, so the user can restart the download
                            mainViewModel.deleteDownloadedFile(
                                requireContext(),
                                updateData,
                                true
                            )
                        }

                        // Setup the work request before enqueueing it
                        mainViewModel.setupDownloadWorkRequest(updateData!!)
                        enqueueDownloadWork()
                    }
                }

                displayUpdateInformation(updateData!!)
                isLoadedOnce = true
            }
        }
    }

    private fun setupWorkObservers() {
        mainViewModel.downloadWorkInfo.observe(viewLifecycleOwner) {
            if (it.isNotEmpty()) {
                mainViewModel.updateDownloadStatus(it.first())
            }
        }

        mainViewModel.verificationWorkInfo.observe(viewLifecycleOwner) {
            if (it.isNotEmpty()) {
                mainViewModel.updateDownloadStatus(it.first(), true)
            }
        }

        mainViewModel.downloadStatus.observe(viewLifecycleOwner) {
            logDebug(TAG, "Download status updated: ${it.first}")

            initDownloadLayout(it)
        }
    }

    /**
     * Fetches all server data. This includes update information, server messages and server status
     * checks
     */
    private fun load() {
        // show the loading shimmer
        binding?.shimmerFrameLayout?.isVisible = true

        crashlytics.setUserId(
            "Device: "
                    + PrefManager.getString(PrefManager.PROPERTY_DEVICE, "<UNKNOWN>")
                    + ", Update Method: "
                    + PrefManager.getString(PrefManager.PROPERTY_UPDATE_METHOD, "<UNKNOWN>")
        )

        val deviceId = PrefManager.getLong(PrefManager.PROPERTY_DEVICE_ID, -1L)
        val updateMethodId = PrefManager.getLong(PrefManager.PROPERTY_UPDATE_METHOD_ID, -1L)

        mainViewModel.fetchServerStatus()

        mainViewModel.fetchUpdateData(
            deviceId,
            updateMethodId,
            SystemVersionProperties.oxygenOSOTAVersion
        )
    }

    private fun inflateAndShowErrorState() {
        // Hide the loading shimmer since an error state can only be enabled after a load completes
        binding?.shimmerFrameLayout?.isVisible = false
        // Hide the refreshing icon if it is present
        binding?.swipeRefreshLayout?.isRefreshing = false

        // Show error layout
        if (binding?.errorLayoutStub?.parent != null) {
            binding?.errorLayoutStub?.inflate()
        }

        val rootView = binding?.root
        rootView?.findViewById<View>(R.id.errorLayout)?.isVisible = true
        // Hide "System update available" view
        rootView?.findViewById<View>(R.id.updateInformationLayout)?.isVisible = false
        // Hide "System is up to date" view
        rootView?.findViewById<View>(R.id.systemIsUpToDateLayout)?.isVisible = false

        rootView?.findViewById<TextView>(R.id.errorTitle)?.text = getString(R.string.update_information_error_title)
        // Make the links clickable
        rootView?.findViewById<TextView>(R.id.errorText)?.movementMethod = LinkMovementMethod.getInstance()

        rootView?.findViewById<View>(R.id.errorActionButton)?.setOnClickListener { load() }
    }

    private fun hideErrorStateIfInflated() {
        // Stub is null only after it has been inflated, and
        // we need to hide the error state only if it has been inflated
        if (binding?.errorLayoutStub == null || binding?.errorLayoutStub?.parent == null) {
            val rootView = binding?.root
            rootView?.findViewById<View>(R.id.errorLayout)?.isVisible = false
            rootView?.findViewById<View>(R.id.errorActionButton)?.setOnClickListener { }
        }
    }

    /**
     * Displays the update information from a [UpdateData] with update information.
     *
     * @param updateData Update information to display
     */
    private fun displayUpdateInformation(updateData: UpdateData) {
        if (!isAdded) {
            return
        }

        val online = Utils.checkNetworkConnection()

        // hide the loading shimmer
        binding?.shimmerFrameLayout?.isVisible = false
        // Hide the refreshing icon if it is present
        binding?.swipeRefreshLayout?.isRefreshing = false

        val systemIsUpToDate = updateData.systemIsUpToDate && !PrefManager.getBoolean(
            PrefManager.PROPERTY_ADVANCED_MODE,
            false
        )

        if (updateData.id == null
            || systemIsUpToDate
            || !updateData.isUpdateInformationAvailable
        ) {
            propagateTitleAndSubtitleChanges(
                getString(
                    if (updateData.isUpdateInformationAvailable) {
                        R.string.update_information_system_is_up_to_date
                    } else {
                        R.string.update_information_no_update_data_available
                    }
                )
            )

            displayUpdateInformationWhenUpToDate(updateData, online)
        } else {
            propagateTitleAndSubtitleChanges(
                getString(
                    if (updateData.systemIsUpToDate) {
                        R.string.update_information_header_advanced_mode_hint
                    } else {
                        R.string.update_notification_channel_name
                    }
                )
            )

            displayUpdateInformationWhenNotUpToDate(updateData)
        }

        if (online) {
            // Save update data for offline viewing
            PrefManager.apply {
                putLong(PROPERTY_OFFLINE_ID, updateData.id ?: -1L)
                putString(PROPERTY_OFFLINE_UPDATE_NAME, updateData.versionNumber)
                putLong(PROPERTY_OFFLINE_UPDATE_DOWNLOAD_SIZE, updateData.downloadSize)
                putString(PROPERTY_OFFLINE_UPDATE_DESCRIPTION, updateData.description)
                putString(PROPERTY_OFFLINE_FILE_NAME, updateData.filename)
                putString(PROPERTY_OFFLINE_DOWNLOAD_URL, updateData.downloadUrl)
                putBoolean(PROPERTY_OFFLINE_UPDATE_INFORMATION_AVAILABLE, updateData.isUpdateInformationAvailable)
                putString(PROPERTY_UPDATE_CHECKED_DATE, LocalDateTime.now(SERVER_TIME_ZONE).toString())
                putBoolean(PROPERTY_OFFLINE_IS_UP_TO_DATE, updateData.systemIsUpToDate)
            }
        }
    }

    private fun displayUpdateInformationWhenNotUpToDate(
        updateData: UpdateData
    ) {
        // Show "System update available" view.
        if (binding?.updateInformationLayoutStub?.parent != null) {
            binding?.updateInformationLayoutStub?.inflate()
        }

        val rootView = binding?.root
        rootView?.findViewById<View>(R.id.updateInformationLayout)?.isVisible = true
        // Hide "System is up to date" view
        rootView?.findViewById<View>(R.id.systemIsUpToDateLayout)?.isVisible = false

        // Hide download link (it's supposed to be shown only in case of failure)
        rootView?.findViewById<View>(R.id.downloadLinkTextView)?.isVisible = false

        rootView?.findViewById<TextView>(R.id.oxygenOsVersionTextView)?.text = if (updateData.versionNumber != null && updateData.versionNumber != "null") {
            if (canVersionInfoBeFormatted(updateData)) {
                getFormattedVersionNumber(updateData)
            } else {
                updateData.versionNumber
            }
        } else {
            getString(
                R.string.update_information_unknown_update_name,
                PrefManager.getString(PrefManager.PROPERTY_DEVICE, getString(R.string.device_information_unknown))
            )
        }

        val visible = updateData.systemIsUpToDate
        val footerTextView = rootView?.findViewById<TextView>(R.id.footerTextView)
        footerTextView?.isVisible = visible
        rootView?.findViewById<View>(R.id.footerDivider)?.isVisible = visible
        if (visible) {
            val updateMethod = PrefManager.getString(PrefManager.PROPERTY_UPDATE_METHOD, "<UNKNOWN>")

            // Format footer based on system version installed.
            footerTextView?.text = getString(R.string.update_information_header_advanced_mode_helper, updateMethod)
        } else {
            // display badge to indicate a new system update is available
            (activity as MainActivity?)?.updateTabBadge(R.id.page_update)
        }

        // Display download size.
        rootView?.findViewById<TextView>(R.id.downloadSizeTextView)?.text = context?.formatFileSize(
            updateData.downloadSize
        )

        // Display update description.
        rootView?.findViewById<TextView>(R.id.changelogTextView)?.apply {
            movementMethod = LinkMovementMethod.getInstance()
            text = getUpdateChangelog(updateData)
        }

        // Display update file name.
        rootView?.findViewById<TextView>(R.id.fileNameTextView)?.text = getString(R.string.update_information_file_name, updateData.filename)
        rootView?.findViewById<TextView>(R.id.md5TextView)?.text = getString(R.string.update_information_md5, updateData.mD5Sum)

        // Setup the work request before enqueueing it
        mainViewModel.setupDownloadWorkRequest(updateData)

        if (mainViewModel.checkDownloadCompletionByFile(updateData)) {
            mainViewModel.updateDownloadStatus(DOWNLOAD_COMPLETED)
        } else {
            mainViewModel.updateDownloadStatus(NOT_DOWNLOADING)
        }
    }

    private fun displayUpdateInformationWhenUpToDate(
        updateData: UpdateData,
        online: Boolean
    ) {
        val rootView = binding?.root

        // Show "System is up to date" view.
        if (binding?.systemIsUpToDateLayoutStub?.parent != null) {
            binding?.systemIsUpToDateLayoutStub!!.inflate()
        }
        rootView?.findViewById<View>(R.id.systemIsUpToDateLayout)?.isVisible = true
        // Hide "System update available" view
        rootView?.findViewById<View>(R.id.updateInformationLayout)?.isVisible = false

        // https://stackoverflow.com/a/60542345
        rootView?.findViewById<ViewGroup>(R.id.systemIsUpToDateLayoutChild)?.layoutTransition?.setAnimateParentHierarchy(false)

        val incomingOtaVersion = updateData.otaVersionNumber
        val currentOtaVersion = SystemVersionProperties.oxygenOSOTAVersion
        val isDifferentVersion = incomingOtaVersion != currentOtaVersion
        val method = PrefManager.getString(PrefManager.PROPERTY_UPDATE_METHOD, "<UNKNOWN>") ?: "<UNKNOWN>"
        val visible = isDifferentVersion
                // ...and incoming is newer (older builds can't be installed due to standard Android security measures)
                && UpdateData.getBuildDate(incomingOtaVersion) >= UpdateData.getBuildDate(currentOtaVersion)
                // ...and incoming is likely a full ZIP, or the selected update method is "full"
                // (incrementals are only for specific source/target version combos)
                && (updateData.downloadSize >= FULL_ZIP_LIKELY_MIN_SIZE || method.endsWith("(full)") || method.endsWith("(volledig)"))

        rootView?.findViewById<TextView>(R.id.advancedModeTipTextView)?.run {
            isVisible = visible
            rootView.findViewById<View>(R.id.advancedModeTipDivider)?.isVisible = visible

            if (visible) text = getString(R.string.update_information_banner_advanced_mode_tip, method)
        }

        // Save last time checked if online.
        val now = LocalDateTime.now(SERVER_TIME_ZONE).toString()
        if (online) PrefManager.putString(PROPERTY_UPDATE_CHECKED_DATE, now)

        // Show last time checked.
        val updateCheckedDateStr = PrefManager.getString(
            PROPERTY_UPDATE_CHECKED_DATE,
            now
        )?.replace(" ", "T") ?: now
        val userDateTime = LocalDateTime.parse(updateCheckedDateStr)
            .atZone(SERVER_TIME_ZONE)

        rootView?.findViewById<TextView>(R.id.updateLastCheckedField)?.text = getString(
            R.string.update_information_last_checked_on,
            DateUtils.getRelativeTimeSpanString(
                userDateTime.toInstant().toEpochMilli(),
                System.currentTimeMillis(),
                DateUtils.SECOND_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL
            )
        )

        displaySoftwareInfo()
        setupChangelogViews(isDifferentVersion)
    }

    private fun displaySoftwareInfo() {
        if (!isAdded) {
            logDebug(TAG, "Fragment not added. Can not display software information!")
            return
        }

        val rootView = binding?.root
        rootView?.findViewById<View>(R.id.softwareHeader)?.isVisible = false

        // Android version
        rootView?.findViewById<TextView>(R.id.osVersionField)?.text = DeviceInformationData.osVersion

        // OxygenOS version (if available)
        rootView?.findViewById<TextView>(R.id.oxygenOsVersionField)?.run {
            val oxygenOSVersion = getFormattedOxygenOsVersion(SystemVersionProperties.oxygenOSVersion)

            if (oxygenOSVersion != Build.UNKNOWN) {
                text = oxygenOSVersion
            } else {
                rootView.findViewById<View>(R.id.oxygenOsVersionLabel)?.isVisible = false
                isVisible = false
            }
        }

        // OxygenOS OTA version (if available)
        rootView?.findViewById<TextView>(R.id.otaVersionField)?.run {
            val oxygenOSOTAVersion = SystemVersionProperties.oxygenOSOTAVersion

            if (oxygenOSOTAVersion != Build.UNKNOWN) {
                text = oxygenOSOTAVersion
            } else {
                rootView.findViewById<View>(R.id.otaVersionLabel)?.isVisible = false
                isVisible = false
            }
        }

        // Incremental OS version
        rootView?.findViewById<TextView>(R.id.incrementalOsVersionField)?.text = DeviceInformationData.incrementalOsVersion

        // Security Patch Date (if available)
        rootView?.findViewById<TextView>(R.id.securityPatchField)?.run {
            val securityPatchDate = SystemVersionProperties.securityPatchDate

            if (securityPatchDate != Build.UNKNOWN) {
                text = securityPatchDate
            } else {
                rootView.findViewById<View>(R.id.securityPatchLabel)?.isVisible = false
                isVisible = false
            }
        }
    }

    private fun requestDownloadPermissions(
        callback: KotlinCallback<Boolean>
    ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            // This opens up a screen where the user can grant "All files access" to the app.
            // Since there's no result/output, there's no callback to receive it.
            startActivity(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            )

            // Show a toast asking users what to do
            // TODO: make it a dialog
            Toast.makeText(
                context,
                R.string.grant_all_files_access,
                LENGTH_LONG
            ).show()
        } catch (e: ActivityNotFoundException) {
            logError(TAG, "Couldn't open MANAGE_ALL_FILES_ACCESS settings screen", e)
        }
    } else {
        downloadPermissionCallback = callback
        // Request both READ and WRITE permissions, since there may be cases
        // when the WRITE permission is granted but READ isn't.
        requestPermissionLauncher.launch(
            arrayOf(DOWNLOAD_FILE_PERMISSION, VERIFY_FILE_PERMISSION)
        )
    }

    /**
     * This utility function shows the user a dialog asking them to free up
     * some space, so that the app can download the update ZIP successfully.
     *
     * If the user accepts this request, [manageStorageLauncher] is launched
     * which shows an Android system-supplied "Remove items" UI (API 26+)
     * that guides the user on clearing up the required storage space.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestManageStorage(
        appSpecificExternalDirUuid: UUID,
        requiredFreeBytes: Long,
        allocatableBytes: Long
    ) = noSpaceForDownloadDialog.show {
        if (it == Dialog.BUTTON_POSITIVE) {
            manageStorageLauncher.launch(
                Intent(
                    StorageManager.ACTION_MANAGE_STORAGE
                ).putExtras(
                    bundleOf(
                        StorageManager.EXTRA_UUID to appSpecificExternalDirUuid,
                        StorageManager.EXTRA_REQUESTED_BYTES to requiredFreeBytes + SAFE_MARGIN - allocatableBytes
                    )
                )
            )
        } else {
            // To avoid duplicate clicks, we remove the click listener in
            // [downloadNotStartedOnClickListener]. But since this function is
            // called from that listener's flow itself, we need to now allow
            // the user to click the download button again.
            binding?.root?.findViewById<View>(R.id.downloadLayout)?.setOnClickListener(downloadNotStartedOnClickListener)
        }
    }

    /**
     * Calls [MainViewModel.enqueueDownloadWork] whenever appropriate
     */
    private fun enqueueDownloadWork() {
        val context = context ?: return
        val updateData = updateData ?: return

        val requiredFreeBytes = updateData.downloadSize
        val externalFilesDir = context.getExternalFilesDir(null)!!

        // Even though it's impossible for this `lateinit` property to be
        // uninitialized, it's better to guard against a crash just in case
        // future code changes create unintentional bugs (i.e. we may forget to
        // ensure that [setupDownloadWorkRequest] is always called before this).
        if (!mainViewModel.isDownloadWorkInitialized) {
            mainViewModel.setupDownloadWorkRequest(updateData)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val storageManager = context.getSystemService<StorageManager>()!!
            val appSpecificExternalDirUuid = storageManager.getUuidForPath(externalFilesDir)

            // Get maximum bytes that can be allocated by the system to the app
            // This value is usually larger than [File.usableSpace],
            // because the system considers cached files that can be deleted
            val allocatableBytes = storageManager.getAllocatableBytes(appSpecificExternalDirUuid)
            if (allocatableBytes >= requiredFreeBytes + SAFE_MARGIN) {
                try {
                    // Allocate bytes: the system will delete cached files if
                    // necessary to fulfil this request, or throw an IOException
                    // if it fails for whatever reason.
                    storageManager.allocateBytes(appSpecificExternalDirUuid, requiredFreeBytes)

                    // Since the required space has been freed up, we can enqueue the download work
                    mainViewModel.enqueueDownloadWork()
                } catch (e: IOException) {
                    // Request the user to free up space manually because the
                    // system couldn't do it automatically
                    requestManageStorage(
                        appSpecificExternalDirUuid,
                        requiredFreeBytes,
                        allocatableBytes
                    )
                }
            } else {
                requestManageStorage(
                    appSpecificExternalDirUuid,
                    requiredFreeBytes,
                    allocatableBytes
                )
            }
        } else {
            // Check if there is enough free storage space before downloading
            val usableBytes = externalFilesDir.usableSpace

            if (usableBytes >= requiredFreeBytes + SAFE_MARGIN) {
                // Since we have enough space available, we can enqueue the download work
                mainViewModel.enqueueDownloadWork()
            } else {
                // Don't have enough space to complete the download. Display a notification and an error dialog to the user
                LocalNotifications.showDownloadFailedNotification(
                    context,
                    false,
                    R.string.download_error_storage,
                    R.string.download_notification_error_storage_full
                )

                showDownloadError(
                    activity,
                    false,
                    R.string.download_error,
                    R.string.download_error_storage
                )
            }
        }
    }

    private fun setupChangelogViews(isDifferentVersion: Boolean) {
        val rootView = binding?.root
        val changelogField = rootView?.findViewById<TextView>(R.id.changelogField)
        val differentVersionChangelogNotice = rootView?.findViewById<TextView>(R.id.differentVersionChangelogNotice)

        changelogField?.text = getUpdateChangelog(updateData)
        differentVersionChangelogNotice?.text = getString(
            R.string.update_information_different_version_changelog_notice,
            PrefManager.getString(PrefManager.PROPERTY_UPDATE_METHOD, "<UNKNOWN>")
        )

        rootView?.findViewById<TextView>(R.id.changelogLabel)?.run {
            // Set text to "No update information is available" if needed,
            // and disallow clicking
            text = if (updateData?.isUpdateInformationAvailable == false) {
                isClickable = false
                setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.error, 0, 0, 0
                )
                getString(R.string.update_information_no_update_data_available)
            } else {
                setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.collapse, 0, 0, 0
                )
                setOnClickListener {
                    val visible = changelogField?.isVisible != true

                    // Display a notice above the changelog if the user's currently installed
                    // version doesn't match the version this changelog is meant for
                    differentVersionChangelogNotice?.isVisible = visible && isDifferentVersion
                    // Show the changelog
                    changelogField?.isVisible = visible

                    // Toggle expand/collapse icons
                    setCompoundDrawablesRelativeWithIntrinsicBounds(
                        if (visible) {
                            R.drawable.expand
                        } else {
                            R.drawable.collapse
                        }, 0, 0, 0
                    )
                }
                getString(R.string.update_information_view_update_information)
            }
        }
    }

    private fun propagateTitleAndSubtitleChanges(subtitle: CharSequence?) {
        mainViewModel.saveSubtitleForPage(
            R.id.page_update,
            subtitle
        )

        // Since this is the first/default page, the page change callback isn't fired
        // So we need to manually update toolbar text
        (activity as MainActivity?)?.updateToolbarForPage(R.id.page_update)
    }

    private fun getUpdateChangelog(updateData: UpdateData?) = updateData?.run {
        if (!changelog.isNullOrBlank() && !description.isNullOrBlank()) {
            UpdateDescriptionParser.parse(context, description!!).trim()
        } else {
            getString(R.string.update_information_description_not_available)
        }
    } ?: getString(R.string.update_information_description_not_available)

    private fun showDownloadLink() {
        if (updateData != null) {
            val rootView = binding?.root
            rootView?.findViewById<TextView>(R.id.downloadLinkTextView)?.apply {
                isVisible = true
                movementMethod = LinkMovementMethod.getInstance()
                text = SpannableString(
                    getString(R.string.update_information_download_link, updateData!!.downloadUrl)
                ).apply {
                    setSpan(URLSpan(updateData!!.downloadUrl), indexOf("\n") + 1, length, SpannableString.SPAN_INCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }

    private fun showDownloadErrorForUnsuccessfulResponse() = showDownloadError(
        requireActivity(),
        false,
        getString(R.string.download_error),
        HtmlCompat.fromHtml(
            getString(R.string.download_error_unsuccessful_response),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
    )

    private fun showDownloadError(
        @StringRes message: Int,
        isResumable: Boolean = false,
        callback: KotlinCallback<Boolean>? = null,
    ) = showDownloadError(
        requireActivity(),
        isResumable,
        R.string.download_error,
        message,
        callback
    )

    private fun initDownloadLayout(pair: Pair<DownloadStatus, WorkInfo?>) {
        // If the stub is null, that means it's been inflated, which means views used in this function aren't null
        if (binding?.updateInformationLayoutStub == null || binding?.updateInformationLayoutStub?.parent == null) {
            val workInfo = pair.second

            val rootView = binding?.root
            val downloadIcon = rootView?.findViewById<ImageView>(R.id.downloadIcon)
            val downloadProgressBar = rootView?.findViewById<ProgressBar>(R.id.downloadProgressBar)
            val downloadSizeTextView = rootView?.findViewById<TextView>(R.id.downloadSizeTextView)
            when (pair.first) {
                NOT_DOWNLOADING -> {
                    initDownloadActionButton(true)

                    rootView?.findViewById<TextView>(R.id.downloadUpdateTextView)?.setText(R.string.download)
                    downloadSizeTextView?.text = context?.formatFileSize(
                        updateData?.downloadSize ?: 0
                    )

                    downloadProgressBar?.isVisible = false
                    rootView?.findViewById<View>(R.id.downloadActionButton)?.isVisible = false
                    rootView?.findViewById<View>(R.id.downloadDetailsTextView)?.isVisible = false

                    downloadIcon?.setImageResourceWithTint(R.drawable.download, R.color.colorPrimary)

                    rootView?.findViewById<View>(R.id.downloadLayout)?.apply {
                        val shouldEnable = updateData?.downloadUrl?.contains("http") == true
                        isEnabled = shouldEnable
                        isClickable = shouldEnable

                        if (shouldEnable) {
                            setOnClickListener(downloadNotStartedOnClickListener)
                        }
                    }

                    if (updateData == null) {
                        load()
                    }
                }
                DOWNLOAD_QUEUED,
                DOWNLOADING -> {
                    initDownloadActionButton(true)

                    rootView?.findViewById<TextView>(R.id.downloadUpdateTextView)?.setText(R.string.downloading)

                    val workProgress = workInfo?.progress
                    val bytesDone = workProgress?.getLong(WORK_DATA_DOWNLOAD_BYTES_DONE, -1L) ?: -1L
                    val totalBytes = workProgress?.getLong(WORK_DATA_DOWNLOAD_TOTAL_BYTES, -1L) ?: -1L
                    val currentProgress = workProgress?.getInt(WORK_DATA_DOWNLOAD_PROGRESS, 0) ?: 0
                    val downloadEta = workProgress?.getString(WORK_DATA_DOWNLOAD_ETA)

                    rootView?.findViewById<TextView>(R.id.downloadDetailsTextView)?.apply {
                        isVisible = true
                        text = downloadEta ?: getString(R.string.summary_please_wait)
                    }

                    downloadProgressBar?.apply {
                        isVisible = true

                        if (bytesDone != -1L && totalBytes != -1L) {
                            val previousProgress = progress

                            // only update view in increments of progress to avoid multiple unnecessary view updates or logs
                            if (currentProgress != previousProgress) {
                                isIndeterminate = false
                                progress = currentProgress

                                val bytesDoneStr = context?.formatFileSize(bytesDone)
                                val totalBytesStr = context?.formatFileSize(totalBytes)

                                @SuppressLint("SetTextI18n")
                                downloadSizeTextView?.text = "$bytesDoneStr / $totalBytesStr ($currentProgress%)"
                            }
                        } else {
                            isIndeterminate = true
                        }
                    }

                    downloadIcon?.apply {
                        if (drawable !is AnimationDrawable || !(drawable as AnimationDrawable).isRunning) {
                            setImageResourceWithTint(android.R.drawable.stat_sys_download, R.color.colorPositive)
                            (drawable as AnimationDrawable).start()
                        }
                    }

                    rootView?.findViewById<View>(R.id.downloadLayout)?.apply {
                        isEnabled = true
                        isClickable = false
                        setOnClickListener {
                            downloadIcon?.setImageResourceWithTint(R.drawable.download, R.color.colorPositive)

                            mainViewModel.pauseDownloadWork()

                            // Prevents sending duplicate Intents
                            setOnClickListener { }
                        }
                    }
                }
                DOWNLOAD_PAUSED -> {
                    initDownloadActionButton(true)

                    rootView?.findViewById<TextView>(R.id.downloadUpdateTextView)?.setText(R.string.paused)
                    rootView?.findViewById<TextView>(R.id.downloadDetailsTextView)?.setText(R.string.download_progress_text_paused)

                    // Hide progress bar if it's in an indeterminate state
                    downloadProgressBar?.apply {
                        isVisible = !isIndeterminate
                    }
                    rootView?.findViewById<View>(R.id.downloadDetailsTextView)?.isVisible = true

                    downloadIcon?.setImageResourceWithTint(R.drawable.download, R.color.colorPositive)

                    rootView?.findViewById<View>(R.id.downloadLayout)?.apply {
                        isEnabled = true
                        isClickable = false
                        setOnClickListener {
                            downloadIcon?.apply {
                                setImageResourceWithTint(android.R.drawable.stat_sys_download, R.color.colorPositive)
                                (drawable as AnimationDrawable).start()
                            }

                            enqueueDownloadWork()

                            // No resuming twice allowed
                            setOnClickListener { }
                        }
                    }
                }
                DOWNLOAD_COMPLETED -> {
                    initDownloadActionButton(false)

                    rootView?.findViewById<TextView>(R.id.downloadUpdateTextView)?.setText(R.string.downloaded)
                    downloadSizeTextView?.text = context?.formatFileSize(
                        updateData?.downloadSize ?: 0
                    )

                    downloadProgressBar?.isVisible = false
                    rootView?.findViewById<View>(R.id.downloadDetailsTextView)?.isVisible = false

                    downloadIcon?.setImageResourceWithTint(R.drawable.done_outline, R.color.colorPositive)

                    // `workInfo` is null only while delivering the initial status update
                    // This check prevents starting verification work as soon as the app starts
                    if (workInfo != null) {
                        rootView?.findViewById<View>(R.id.downloadLayout)?.apply {
                            isEnabled = false
                            isClickable = false
                            setOnClickListener { }
                        }
                    } else {
                        rootView?.findViewById<View>(R.id.downloadLayout)?.apply {
                            isEnabled = true
                            isClickable = true
                            setOnClickListener {
                                updateAlreadyDownloadedDialog.show()
                            }
                        }
                    }
                }
                DOWNLOAD_FAILED -> {
                    val outputData = workInfo?.outputData

                    val failureType = outputData?.getString(WORK_DATA_DOWNLOAD_FAILURE_TYPE)

                    if (failureType != null) {
                        mainViewModel.updateDownloadStatus(NOT_DOWNLOADING)

                        when (DownloadFailure[failureType]) {
                            DownloadFailure.SERVER_ERROR -> {
                                showDownloadLink()
                                showDownloadError(
                                    R.string.download_error_server,
                                    true
                                ) {
                                    enqueueDownloadWork()
                                }
                            }
                            DownloadFailure.CONNECTION_ERROR -> {
                                showDownloadLink()
                                showDownloadError(
                                    R.string.download_error_internal,
                                    true
                                ) {
                                    enqueueDownloadWork()
                                }
                            }
                            DownloadFailure.UNSUCCESSFUL_RESPONSE -> {
                                showDownloadLink()
                                showDownloadErrorForUnsuccessfulResponse()
                                mainViewModel.logDownloadError(outputData)
                            }
                            DownloadFailure.NULL_UPDATE_DATA_OR_DOWNLOAD_URL,
                            DownloadFailure.DOWNLOAD_URL_INVALID_SCHEME,
                            DownloadFailure.UNKNOWN,
                            -> {
                                showDownloadLink()
                                showDownloadError(R.string.download_error_internal)
                            }
                            DownloadFailure.COULD_NOT_MOVE_TEMP_FILE -> showDownloadError(
                                requireActivity(),
                                false,
                                getString(R.string.download_error),
                                getString(
                                    R.string.download_error_could_not_move_temp_file,
                                    "Android/data/${requireContext().packageName}/files"
                                )
                            )
                            else -> {}
                        }
                    }
                }
                VERIFYING -> {
                    rootView?.findViewById<TextView>(R.id.downloadUpdateTextView)?.setText(R.string.download_verifying)
                    rootView?.findViewById<TextView>(R.id.downloadDetailsTextView)?.setText(R.string.download_progress_text_verifying)

                    downloadProgressBar?.isVisible = true
                    rootView?.findViewById<View>(R.id.downloadActionButton)?.isVisible = false
                    rootView?.findViewById<View>(R.id.downloadDetailsTextView)?.isVisible = true

                    downloadProgressBar?.isIndeterminate = true
                    rootView?.findViewById<View>(R.id.downloadLayout)?.apply {
                        isEnabled = true
                        isClickable = false
                    }
                }
                VERIFICATION_COMPLETED -> {
                    initDownloadActionButton(false)

                    rootView?.findViewById<TextView>(R.id.downloadUpdateTextView)?.setText(R.string.downloaded)
                    downloadSizeTextView?.text = context?.formatFileSize(
                        updateData?.downloadSize ?: 0
                    )

                    downloadProgressBar?.isVisible = false
                    rootView?.findViewById<View>(R.id.downloadDetailsTextView)?.isVisible = false

                    downloadIcon?.setImageResourceWithTint(R.drawable.done_outline, R.color.colorPositive)

                    rootView?.findViewById<View>(R.id.downloadLayout)?.apply {
                        isEnabled = true
                        isClickable = true
                        setOnClickListener {
                            updateAlreadyDownloadedDialog.show()
                        }
                    }

                    // Since file has been verified, we can prune the work and launch [InstallActivity]
                    mainViewModel.maybePruneWork()
                    Toast.makeText(context, getString(R.string.download_complete), LENGTH_LONG).show()
                    activity?.startInstallActivity(
                        true,
                        rootView?.findViewById(R.id.downloadActionButton)!!
                    )
                }
                VERIFICATION_FAILED -> {
                    initDownloadActionButton(false)

                    rootView?.findViewById<TextView>(R.id.downloadUpdateTextView)?.setText(R.string.download_verifying_error)

                    downloadProgressBar?.isVisible = false
                    rootView?.findViewById<View>(R.id.downloadDetailsTextView)?.isVisible = true

                    rootView?.findViewById<TextView>(R.id.downloadDetailsTextView)?.setText(R.string.download_notification_error_corrupt)

                    downloadIcon?.setImageResourceWithTint(R.drawable.download, R.color.colorPositive)

                    rootView?.findViewById<View>(R.id.downloadLayout)?.apply {
                        isEnabled = true
                        isClickable = true

                        setOnClickListener {
                            downloadIcon?.apply {
                                setImageResourceWithTint(android.R.drawable.stat_sys_download, R.color.colorPositive)
                                (drawable as AnimationDrawable).start()
                            }

                            enqueueDownloadWork()

                            // No resuming twice allowed
                            setOnClickListener { }
                        }
                    }

                    showDownloadLink()
                    showDownloadError(
                        R.string.download_error_corrupt,
                        false
                    ) {
                        enqueueDownloadWork()
                    }
                    // Delete downloaded file, so the user can restart the download
                    mainViewModel.deleteDownloadedFile(
                        requireContext(),
                        updateData,
                        true
                    )
                }
            }
        }
    }

    private fun initDownloadActionButton(isCancelAction: Boolean) {
        val rootView = binding?.root
        val drawableResId: Int
        val colorResId: Int

        val onClickListener = if (isCancelAction) {
            drawableResId = R.drawable.close
            colorResId = R.color.colorError

            View.OnClickListener {
                mainViewModel.cancelDownloadWork(requireContext(), updateData)

                Handler(Looper.getMainLooper()).postDelayed(
                    { mainViewModel.updateDownloadStatus(NOT_DOWNLOADING) },
                    250
                )
            }
        } else {
            drawableResId = R.drawable.install
            colorResId = R.color.colorPositive

            View.OnClickListener {
                activity?.startInstallActivity(
                    true,
                    rootView?.findViewById(R.id.downloadActionButton)!!
                )
            }
        }

        rootView?.findViewById<ImageView>(R.id.downloadActionButton)?.apply {
            isVisible = true
            setImageResourceWithTint(drawableResId, colorResId)
            setOnClickListener(onClickListener)
        }
    }

    companion object {
        const val TAG = "UpdateInformationFragment"

        private const val FULL_ZIP_LIKELY_MIN_SIZE = 1048576 * 2000L // 2GB

        /**
         * Amount of free storage space to reserve when downloading an update.
         * Currently: `25 MB`
         */
        const val SAFE_MARGIN = 1048576 * 25L

        // In app message bar collections and identifiers.
        const val KEY_HAS_DOWNLOAD_ERROR = "has_download_error"
        const val KEY_DOWNLOAD_ERROR_TITLE = "download_error_title"
        const val KEY_DOWNLOAD_ERROR_MESSAGE = "download_error_message"
        const val KEY_DOWNLOAD_ERROR_RESUMABLE = "download_error_resumable"
    }
}
