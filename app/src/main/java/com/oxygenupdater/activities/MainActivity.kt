package com.oxygenupdater.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.IntIntPair
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.os.postDelayed
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.NavType.Companion.BoolType
import androidx.navigation.NavType.Companion.LongType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentDebugSettings.DebugGeography
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.oxygenupdater.BuildConfig
import com.oxygenupdater.OxygenUpdater
import com.oxygenupdater.R
import com.oxygenupdater.enums.PurchaseType
import com.oxygenupdater.extensions.openPlayStorePage
import com.oxygenupdater.extensions.showToast
import com.oxygenupdater.icons.CustomIcons
import com.oxygenupdater.icons.Image
import com.oxygenupdater.icons.LogoNotification
import com.oxygenupdater.internal.NotSet
import com.oxygenupdater.internal.NotSetL
import com.oxygenupdater.internal.settings.KeyAdvancedMode
import com.oxygenupdater.internal.settings.KeyContribute
import com.oxygenupdater.internal.settings.KeyIgnoreIncorrectDeviceWarnings
import com.oxygenupdater.internal.settings.KeyIgnoreNotificationPermissionSheet
import com.oxygenupdater.internal.settings.KeyIgnoreUnsupportedDeviceWarnings
import com.oxygenupdater.internal.settings.KeySetupDone
import com.oxygenupdater.models.Article
import com.oxygenupdater.models.Device
import com.oxygenupdater.models.DeviceOsSpec
import com.oxygenupdater.models.SystemVersionProperties
import com.oxygenupdater.ui.CollapsingAppBar
import com.oxygenupdater.ui.RefreshAwareState
import com.oxygenupdater.ui.TopAppBar
import com.oxygenupdater.ui.about.AboutScreen
import com.oxygenupdater.ui.common.BannerAd
import com.oxygenupdater.ui.common.adLoadListener
import com.oxygenupdater.ui.common.buildAdRequest
import com.oxygenupdater.ui.common.loadBannerAd
import com.oxygenupdater.ui.common.rememberSaveableState
import com.oxygenupdater.ui.device.DefaultDeviceName
import com.oxygenupdater.ui.device.DeviceScreen
import com.oxygenupdater.ui.device.IncorrectDeviceDialog
import com.oxygenupdater.ui.device.UnsupportedDeviceOsSpecDialog
import com.oxygenupdater.ui.faq.FaqScreen
import com.oxygenupdater.ui.faq.FaqViewModel
import com.oxygenupdater.ui.install.InstallGuideScreen
import com.oxygenupdater.ui.install.InstallGuideViewModel
import com.oxygenupdater.ui.main.AboutRoute
import com.oxygenupdater.ui.main.AppUpdateInfo
import com.oxygenupdater.ui.main.ArticleRoute
import com.oxygenupdater.ui.main.ChildScreen
import com.oxygenupdater.ui.main.DeviceRoute
import com.oxygenupdater.ui.main.DownloadedArg
import com.oxygenupdater.ui.main.ExternalArg
import com.oxygenupdater.ui.main.FaqRoute
import com.oxygenupdater.ui.main.FlexibleAppUpdateProgress
import com.oxygenupdater.ui.main.GuideRoute
import com.oxygenupdater.ui.main.IdArg
import com.oxygenupdater.ui.main.MainMenu
import com.oxygenupdater.ui.main.MainNavigationBar
import com.oxygenupdater.ui.main.MainNavigationRail
import com.oxygenupdater.ui.main.MainScreens
import com.oxygenupdater.ui.main.MainSnackbar
import com.oxygenupdater.ui.main.NavType
import com.oxygenupdater.ui.main.NewsListRoute
import com.oxygenupdater.ui.main.NoConnectionSnackbarData
import com.oxygenupdater.ui.main.NotificationPermission
import com.oxygenupdater.ui.main.OuScheme
import com.oxygenupdater.ui.main.Screen
import com.oxygenupdater.ui.main.ServerStatusBanner
import com.oxygenupdater.ui.main.ServerStatusDialogs
import com.oxygenupdater.ui.main.SettingsRoute
import com.oxygenupdater.ui.main.UpdateRoute
import com.oxygenupdater.ui.news.ArticleScreen
import com.oxygenupdater.ui.news.ArticleViewModel
import com.oxygenupdater.ui.news.NewsListScreen
import com.oxygenupdater.ui.news.NewsListViewModel
import com.oxygenupdater.ui.onboarding.OnboardingScreen
import com.oxygenupdater.ui.settings.SettingsScreen
import com.oxygenupdater.ui.settings.SettingsViewModel
import com.oxygenupdater.ui.settings.adFreeConfig
import com.oxygenupdater.ui.theme.AppTheme
import com.oxygenupdater.ui.theme.backgroundVariant
import com.oxygenupdater.ui.theme.light
import com.oxygenupdater.ui.update.KeyDownloadErrorMessage
import com.oxygenupdater.ui.update.UpdateInformationViewModel
import com.oxygenupdater.ui.update.UpdateScreen
import com.oxygenupdater.ui.update.WorkProgress
import com.oxygenupdater.utils.ContributorUtils
import com.oxygenupdater.utils.LocalNotifications
import com.oxygenupdater.utils.Utils.checkPlayServices
import com.oxygenupdater.utils.hasRootAccess
import com.oxygenupdater.utils.logBillingError
import com.oxygenupdater.utils.logDebug
import com.oxygenupdater.utils.logError
import com.oxygenupdater.utils.logUmpConsentFormError
import com.oxygenupdater.utils.logWarning
import com.oxygenupdater.viewmodels.BillingViewModel
import com.oxygenupdater.viewmodels.MainViewModel
import com.oxygenupdater.workers.WorkDataDownloadBytesDone
import com.oxygenupdater.workers.WorkDataDownloadEta
import com.oxygenupdater.workers.WorkDataDownloadFailureType
import com.oxygenupdater.workers.WorkDataDownloadProgress
import com.oxygenupdater.workers.WorkDataDownloadTotalBytes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale
import java.util.Timer
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.concurrent.schedule

/**
 * Single activity.
 *
 * We're using [AppCompatActivity] instead of [androidx.activity.ComponentActivity] because of
 * [automatic per-app language](https://developer.android.com/guide/topics/resources/app-languages#androidx-impl)
 *
 * @author [Adhiraj Singh Chauhan](https://github.com/adhirajsinghchauhan)
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val updateViewModel: UpdateInformationViewModel by viewModels()
    private val newsListViewModel: NewsListViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val billingViewModel: BillingViewModel by viewModels()

    private val validatePurchaseTimer = Timer()

    @Volatile
    private var bannerAdView: AdView? = null

    @Volatile
    private var interstitialAd: InterstitialAd? = null

    @Volatile
    private var currentRoute: String? = null

    private val fullScreenAdContentCallback = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
            interstitialAd = null
            logDebug(TAG, "Interstitial ad was dismissed")
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            interstitialAd = null
            crashlytics.logWarning(TAG, "Interstitial ad failed to show: $error")
        }

        override fun onAdShowedFullScreenContent() = logDebug(TAG, "Interstitial ad was shown")
    }

    private var interstitialAdLoadType = InterstitialAdLoadType.LoadOnly
    private val interstitialAdLoadCallback = object : InterstitialAdLoadCallback() {
        override fun onAdFailedToLoad(error: LoadAdError) {
            interstitialAd = null
            crashlytics.logWarning(TAG, "Interstitial ad failed to load: $error")
        }

        override fun onAdLoaded(ad: InterstitialAd) {
            val ms = System.currentTimeMillis() - interstitialLoadCallMs
            logDebug(TAG, "Interstitial ad loaded in ${ms}ms")

            ad.fullScreenContentCallback = fullScreenAdContentCallback
            interstitialAd = ad

            when (interstitialAdLoadType) {
                InterstitialAdLoadType.LoadAndShowImmediately -> {
                    logDebug(TAG, "Showing interstitial ad now")
                    ad.show(this@MainActivity)
                }

                // Note: AdMob won't show the interstitial if app is not
                // in foreground, so we don't need to handle it ourselves.
                InterstitialAdLoadType.LoadAndShowDelayed -> {
                    logDebug(TAG, "Showing interstitial ad in 5s")

                    // Show immediately if roughly 5s have already passed
                    // between requesting an ad load, and the ad being loaded.
                    // Also, show only if we're still in the article screen.
                    if (ms >= 4900L && currentRoute?.startsWith(ChildScreen.Article.value) == true) {
                        logDebug(TAG, "Showing interstitial ad now")
                        ad.show(this@MainActivity)
                    }
                    // If 5s have not yet passed, schedule (main thread) to show
                    // it in however much time is left for the 5s mark.
                    // We don't need to add debounce logic here, because AdMob
                    // frequency capping ensures that `onAdLoaded` is entered only
                    // once within the 5m window (and our delay is 5s).
                    else {
                        val diffMs = 5000L - ms
                        logDebug(TAG, "Showing interstitial ad in ${diffMs}ms")
                        Handler(Looper.getMainLooper()).postDelayed(diffMs) {
                            // Show only if we're still in the article screen.
                            // Note: AdMob won't show the interstitial if app is not
                            // in foreground, so we don't need to handle it ourselves.
                            if (currentRoute?.startsWith(ChildScreen.Article.value) == true) {
                                interstitialAd?.show(this@MainActivity)
                            }
                        }
                    }
                }
            }
        }
    }

    @Inject
    lateinit var contributorUtils: ContributorUtils

    @Inject
    lateinit var crashlytics: FirebaseCrashlytics

    private var downloadErrorMessage: String? = null

    private lateinit var consentInformation: ConsentInformation

    private lateinit var navController: NavHostController
    private lateinit var defaultNavOptions: NavOptions

    /**
     * Passthrough from [MainViewModel] to avoid sending a request again, but only after server request for
     * all devices completes ([MainViewModel] sends it on init, which would be `null` initially).
     *
     * This additional check is required only during onboarding to avoid unnecessary requests being sent.
     * We don't want to send a server request for enabled devices simultaneously, because that can be derived
     * from all devices anyway. Additionally, unnecessary changes in device lists will cause a repeated request
     * for update methods to also be sent.
     */
    @Suppress("NOTHING_TO_INLINE")
    @Composable
    private inline fun PassthroughEnabledDevicesToSettingsViewModel(enabledDevices: List<Device>?) {
        if (enabledDevices == null) return
        LaunchedEffect(enabledDevices) {
            settingsViewModel.fetchEnabledDevices(enabledDevices)
        }
    }

    @Composable
    @ReadOnlyComposable
    private fun EdgeToEdge() {
        val light = MaterialTheme.colorScheme.light
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { !light },
            navigationBarStyle = navigationBarStyle,
        )

        // Below Android 6/Marshmallow, the above function adds window flags to
        // draw system bars as translucent for better legibility, because status
        // bar content & nav bar icons are always white on these old Android
        // versions. However, if a dark theme is active, the background is
        // guaranteed to be a dark enough colour, and so it's better if we clear
        // these flags for the same "proper" edge-to-edge as on newer versions.
        @Suppress("DEPRECATION")
        if (SDK_INT < VERSION_CODES.M && !light) window.run {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun OnboardingContent(windowSize: WindowSizeClass) = Column {
        val allDevices by viewModel.allDevicesState.collectAsStateWithLifecycle()
        val cachedEnabledDevices = remember(allDevices) { allDevices?.filter { it.enabled } }
        PassthroughEnabledDevicesToSettingsViewModel(cachedEnabledDevices)

        val deviceName = settingsViewModel.deviceName ?: remember(cachedEnabledDevices) {
            cachedEnabledDevices?.find {
                it.productNames.contains(SystemVersionProperties.oxygenDeviceName)
            }?.name
        }

        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        // Use normal app bar if height isn't enough (e.g. landscape phones)
        if (windowSize.heightSizeClass == WindowHeightSizeClass.Compact) TopAppBar(
            scrollBehavior = scrollBehavior,
            subtitleResId = R.string.onboarding,
            root = true,
        ) else CollapsingAppBar(
            scrollBehavior = scrollBehavior,
            image = { modifier ->
                val context = LocalContext.current
                AsyncImage(
                    model = deviceName?.let {
                        val density = LocalDensity.current
                        remember(it, maxWidth) {
                            ImageRequest.Builder(context)
                                .data(Device.constructImageUrl(it))
                                .size(density.run { Size(maxWidth.roundToPx(), 256.dp.roundToPx()) })
                                .build()
                        }
                    },
                    contentDescription = stringResource(R.string.device_information_image_description),
                    placeholder = rememberVectorPainter(CustomIcons.Image),
                    error = rememberVectorPainter(CustomIcons.LogoNotification),
                    contentScale = ContentScale.Crop,
                    colorFilter = if (deviceName == null) ColorFilter.tint(MaterialTheme.colorScheme.primary) else null,
                    modifier = modifier
                )
            },
            title = stringResource(R.string.app_name),
            subtitle = stringResource(R.string.onboarding),
        )

        val deviceConfig by settingsViewModel.deviceConfigState.collectAsStateWithLifecycle()
        val methodConfig by settingsViewModel.methodConfigState.collectAsStateWithLifecycle()
        OnboardingScreen(
            windowWidthSize = windowSize.widthSizeClass,
            scrollBehavior = scrollBehavior,
            deviceConfig = deviceConfig,
            onDeviceSelect = settingsViewModel::saveSelectedDevice,
            methodConfig = methodConfig,
            onMethodSelect = settingsViewModel::saveSelectedMethod,
            getPrefStr = viewModel::getPref,
            getPrefBool = viewModel::getPref,
            persistBool = viewModel::persist,
            onStartAppClick = { contribute ->
                settingsViewModel.resubscribeToFcmTopic()

                if (viewModel.isDeviceAndMethodSet) {
                    viewModel.persist(KeySetupDone, true)
                    (application as? OxygenUpdater)?.setupCrashReporting()

                    // If user enables OTA contribution, check if device is rooted and ask for root permission
                    if (ContributorUtils.isAtLeastQAndPossiblyRooted && contribute) {
                        showToast(R.string.contribute_allow_storage)
                        hasRootAccess {
                            viewModel.persist(KeyContribute, true)
                            viewModel.shouldShowOnboarding = false
                        }
                    } else {
                        // Skip shell creation and thus don't show root permission prompt
                        viewModel.persist(KeyContribute, false)
                        viewModel.shouldShowOnboarding = false
                    }
                } else {
                    crashlytics.logWarning(TAG, "Required preferences not valid: ${viewModel.deviceId}, ${viewModel.updateMethodId}")
                    showToast(R.string.settings_entered_incorrectly)
                    viewModel.shouldShowOnboarding = true
                }
            },
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Content(windowSize: WindowSizeClass, startScreen: Screen) {
        LaunchedEffect(Unit) { // run only on init
            viewModel.fetchServerMessages()
        }

        val showDeviceBadge = viewModel.deviceOsSpec.let {
            it != null && it != DeviceOsSpec.SupportedOxygenOs
        } || viewModel.deviceMismatch.let { it != null && it.first }
        Screen.Device.badge = if (showDeviceBadge) "!" else null

        val allDevices by viewModel.allDevicesState.collectAsStateWithLifecycle()
        val enabledDevices = remember(allDevices) { allDevices?.filter { it.enabled } }

        viewModel.deviceOsSpec?.let {
            /**
             * We're maintaining `show` outside of [UnsupportedDeviceOsSpecDialog]
             * to allow re-opening after hiding it the first time.
             */
            var show by remember { mutableStateOf(viewModel.shouldShowUnsupportedDeviceDialog) }
            UnsupportedDeviceOsSpecDialog(
                show = show,
                hide = { ignore ->
                    viewModel.persist(KeyIgnoreUnsupportedDeviceWarnings, ignore)
                    show = false
                },
                spec = it,
            )
        }

        if (viewModel.shouldShowIncorrectDeviceDialog) viewModel.deviceMismatch?.let { mismatchStatus ->
            IncorrectDeviceDialog(
                hide = { viewModel.persist(KeyIgnoreIncorrectDeviceWarnings, it) },
                mismatchStatus = mismatchStatus,
            )
        }

        var snackbarText by remember {
            // Referential equality because we're reusing static Pairs
            mutableStateOf<IntIntPair?>(null, referentialEqualityPolicy())
        }

        val appUpdateInfo by viewModel.appUpdateInfo.collectAsStateWithLifecycle()
        appUpdateInfo?.let { it ->
            AppUpdateInfo(
                status = it.installStatus(),
                availability = it::updateAvailability,
                snackbarMessageId = { snackbarText?.first },
                updateSnackbarText = { snackbarText = it },
                resetAppUpdateIgnoreCount = viewModel::resetAppUpdateIgnoreCount,
                incrementAppUpdateIgnoreCount = viewModel::incrementAppUpdateIgnoreCount,
                unregisterAppUpdateListener = viewModel::unregisterAppUpdateListener,
                requestUpdate = viewModel::requestUpdate,
                requestImmediateUpdate = viewModel::requestImmediateAppUpdate,
            )
        }

        // Display the "No connection" banner if required
        val isNetworkAvailable by OxygenUpdater.isNetworkAvailable.collectAsStateWithLifecycle()
        if (!isNetworkAvailable) snackbarText = NoConnectionSnackbarData
        else if (snackbarText?.first == NoConnectionSnackbarData.first) {
            // Dismiss only this snackbar
            snackbarText = null
        }

        Row {
            var subtitleResId by rememberSaveableState("subtitleResId", if (startScreen.useVersionName) 0 else startScreen.labelResId)
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            currentRoute = navBackStackEntry?.destination?.route

            val windowWidthSize = windowSize.widthSizeClass
            val navType = NavType.from(windowWidthSize)

            /** Check if the current screen is ["main"][MainScreens], i.e. not a [ChildScreen] */
            val isMainScreen = currentRoute?.let { !ChildScreen.check(it) } ?: true
            val isNavTypeBottomBar = navType == NavType.BottomBar

            val onNavIconClick = {
                if (isMainScreen) navController.navigateWithDefaults(AboutRoute)
                else navController.popBackStack().let {}
            }

            AnimatedVisibility(!isNavTypeBottomBar) {
                MainNavigationRail(
                    currentRoute = currentRoute,
                    onNavIconClick = onNavIconClick,
                    root = isMainScreen,
                    navigateTo = { navController.navigateWithDefaults(it) },
                    setSubtitleResId = { subtitleResId = it },
                )
            }

            AnimatedVisibility(!isNavTypeBottomBar) {
                VerticalDivider(color = MaterialTheme.colorScheme.backgroundVariant)
            }

            Column {
                val isArticleScreen = currentRoute?.startsWith(ChildScreen.Article.value) == true
                val scrollBehavior = if (isArticleScreen) {
                    TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                } else TopAppBarDefaults.enterAlwaysScrollBehavior()

                AnimatedContent(
                    targetState = isArticleScreen,
                    label = "ArticleCollapsingAppBarAnimatedContent",
                ) { showCollapsingAppBar ->
                    if (showCollapsingAppBar) CollapsingAppBar(
                        scrollBehavior = scrollBehavior,
                        image = { modifier ->
                            val context = LocalContext.current
                            val imageUrl = ArticleViewModel.item?.imageUrl
                            AsyncImage(
                                model = imageUrl?.let {
                                    val density = LocalDensity.current
                                    remember(it, maxWidth) {
                                        ImageRequest.Builder(context)
                                            .data(it)
                                            .size(density.run { Size(maxWidth.roundToPx(), 256.dp.roundToPx()) })
                                            .build()
                                    }
                                },
                                contentDescription = stringResource(R.string.news),
                                placeholder = rememberVectorPainter(CustomIcons.Image),
                                error = rememberVectorPainter(CustomIcons.LogoNotification),
                                contentScale = ContentScale.Crop,
                                colorFilter = if (imageUrl == null) {
                                    ColorFilter.tint(MaterialTheme.colorScheme.primary)
                                } else null,
                                modifier = modifier
                            )
                        },
                        title = ArticleViewModel.item?.title ?: stringResource(R.string.loading),
                        subtitle = ArticleViewModel.item?.authorName ?: stringResource(R.string.summary_please_wait),
                        // Don't show nav icon if SideRail is shown
                        onNavIconClick = if (isNavTypeBottomBar) onNavIconClick else null,
                    ) else TopAppBar(
                        scrollBehavior = scrollBehavior,
                        // Don't show nav icon if SideRail is shown
                        onNavIconClick = if (isNavTypeBottomBar) onNavIconClick else null,
                        subtitleResId = subtitleResId,
                        root = isMainScreen,
                    ) {
                        val serverMessages by viewModel.serverMessages.collectAsStateWithLifecycle()
                        MainMenu(
                            serverMessages = serverMessages,
                            showMarkAllRead = subtitleResId == Screen.NewsList.labelResId,
                            onMarkAllReadClick = newsListViewModel::markAllRead,
                            onContributorEnrollmentChange = {
                                contributorUtils.flushSettings(this@MainActivity, it)
                            },
                        )
                    }
                }

                val appUpdateStatus by viewModel.appUpdateStatus.collectAsStateWithLifecycle()
                appUpdateStatus?.let { it ->
                    FlexibleAppUpdateProgress(
                        status = it.installStatus(),
                        bytesDownloaded = it::bytesDownloaded,
                        totalBytesToDownload = it::totalBytesToDownload,
                        snackbarMessageId = { snackbarText?.first },
                        updateSnackbarText = { snackbarText = it },
                    )
                }

                val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
                ServerStatusDialogs(serverStatus.status, ::openPlayStorePage)
                ServerStatusBanner(serverStatus, ::openPlayStorePage)

                // NavHost can't preload other composables, so in order to get NewsList's unread count early,
                // we're using the initial state here itself. State is refreshed only once the user visits that
                // screen, so it's easy on the server too (no unnecessarily eager requests).
                // Note: can't use `by` here because it doesn't propagate to [newsListScreen]
                val newsListState = newsListViewModel.state.collectAsStateWithLifecycle().value
                LaunchedEffect(Unit) { // run only on init
                    val unreadCount = newsListViewModel.unreadCount.intValue
                    Screen.NewsList.badge = if (unreadCount == 0) null else "$unreadCount"
                }

                val showAds by billingViewModel.shouldShowAds.collectAsStateWithLifecycle()
                NavHost(
                    navController = navController,
                    startDestination = startScreen.route,
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                ) {
                    updateScreen(
                        navType = navType,
                        windowWidthSize = windowWidthSize,
                        setSubtitleResId = { subtitleResId = it },
                        openInstallGuide = {
                            navController.navigateWithDefaults(
                                route = ChildScreen.Guide.value + "$DownloadedArg=true",
                                isChildRoute = true,
                            )
                        }
                    )

                    newsListScreen(
                        navType = navType,
                        windowSize = windowSize,
                        state = newsListState,
                        showAds = { showAds },
                    )

                    deviceScreen(
                        navType = navType,
                        windowWidthSize = windowWidthSize,
                        allDevices = allDevices,
                    )

                    aboutScreen(
                        navType = navType,
                        windowWidthSize = windowWidthSize,
                    ) {
                        when (it) {
                            ChildScreen.Guide -> navController.navigateWithDefaults(
                                route = it.value + "$DownloadedArg=false",
                                isChildRoute = true,
                            )

                            ChildScreen.Faq -> navController.navigateWithDefaults(
                                route = it.value,
                                isChildRoute = true,
                            )
                        }
                    }

                    settingsScreen(
                        navType = navType,
                        cachedEnabledDevices = enabledDevices,
                        updateMismatchStatus = viewModel::updateDeviceMismatch,
                        openAboutScreen = { navController.navigateWithDefaults(AboutRoute) },
                    )

                    articleScreen(showAds = { showAds }, scrollBehavior = scrollBehavior)
                    installGuideScreen(setSubtitleResId = { subtitleResId = it })
                    faqScreen(setSubtitleResId = { subtitleResId = it })
                }

                // This must be defined on the same level as NavHost, otherwise it won't work
                BackHandler {
                    with(navController) {
                        if (shouldStopNavigateAwayFromSettings) showSettingsWarning()
                        else if (!popBackStack()) finishAffinity() // nothing to back to => exit
                    }
                }

                val showNavBar = isNavTypeBottomBar && isMainScreen
                if (showAds) {
                    var adLoaded by rememberSaveableState("adLoaded", false)
                    BannerAd(
                        adUnitId = BuildConfig.AD_BANNER_MAIN_ID,
                        adListener = adLoadListener { adLoaded = it },
                        onViewUpdate = ::onBannerAdInit,
                        // We draw the activity edge-to-edge, so nav bar padding should be applied only if ad loaded
                        modifier = if (!showNavBar && adLoaded) Modifier.navigationBarsPadding() else Modifier
                    )
                }

                AnimatedVisibility(showNavBar) {
                    MainNavigationBar(
                        currentRoute = currentRoute,
                        navigateTo = { navController.navigateWithDefaults(it) },
                        setSubtitleResId = { subtitleResId = it },
                    )
                }
            }
        }

        // Gets placed over TopAppBar
        MainSnackbar(
            snackbarText = snackbarText,
            openPlayStorePage = ::openPlayStorePage,
            completeAppUpdate = viewModel::completeAppUpdate,
        )
    }

    private fun NavController.navigateWithDefaults(
        route: String,
        isChildRoute: Boolean = false,
    ) = if (shouldStopNavigateAwayFromSettings) showSettingsWarning() else navigate(
        route = route,
        navOptions = if (isChildRoute) buildNavOptions(null) else defaultNavOptions,
    )

    private fun NavGraphBuilder.updateScreen(
        navType: NavType,
        windowWidthSize: WindowWidthSizeClass,
        setSubtitleResId: (Int) -> Unit,
        openInstallGuide: () -> Unit,
    ) = composable(UpdateRoute) {
        DisposableEffect(Unit) {
            // Run this every time this screen is visited
            settingsViewModel.updateCrashlyticsUserId()
            updateViewModel.refreshIfNeeded()

            // Prune finished work when leaving this composable, e.g. when
            // switching to another screen or opening a new activity
            onDispose(viewModel::maybePruneWork)
        }

        val state = updateViewModel.state.collectAsStateWithLifecycle(null).value ?: return@composable
        val (workInfo, downloadStatus) = viewModel.workInfoWithStatus.collectAsStateWithLifecycle().value

        val outputData = workInfo?.outputData
        val progress = workInfo?.progress
        val failureType = outputData?.getInt(WorkDataDownloadFailureType, NotSet)
        UpdateScreen(
            navType = navType,
            windowWidthSize = windowWidthSize,
            state = state,
            onRefresh = {
                settingsViewModel.updateCrashlyticsUserId()
                viewModel.fetchServerStatus()
                updateViewModel.refresh()
            },
            _downloadStatus = downloadStatus,
            failureType = failureType,
            workProgress = if (progress == null) null else remember(progress) {
                WorkProgress(
                    bytesDone = progress.getLong(WorkDataDownloadBytesDone, NotSetL),
                    totalBytes = progress.getLong(WorkDataDownloadTotalBytes, NotSetL),
                    currentProgress = progress.getInt(WorkDataDownloadProgress, NotSet),
                    downloadEta = progress.getString(WorkDataDownloadEta),
                )
            },
            forceDownloadErrorDialog = downloadErrorMessage != null,
            getPrefStr = viewModel::getPref,
            getPrefBool = viewModel::getPref,
            setSubtitleResId = setSubtitleResId,
            enqueueDownload = viewModel::enqueueDownloadWork,
            pauseDownload = viewModel::pauseDownloadWork,
            cancelDownload = { viewModel.cancelDownloadWork(this@MainActivity, it) },
            deleteDownload = { viewModel.deleteDownloadedFile(this@MainActivity, it) },
            openInstallGuide = openInstallGuide,
            logDownloadError = { if (outputData != null) viewModel.logDownloadError(outputData) },
            hideDownloadCompleteNotification = {
                LocalNotifications.hideDownloadCompleteNotification(this@MainActivity)
            },
            showDownloadFailedNotification = {
                LocalNotifications.showDownloadFailedNotification(
                    context = this@MainActivity,
                    resumable = false,
                    message = R.string.download_error_storage,
                    notificationMessage = R.string.download_notification_error_storage_full,
                )
            },
        )
    }

    private fun NavGraphBuilder.newsListScreen(
        navType: NavType,
        windowSize: WindowSizeClass,
        state: RefreshAwareState<List<Article>>,
        showAds: () -> Boolean,
    ) = composable(
        route = NewsListRoute,
        deepLinks = listOf(
            navDeepLink { uriPattern = OuScheme + NewsListRoute },
        ),
    ) {
        LaunchedEffect(Unit) {
            // Avoid refreshing every time this screen is visited by guessing
            // if it's the first load (`refreshing` is true only initially)
            if (state.refreshing) newsListViewModel.refresh()
            if (showAds()) {
                // Eagerly load an interstitial ad to eventually show when clicking on an item
                interstitialAdLoadType = InterstitialAdLoadType.LoadOnly
                loadInterstitialAd()
            }
        }

        NewsListScreen(
            navType = navType,
            windowWidthSize = windowSize.widthSizeClass,
            windowHeightSize = windowSize.heightSizeClass,
            state = state,
            onRefresh = {
                viewModel.fetchServerStatus()
                newsListViewModel.refresh()
            },
            unreadCountState = newsListViewModel.unreadCount,
            onMarkAllReadClick = newsListViewModel::markAllRead,
            onToggleReadClick = newsListViewModel::toggleRead,
            openItem = {
                // Show interstitial ad at a natural transition point. Frequency
                // is capped to once in a 5m window in the AdMob dashboard.
                if (showAds()) interstitialAd.let { ad ->
                    if (ad != null) ad.show(this@MainActivity) else {
                        interstitialAdLoadType = InterstitialAdLoadType.LoadAndShowImmediately
                        loadInterstitialAd()
                    }
                }

                // TODO(compose/news): handle shared element transition, see `movableContentOf` and `LookaheadScope`
                navController.navigateWithDefaults(
                    route = ChildScreen.Article.value + it,
                    isChildRoute = true,
                )
            },
        )
    }

    private fun NavGraphBuilder.deviceScreen(
        navType: NavType,
        windowWidthSize: WindowWidthSizeClass,
        allDevices: List<Device>?,
    ) = composable(DeviceRoute) {
        DeviceScreen(
            navType = navType,
            windowWidthSize = windowWidthSize,
            deviceName = remember(allDevices) {
                allDevices?.find {
                    it.productNames.contains(SystemVersionProperties.oxygenDeviceName)
                }?.name ?: DefaultDeviceName
            },
            deviceOsSpec = viewModel.deviceOsSpec,
            deviceMismatchStatus = viewModel.deviceMismatch,
        )
    }

    private fun NavGraphBuilder.aboutScreen(
        navType: NavType,
        windowWidthSize: WindowWidthSizeClass,
        showChildScreen: (ChildScreen) -> Unit,
    ) = composable(AboutRoute) {
        AboutScreen(
            navType = navType,
            windowWidthSize = windowWidthSize,
            navigateTo = showChildScreen,
            openEmail = { viewModel.openEmail(this@MainActivity) }
        )
    }

    private fun NavGraphBuilder.settingsScreen(
        navType: NavType,
        cachedEnabledDevices: List<Device>?,
        updateMismatchStatus: () -> Unit,
        openAboutScreen: () -> Unit,
    ) = composable(SettingsRoute) {
        PassthroughEnabledDevicesToSettingsViewModel(cachedEnabledDevices)

        val adFreePrice by billingViewModel.adFreePrice.collectAsStateWithLifecycle(null)
        val adFreeState by billingViewModel.adFreeState.collectAsStateWithLifecycle(null)

        val adFreeConfig = remember(adFreeState) {
            adFreeConfig(
                state = adFreeState,
                logBillingError = {
                    crashlytics.logBillingError("AdFreeConfig", "SKU '${PurchaseType.AD_FREE.sku}' is not available")
                },
                makePurchase = { billingViewModel.makePurchase(this@MainActivity, it) },
                markPending = { showToast(R.string.purchase_error_pending_payment) },
            )
        }

        // Note: we use `this` instead of LocalLifecycleOwner because the latter can change, which results
        // in an IllegalArgumentException (can't reuse the same observer with different lifecycles)
        billingViewModel.newPurchase.observe(this@MainActivity, remember {
            Observer<Pair<Int, Purchase?>> {
                val (responseCode, purchase) = it
                when (responseCode) {
                    BillingClient.BillingResponseCode.OK -> if (purchase != null) validateAdFreePurchase(
                        purchase, adFreePrice, PurchaseType.AD_FREE
                    )

                    BillingClient.BillingResponseCode.USER_CANCELED -> logDebug(TAG, "Purchase of ad-free version was cancelled by the user")

                    else -> {
                        crashlytics.logBillingError(
                            TAG,
                            "Purchase of the ad-free version failed due to an unknown error during the purchase flow: $responseCode"
                        )
                        showToast(R.string.purchase_error_after_payment)
                    }
                }
            }
        })

        val deviceConfig by settingsViewModel.deviceConfigState.collectAsStateWithLifecycle()
        val methodConfig by settingsViewModel.methodConfigState.collectAsStateWithLifecycle()
        SettingsScreen(
            navType = navType,
            adFreePrice = adFreePrice,
            adFreeConfig = adFreeConfig,
            onContributorEnrollmentChange = {
                contributorUtils.flushSettings(this@MainActivity, it)
            },
            deviceConfig = deviceConfig,
            onDeviceSelect = {
                settingsViewModel.saveSelectedDevice(it)
                updateMismatchStatus()
            },
            methodConfig = methodConfig,
            onMethodSelect = {
                settingsViewModel.saveSelectedMethod(it)
                settingsViewModel.resubscribeToFcmTopic()

                if (!checkPlayServices(this@MainActivity, true)) {
                    showToast(R.string.notification_no_notification_support)
                }
            },
            onThemeSelect = settingsViewModel::updateTheme,
            advancedMode = viewModel.advancedMode,
            onAdvancedModeChange = { viewModel.persist(KeyAdvancedMode, it) },
            isPrivacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus == PrivacyOptionsRequirementStatus.REQUIRED,
            showPrivacyOptionsForm = {
                UserMessagingPlatform.showPrivacyOptionsForm(this@MainActivity) { error ->
                    if (error != null) crashlytics.logUmpConsentFormError(TAG, error)
                }
            },
            openAboutScreen = openAboutScreen,
            getPrefStr = viewModel::getPref,
            getPrefBool = viewModel::getPref,
            persistBool = viewModel::persist,
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun NavGraphBuilder.articleScreen(
        showAds: () -> Boolean,
        scrollBehavior: TopAppBarScrollBehavior,
    ) = composable(
        route = ArticleRoute,
        arguments = listOf(
            // Required argument, must be passed
            navArgument(IdArg) { type = LongType },
            /**
             * Optional argument, should be set only when opening via notification
             *
             * @see com.oxygenupdater.workers.DisplayDelayedNotificationWorker.getNotificationIntent
             */
            navArgument(ExternalArg) {
                type = BoolType
                defaultValue = false
            },
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = OuScheme + ArticleRoute },
            navDeepLink { uriPattern = BuildConfig.SERVER_DOMAIN + "api/.*/news-content/{$IdArg}/.*" },
            navDeepLink { uriPattern = BuildConfig.SERVER_DOMAIN + "article/{$IdArg}/" },
        )
    ) { entry ->
        val id = entry.arguments?.getLong(IdArg, NotSetL) ?: return@composable
        if (id == NotSetL) return@composable

        ArticleScreen(
            viewModel = hiltViewModel<ArticleViewModel>(this@MainActivity),
            id = id,
            scrollBehavior = scrollBehavior,
            loadInterstitialAd = {
                if (!showAds()) return@ArticleScreen
                interstitialAdLoadType = InterstitialAdLoadType.LoadAndShowDelayed
                loadInterstitialAd()
            },
        )
    }

    private fun NavGraphBuilder.installGuideScreen(
        setSubtitleResId: (Int) -> Unit,
    ) = composable(
        route = GuideRoute,
        arguments = listOf(
            // Optional argument, defaults to `false`
            navArgument(DownloadedArg) {
                type = BoolType
                defaultValue = false
            },
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = OuScheme + GuideRoute },
        )
    ) {
        InstallGuideScreen(
            viewModel = hiltViewModel<InstallGuideViewModel>(this@MainActivity),
            downloaded = it.arguments?.getBoolean(DownloadedArg, false) ?: false,
            setSubtitleResId = setSubtitleResId,
        )
    }

    private fun NavGraphBuilder.faqScreen(
        setSubtitleResId: (Int) -> Unit,
    ) = composable(
        route = FaqRoute,
        deepLinks = listOf(
            navDeepLink { uriPattern = OuScheme + FaqRoute },
        )
    ) {
        FaqScreen(
            viewModel = hiltViewModel<FaqViewModel>(this@MainActivity),
            setSubtitleResId = setSubtitleResId,
        )
    }

    /**
     * Validate the in app purchase on the app's server
     *
     * @param purchase Purchase which must be validated
     * @param amount Localized price
     * @param purchaseType the purchase type
     */
    private fun validateAdFreePurchase(
        purchase: Purchase,
        amount: String?,
        purchaseType: PurchaseType,
    ): Job = billingViewModel.verifyPurchase(
        purchase,
        amount,
        purchaseType
    ) {
        validatePurchaseTimer.cancel() // ensure only the latest task goes through
        if (isFinishing) return@verifyPurchase

        when {
            // If server can't be reached, keep trying until it can
            it == null -> validatePurchaseTimer.schedule(2000) {
                validateAdFreePurchase(purchase, amount, purchaseType)
            }

            !it.success -> crashlytics.logBillingError(
                TAG,
                "[validateAdFreePurchase] couldn't purchase ad-free: (${it.errorMessage})"
            )
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        consentInformation = UserMessagingPlatform.getConsentInformation(this)

        if (viewModel.isDeviceAndMethodSet) settingsViewModel.resubscribeToFcmTopic()
        if (!checkPlayServices(this, false)) {
            showToast(R.string.notification_no_notification_support)
        }

        lifecycle.addObserver(billingViewModel.lifecycleObserver)

        // Must be before calling setContent, because it uses intent-values
        val startScreen = handleIntentAndGetStartScreen(intent)

        setContent {
            val windowSize = calculateWindowSizeClass(this)

            /** Do this as early as possible because it's used in [onNewIntent] */
            navController = rememberNavController()

            AppTheme(settingsViewModel.theme) {
                EdgeToEdge()

                // We're using Surface to avoid Scaffold's recomposition-on-scroll issue (when using scrollBehaviour and consuming innerPadding)
                Surface(Modifier.semantics {
                    @OptIn(ExperimentalComposeUiApi::class)
                    testTagsAsResourceId = true
                }) {
                    Crossfade(
                        targetState = viewModel.shouldShowOnboarding,
                        label = "OnboardingMainCrossfade",
                    ) { shouldShowOnboarding ->
                        if (shouldShowOnboarding) OnboardingContent(windowSize) else {
                            if (viewModel.isOnboardingComplete) viewModel.shouldShowOnboarding = false else {
                                viewModel.shouldShowOnboarding = true
                                return@Crossfade // we shouldn't be here; exit
                            }

                            DisposableEffect(Unit) {
                                setupUmp()
                                onDispose {}
                            }

                            if (SDK_INT >= VERSION_CODES.TIRAMISU) NotificationPermission(
                                canShow = viewModel.canShowNotifPermissionSheet,
                                hide = { viewModel.persist(KeyIgnoreNotificationPermissionSheet, it) }
                            )

                            Content(windowSize, startScreen)
                        }
                    }
                }
            }
        }

        contributorUtils.startOrStop(this)
    }

    /** @return start [Screen] */
    private fun handleIntentAndGetStartScreen(intent: Intent?): Screen {
        checkIntentForExternalArticleUri(intent, true)

        // Action will be set if opened from a shortcut
        val startScreen = when (intent?.action) {
            ActionPageUpdate -> Screen.Update
            ActionPageNews -> Screen.NewsList
            ActionPageDevice -> Screen.Device
            else -> Screen.Update
        }

        defaultNavOptions = buildNavOptions(startScreen.route)

        // Force-show download error dialog if started from an intent with error info
        // TODO(compose/update): download error dialog logic is simplified in UpdateScreen. Verify if the dialog meant
        //  to be shown after clicking the download failed notification (i.e. on activity start) is shown
        //  immediately in the UI itself, in both cases: app in foreground and background/killed.
        //  Otherwise, see KEY_DOWNLOAD_ERROR_MESSAGE in UIF.setupServerResponseObservers()
        downloadErrorMessage = try {
            intent?.getStringExtra(KeyDownloadErrorMessage)
        } catch (ignored: IndexOutOfBoundsException) {
            null
        }

        return startScreen
    }

    private fun buildNavOptions(popUpTo: String?) = NavOptions.Builder()
        // Avoid multiple copies of the same destination when reselecting
        .setLaunchSingleTop(true)
        // Restore state on reselect
        .setRestoreState(true).apply {
            // Pop up to the start destination to avoid polluting back stack
            if (popUpTo != null) setPopUpTo(popUpTo, inclusive = false, saveState = true)
        }
        .build()

    private fun setupUmp() {
        val params = ConsentRequestParameters.Builder()
        if (BuildConfig.DEBUG) params.setConsentDebugSettings(
            ConsentDebugSettings.Builder(this)
                .setDebugGeography(DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .setForceTesting(true)
                .build()
        )

        if (!::consentInformation.isInitialized) {
            consentInformation = UserMessagingPlatform.getConsentInformation(this)
        }

        consentInformation.requestConsentInfoUpdate(this, params.build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this@MainActivity) { error ->
                if (error != null) crashlytics.logUmpConsentFormError(TAG, error)

                if (error != null || consentInformation.canRequestAds()) setupMobileAds()
            }
        }, { error ->
            crashlytics.logUmpConsentFormError(TAG, error)
            setupMobileAds()
        })

        // Check if SDK can be initialized in parallel while checking for new consent info.
        // Consent obtained in the previous session can be used to request ads.
        if (consentInformation.canRequestAds()) setupMobileAds()
    }

    private fun onBannerAdInit(adView: AdView) {
        bannerAdView?.let {
            // Destroy previous AdView if it changed
            if (it != adView) it.destroy()
        }

        // Only one will be active at any time, so update reference
        bannerAdView = adView

        /** Load only if [setupMobileAds] has been called via [setupUmp] */
        if (mobileAdsInitDone.get()) loadBannerAd(bannerAdView)
    }

    private val mobileAdsInitDone = AtomicBoolean(false)
    private fun setupMobileAds() {
        if (mobileAdsInitDone.get()) return else mobileAdsInitDone.set(true)

        MobileAds.initialize(this)
        MobileAds.setRequestConfiguration(MobileAds.getRequestConfiguration().toBuilder().apply {
            // If it's a debug build, add current device's ID to the list of test device IDs for ads
            if (BuildConfig.DEBUG) setTestDeviceIds(buildList(2) {
                /** (uppercase) MD5 checksum of "emulator" */
                add(AdRequest.DEVICE_ID_EMULATOR)

                /**
                 * (uppercase) MD5 checksum of [Settings.Secure.ANDROID_ID],
                 * which is what Play Services Ads expects.
                 */
                try {
                    @SuppressLint("HardwareIds")
                    val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                    val digest = MessageDigest.getInstance("MD5").digest(androidId.toByteArray())

                    // Create a 32-length uppercase hex string (`x` for lowercase)
                    add(String.format(Locale.US, "%032X", BigInteger(1, digest)))
                } catch (e: NoSuchAlgorithmException) {
                    crashlytics.logError(TAG, e.message ?: "MD5 algorithm not found", e)
                }
            })
        }.build())

        // By default video ads run at device volume, which could be annoying
        // to some users. We're reducing ad volume to be 10% of device volume.
        // Note that this doesn't always guarantee that ads will run at a
        // reduced volume. This is either a longstanding SDK bug or due to
        // an undocumented behaviour.
        MobileAds.setAppVolume(0.1f)

        // Init complete
        loadBannerAd(bannerAdView)
    }

    /**
     * Required because we use `android:launchMode="singleTask"` in AndroidManifest.xml
     *
     * @see <a href="https://developer.android.com/guide/navigation/design/deep-link#handle">AndroidX Navigation — Handling deep links</a>
     */
    override fun onNewIntent(intent: Intent?) = super.onNewIntent(intent).also {
        checkIntentForExternalArticleUri(intent, false)

        try {
            navController.handleDeepLink(intent)
        } catch (e: Exception) {
            crashlytics.logWarning(TAG, "NavController can't handle deep link", e)
        }
    }

    override fun onResume() = super.onResume().also {
        bannerAdView?.resume()
        viewModel.checkForStalledAppUpdate()
    }

    override fun onPause() = super.onPause().also {
        bannerAdView?.pause()
    }

    override fun onDestroy() = super.onDestroy().also {
        bannerAdView?.destroy()
        viewModel.maybePruneWork()
        viewModel.unregisterAppUpdateListener()
    }

    /** @return true if we're on [Screen.Settings] and required prefs aren't saved */
    private val NavController.shouldStopNavigateAwayFromSettings
        get() = currentDestination?.route == SettingsRoute && !viewModel.isDeviceAndMethodSet

    private fun showSettingsWarning() {
        val deviceId = viewModel.deviceId
        val updateMethodId = viewModel.updateMethodId

        if (deviceId == NotSetL || updateMethodId == NotSetL) {
            crashlytics.logWarning(TAG, "Required preferences not valid: $deviceId, $updateMethodId")
            showToast(R.string.settings_entered_incorrectly)
        } else showToast(R.string.settings_saving)
    }

    /**
     * Interstitial ads are limited to only once per 5 minutes for better UX.
     *
     * v5.2.0 onwards, this frequency capping is configured within AdMob dashboard itself,
     * because it seemed to be more reliable than custom SharedPreferences-based handling
     * done prior to v5.2.0.
     */
    private fun loadInterstitialAd() = InterstitialAd.load(
        this,
        BuildConfig.AD_INTERSTITIAL_NEWS_ID,
        buildAdRequest(),
        interstitialAdLoadCallback
    ).also {
        interstitialLoadCallMs = System.currentTimeMillis()
    }

    private var interstitialLoadCallMs = 0L

    /**
     * Meant to be used in [onNewIntent] *before* [NavController.handleDeepLink],
     * and also in [handleIntentAndGetStartScreen] for cold launches.
     *
     * An [intent] is considered to be an "external article URI" iff we're
     * receiving it from outside the app entirely (web URLs), or from outside
     * the [internal navigation][navController], e.g. from a notification.
     *
     * If it is so, we [loadInterstitialAd] with [InterstitialAdLoadType.LoadAndShowDelayed].
     *
     * @param intent the [Intent], whose [data][Intent.getData] we check
     * @param firstLaunch should be `true` only when called from [onCreate] (via
     *   [handleIntentAndGetStartScreen]. `false` if from [onNewIntent], because
     *   that means the app was already running, and thus [currentRoute] would
     *   already be set correctly to [ArticleRoute].
     *   (We use this to cancel showing the interstitial in [interstitialAdLoadCallback]).
     */
    private fun checkIntentForExternalArticleUri(
        intent: Intent?,
        firstLaunch: Boolean,
    ) {
        val uri = intent?.data?.normalizeScheme() ?: return
        val scheme = uri.scheme ?: return
        val host = uri.host ?: return
        val path = uri.path ?: return

        val isExternalArticleUri = when (scheme) {
            // Compare host without the `https://` at the beginning.
            // If it's somehow not our domain, return early.
            "http", "https" -> if ("$host/" != BuildConfig.SERVER_DOMAIN.substring(8)) false else {
                val segments = uri.pathSegments
                if (segments.isNullOrEmpty()) false
                // Compare path segments:
                // - First one must be either "api" or "article"
                // - if "api", the consecutive segments must be "news-content" followed by a number
                // - if "article", the next segment must be a number
                else {
                    val type = segments.getOrNull(0)
                    if (type.isNullOrEmpty()) false else when (type) {
                        // https://oxygenupdater.com/api/<version>/news-content/<id>/<lang>/<theme>
                        "api" -> {
                            if (segments.getOrNull(2) != "news-content") false
                            else segments.getOrNull(3)?.toLongOrNull() != null
                        }
                        // https://oxygenupdater.com/article/<id>/
                        "article" -> segments.getOrNull(1)?.toLongOrNull() != null
                        else -> false
                    }
                }
            }

            OuScheme -> {
                if ("$host/" != ChildScreen.Article.value || path.toLongOrNull() == null) false
                // Ensure it's marked as "external" (done only if intent is from a notification)
                else uri.getBooleanQueryParameter(ExternalArg, false)
            }

            else -> false
        }

        if (!isExternalArticleUri) return logDebug(TAG, "Intent data: $uri")
        logDebug(TAG, "External article URI detected: $uri")

        if (billingViewModel.shouldShowAds.value) {
            // If an article deep link came from outside the app, we
            // need to delay showing the interstitial ad for better UX.
            interstitialAdLoadType = InterstitialAdLoadType.LoadAndShowDelayed
            loadInterstitialAd()
        }

        // We need to manually set this because it is always set to "update"
        // when receiving this URI for the first time after launching.
        if (firstLaunch) currentRoute = ArticleRoute
    }

    @Immutable
    @JvmInline
    private value class InterstitialAdLoadType private constructor(val value: Int) {

        override fun toString() = "InterstitialAdLoadType." + when (this) {
            LoadOnly -> "LoadOnly"
            LoadAndShowImmediately -> "LoadAndShowImmediately"
            LoadAndShowDelayed -> "LoadAndShowDelayed"
            else -> "Invalid"
        }

        companion object {
            val LoadOnly = InterstitialAdLoadType(0)
            val LoadAndShowImmediately = InterstitialAdLoadType(1)
            val LoadAndShowDelayed = InterstitialAdLoadType(2)
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        private const val ActionPageUpdate = "com.oxygenupdater.action.page_update"
        private const val ActionPageNews = "com.oxygenupdater.action.page_news"
        private const val ActionPageDevice = "com.oxygenupdater.action.page_device"

        /**
         * Force even 3-button nav to be completely transparent on [Android 10+](https://github.com/android/nowinandroid/pull/817#issuecomment-1647079628)
         */
        private val navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    }
}
