package io.paritytech.polkadotapp.app.root.presentation.root

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import dagger.hilt.android.AndroidEntryPoint
import io.paritytech.polkadotapp.app.BuildConfig
import io.paritytech.polkadotapp.app.R
import io.paritytech.polkadotapp.app.kiosk.KioskDeviceAdminReceiver
import io.paritytech.polkadotapp.app.root.navigation.NavigationHolder
import io.paritytech.polkadotapp.app.root.navigation.executeBack
import io.paritytech.polkadotapp.app.root.presentation.root.compose.DevResetOverlay
import io.paritytech.polkadotapp.app.root.presentation.root.compose.chatoverlay.ChatExtensionOverlayHost
import io.paritytech.polkadotapp.common.domain.kiosk.KioskLockController
import io.paritytech.polkadotapp.common.presentation.deeplink.DeeplinkProcessingOutcome
import io.paritytech.polkadotapp.common.presentation.formatters.time.LocalTimeFormatter
import io.paritytech.polkadotapp.common.presentation.formatters.time.TimeFormatter
import io.paritytech.polkadotapp.common.presentation.notification.AppNotificationHost
import io.paritytech.polkadotapp.common.presentation.notification.AppNotifier
import io.paritytech.polkadotapp.common.presentation.resources.ContextManager
import io.paritytech.polkadotapp.common.presentation.screens.BaseScreenDelegate
import io.paritytech.polkadotapp.common.utils.observe
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.ConnectionStatusBanner
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ConnectionStatusBannerModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RootActivity : AppCompatActivity(R.layout.activity_root) {
    @Inject
    lateinit var navigationHolder: NavigationHolder

    @Inject
    lateinit var contextManager: ContextManager

    @Inject
    lateinit var timeFormatter: TimeFormatter

    @Inject
    lateinit var appNotifier: AppNotifier

    private val viewModel by viewModels<RootViewModel>()

    private val delegate = BaseScreenDelegate(
        context = { this },
        viewModel = ::viewModel
    )

    private companion object {
        const val KIOSK_TAG = "KioskLock"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        super.onCreate(savedInstanceState)

        // Keep the screen on while the POS host is foregrounded (kiosk terminals shouldn't sleep).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        navigationHolder.attach(navController)
        contextManager.attachActivity(this)

        // Enterprise POS kiosk: when this app is the Device Owner, lock the device to it
        // (true Android Lock Task). No-op on non-owner builds.
        setupKioskLockTask()

        // overwriting onBackPressedDispatcher as a workaround as otherwise after using default back navigation - navcontroller getting lost and can't navigate again to this screen
        // This can be reproduced by opening deposit screen from home screen, system back and opening it again
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigationHolder.executeBack()
            }
        })

        intent?.let(::processIntent)

        handleDeeplinkOutcome()
        if (BuildConfig.DEBUG) {
            setupDevResetOverlay()
        }
        setupAppNotificationOverlay()

        if (io.paritytech.polkadotapp.common.BuildConfig.CHAT_ENABLED) {
            setupChatExtensionOverlay()
        }
        // TODO network status currently is annoying: during real reconnects it may appear and disappear a lot
        // We need to improve stability of ConnectionStatusMonitor before bringing it back
//        setupConnectionStatusBanner()

        delegate.subscribeViewModelEvents()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        processIntent(intent)
    }

    /**
     * Allow-lists this app for Lock Task and ties Lock Task to the in-app kiosk state: the device
     * enters full (non-escapable) Lock Task only while the app's kiosk is engaged, and leaves it
     * when the kiosk exits. Only active when this app is the Device Owner and the build has kiosk
     * lockdown enabled.
     */
    private fun setupKioskLockTask() {
        if (!BuildConfig.KIOSK_LOCKDOWN_ENABLED) { Log.i(KIOSK_TAG, "skip: KIOSK_LOCKDOWN_ENABLED=false"); return }
        val dpm = getSystemService(DevicePolicyManager::class.java) ?: run { Log.i(KIOSK_TAG, "skip: no DevicePolicyManager"); return }
        if (!dpm.isDeviceOwnerApp(packageName)) { Log.i(KIOSK_TAG, "skip: not device owner ($packageName)"); return }
        val admin = ComponentName(this, KioskDeviceAdminReceiver::class.java)
        val pkgs = runCatching { dpm.setLockTaskPackages(admin, arrayOf(packageName)) }
        Log.i(KIOSK_TAG, "armed; setLockTaskPackages ok=${pkgs.isSuccess} err=${pkgs.exceptionOrNull()}")

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                KioskLockController.engaged.collect { engaged ->
                    Log.i(KIOSK_TAG, "engaged flow -> $engaged")
                    applyLockTask(engaged)
                }
            }
        }
    }

    /** Start/stop Lock Task to match the app's kiosk state. */
    private fun applyLockTask(engaged: Boolean) {
        val am = getSystemService(ActivityManager::class.java) ?: return
        val locked = am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        Log.i(KIOSK_TAG, "applyLockTask engaged=$engaged locked=$locked state=${am.lockTaskModeState}")
        if (engaged && !locked) {
            runCatching { startLockTask() }
                .onSuccess { Log.i(KIOSK_TAG, "startLockTask() OK") }
                .onFailure { Log.e(KIOSK_TAG, "startLockTask() FAILED", it) }
        } else if (!engaged && locked) {
            runCatching { stopLockTask() }
                .onFailure { Log.e(KIOSK_TAG, "stopLockTask() FAILED", it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        contextManager.detachActivity()
        navigationHolder.detach()
    }

    private fun handleDeeplinkOutcome() {
        viewModel.showDeeplinkOutcome.observe { deeplinkProcessingOutcome ->
            when (deeplinkProcessingOutcome) {
                DeeplinkProcessingOutcome.NoOp -> {}

                is DeeplinkProcessingOutcome.ShowMessage -> delegate.showMessage(
                    deeplinkProcessingOutcome.message
                )

                is DeeplinkProcessingOutcome.Navigate -> deeplinkProcessingOutcome.navigate()
            }
        }
    }

    private val navController: NavController by lazy(LazyThreadSafetyMode.NONE) {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.rootNavHost) as NavHostFragment

        navHostFragment.navController
    }

    private fun setupDevResetOverlay() {
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val showPrompt by viewModel.showDevResetPrompt.collectAsStateWithLifecycle()
                DevResetOverlay(
                    isVisible = showPrompt,
                    onStartOverClick = viewModel::onDevResetStartOverClick,
                    onDismissClick = viewModel::onDevResetDismissClick,
                )
            }
        }
        addContentView(composeView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun setupAppNotificationOverlay() {
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PolkadotTheme {
                    AppNotificationHost(notifier = appNotifier)
                }
            }
        }
        addContentView(composeView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun setupConnectionStatusBanner() {
        val rootContainer = findViewById<ViewGroup>(R.id.rootContainer)
        val composeView = findViewById<ComposeView>(R.id.connectionStatusBanner)
        val navHost = findViewById<View>(R.id.rootNavHost)

        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PolkadotTheme {
                    val model by viewModel.connectionStatusBanner.collectAsStateWithLifecycle()
                    val newVisibility = if (model == ConnectionStatusBannerModel.None) View.GONE else View.VISIBLE
                    if (visibility != newVisibility) {
                        visibility = newVisibility
                        ViewCompat.requestApplyInsets(rootContainer)
                    }
                    ConnectionStatusBanner(model = model)
                }
            }
        }

        // The banner draws behind the status bar; siblings must not also pad for it.
        // The default LinearLayout dispatch sends the same insets to every child, so
        // we intercept here and re-dispatch with the status-bar inset zeroed for the
        // nav host whenever the banner is visible.
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
            ViewCompat.dispatchApplyWindowInsets(composeView, insets)

            val navHostInsets = if (composeView.visibility == View.VISIBLE) {
                insets.consumeTopInsets()
            } else {
                insets
            }
            ViewCompat.dispatchApplyWindowInsets(navHost, navHostInsets)

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun WindowInsetsCompat.consumeTopInsets(): WindowInsetsCompat {
        val systemBars = getInsets(WindowInsetsCompat.Type.systemBars())
        return WindowInsetsCompat.Builder(this)
            .setInsets(
                WindowInsetsCompat.Type.systemBars(),
                Insets.of(systemBars.left, 0, systemBars.right, systemBars.bottom),
            )
            .setInsets(
                WindowInsetsCompat.Type.statusBars(),
                Insets.of(0, 0, 0, 0),
            )
            .build()
    }

    private fun setupChatExtensionOverlay() {
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(LocalTimeFormatter provides timeFormatter) {
                    ChatExtensionOverlayHost(
                        navController = navController,
                        overlays = viewModel.chatOverlays,
                        isOnboarded = viewModel.isOnboarded,
                        bottomNavHeight = viewModel.bottomNavHeight,
                    )
                }
            }
        }
        addContentView(composeView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun processIntent(intent: Intent) {
        intent.data?.let {
            viewModel.handleDeepLink(it)
        }
    }
}
