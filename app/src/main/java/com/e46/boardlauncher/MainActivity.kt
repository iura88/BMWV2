package com.e46.boardlauncher

import android.Manifest
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.e46.boardlauncher.data.AppInfo
import com.e46.boardlauncher.data.AppRepository
import com.e46.boardlauncher.data.PrefsManager
import com.e46.boardlauncher.databinding.ActivityMainBinding
import com.e46.boardlauncher.databinding.DialogAppPickerBinding
import com.e46.boardlauncher.databinding.DialogCarProfileBinding
import com.e46.boardlauncher.media.MediaControllerHelper
import com.e46.boardlauncher.media.MediaNotificationListener
import com.e46.boardlauncher.media.NowPlayingState
import com.e46.boardlauncher.telemetry.TelemetrySnapshot
import com.e46.boardlauncher.telemetry.VehicleTelemetryManager
import com.e46.boardlauncher.ui.AppDrawerAdapter
import com.e46.boardlauncher.ui.ThemeHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private enum class ScreenMode {
        HOME, DRAWER, SETTINGS
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var appRepo: AppRepository
    private lateinit var mediaHelper: MediaControllerHelper
    private lateinit var telemetryManager: VehicleTelemetryManager
    private lateinit var drawerAdapter: AppDrawerAdapter
    private lateinit var audioManager: AudioManager

    private var currentMode: ScreenMode = ScreenMode.HOME
    private var latestMediaState: NowPlayingState? = null
    private var latestTelemetry: TelemetrySnapshot? = null

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateClockAndDate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableImmersiveFullscreen()

        prefs = PrefsManager(this)
        appRepo = AppRepository(applicationContext, prefs)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        setupDrawerRecycler()
        setupClickListeners()
        applyThemeAndSettings()
        updateClockAndDate()

        mediaHelper = MediaControllerHelper(this) { state ->
            runOnUiThread {
                latestMediaState = state
                renderNowPlaying(state)
            }
        }

        telemetryManager = VehicleTelemetryManager(this) { snapshot ->
            runOnUiThread {
                latestTelemetry = snapshot
                renderTelemetry(snapshot)
            }
        }

        appRepo.setOnAppsUpdatedListener { apps ->
            renderHomeTiles()
            renderDockShortcuts()
            renderSettingsValues()
            val pinnedSet = prefs.getAllPinnedPackages().filterNotNull().toSet()
            drawerAdapter.submitData(apps, pinnedSet, prefs.getAccentColor())
        }
        appRepo.registerPackageReceiver()
        appRepo.loadAppsAsync()

        if (savedInstanceState == null && !LauncherApp.hasShownColdSplash) {
            LauncherApp.hasShownColdSplash = true
            startColdSplash()
        } else {
            binding.splashOverlay.visibility = View.GONE
            animateHomeEntrance()
        }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveFullscreen()
        updateClockAndDate()
        registerTimeReceiver()
        mediaHelper.start()
        telemetryManager.start()
        renderVehicleCard()
        renderSettingsValues()
    }

    override fun onPause() {
        super.onPause()
        unregisterTimeReceiver()
        mediaHelper.stop()
        telemetryManager.stop()
        binding.viewHome.equalizerView.stop()
    }

    override fun onDestroy() {
        binding.ivSplashBadge.cancelReveal()
        appRepo.setOnAppsUpdatedListener(null)
        appRepo.unregisterPackageReceiver()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (currentMode != ScreenMode.HOME) {
            switchScreen(ScreenMode.HOME)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.splashOverlay.visibility == View.VISIBLE) {
            dismissSplashImmediately()
            return
        }
        if (currentMode != ScreenMode.HOME) {
            switchScreen(ScreenMode.HOME)
        }
    }

    private fun enableImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun startColdSplash() {
        binding.splashOverlay.visibility = View.VISIBLE
        binding.splashOverlay.alpha = 1f
        binding.splashOverlay.setOnClickListener {
            dismissSplashImmediately()
        }
        binding.ivSplashBadge.startStripeReveal(durationMs = 2100L) {
            fadeOutSplash()
        }
    }

    private fun fadeOutSplash() {
        if (binding.splashOverlay.visibility != View.VISIBLE) return
        binding.splashOverlay.animate()
            .alpha(0f)
            .setDuration(150L)
            .withEndAction {
                binding.splashOverlay.visibility = View.GONE
                animateHomeEntrance()
            }
            .start()
    }

    private fun dismissSplashImmediately() {
        binding.ivSplashBadge.cancelReveal()
        binding.splashOverlay.animate().cancel()
        binding.splashOverlay.visibility = View.GONE
        animateHomeEntrance()
    }

    private fun animateHomeEntrance() {
        if (!prefs.areAnimationsEnabled()) return
        val views = listOf(
            binding.viewHome.cardVehicleHero,
            binding.viewHome.tileNav,
            binding.viewHome.tileMedia,
            binding.viewHome.tilePhone,
            binding.viewHome.tileFourth,
            binding.viewHome.cardNowPlaying
        )
        views.forEachIndexed { idx, v ->
            v.alpha = 0f
            v.translationY = 14f
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((idx * 35).toLong())
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun setupDrawerRecycler() {
        val screenWidthDp = resources.configuration.screenWidthDp
        val columns = when {
            screenWidthDp >= 1200 -> 5
            screenWidthDp >= 900 -> 4
            else -> 3
        }
        drawerAdapter = AppDrawerAdapter(
            animationsEnabledProvider = { prefs.areAnimationsEnabled() },
            onAppClick = { app ->
                launchAppOrToast(app.packageName)
            },
            onAppLongClick = { app ->
                showPinSlotDialog(app)
            }
        )
        binding.viewDrawer.rvAppDrawer.layoutManager = GridLayoutManager(this, columns)
        binding.viewDrawer.rvAppDrawer.adapter = drawerAdapter
        binding.viewDrawer.rvAppDrawer.setHasFixedSize(true)
    }

    private fun setupClickListeners() {
        val animCheck = { prefs.areAnimationsEnabled() }

        // Tactile CarPlay press scale on interactive elements
        val pressables = listOf(
            binding.btnDockShortcut1,
            binding.btnDockShortcut2,
            binding.btnDockShortcut3,
            binding.btnDockModeSwitch,
            binding.btnDockSettings,
            binding.btnHeaderVolDown,
            binding.btnHeaderVolUp,
            binding.btnHeaderDim,
            binding.viewHome.cardVehicleHero,
            binding.viewHome.tileNav,
            binding.viewHome.tileMedia,
            binding.viewHome.tilePhone,
            binding.viewHome.tileFourth,
            binding.viewHome.btnMediaPrev,
            binding.viewHome.btnMediaPlayPause,
            binding.viewHome.btnMediaNext
        )
        pressables.forEach { ThemeHelper.attachTactilePressAnimation(it, animCheck) }

        // Left CarPlay Dock
        binding.btnDockShortcut1.setOnClickListener { launchSlot(PrefsManager.SLOT_NAV) }
        binding.btnDockShortcut2.setOnClickListener { launchSlot(PrefsManager.SLOT_MEDIA) }
        binding.btnDockShortcut3.setOnClickListener { launchSlot(PrefsManager.SLOT_PHONE) }
        binding.btnDockModeSwitch.setOnClickListener {
            if (currentMode == ScreenMode.HOME) {
                switchScreen(ScreenMode.DRAWER)
            } else {
                switchScreen(ScreenMode.HOME)
            }
        }
        binding.btnDockSettings.setOnClickListener {
            if (currentMode == ScreenMode.SETTINGS) {
                switchScreen(ScreenMode.HOME)
            } else {
                switchScreen(ScreenMode.SETTINGS)
            }
        }

        // Top Status Strip Quick Controls (Volume & Night Dim)
        binding.btnHeaderVolDown.setOnClickListener {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
        }
        binding.btnHeaderVolUp.setOnClickListener {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
        }
        binding.btnHeaderDim.setOnClickListener {
            val next = !prefs.isDimEnabled()
            prefs.setDimEnabled(next)
            applyDimOverlay()
            renderSettingsValues()
        }

        // Vehicle Hero Card (Tap to edit car model/specs, or toggle km/h vs mph on speed badge)
        binding.viewHome.cardVehicleHero.setOnClickListener {
            showCarProfileDialog()
        }
        binding.viewHome.layoutSpeedometer.setOnClickListener {
            val nextUnit = if (prefs.getSpeedUnit() == PrefsManager.SPEED_KMH) {
                PrefsManager.SPEED_MPH
            } else {
                PrefsManager.SPEED_KMH
            }
            prefs.setSpeedUnit(nextUnit)
            latestTelemetry?.let { renderTelemetry(it) }
            renderSettingsValues()
        }

        // Home 2x2 App Tiles
        binding.viewHome.tileNav.setOnClickListener { launchSlot(PrefsManager.SLOT_NAV) }
        binding.viewHome.tileNav.setOnLongClickListener {
            showAppPickerForSlot(PrefsManager.SLOT_NAV, getString(R.string.slot_nav_title))
            true
        }

        binding.viewHome.tileMedia.setOnClickListener { launchSlot(PrefsManager.SLOT_MEDIA) }
        binding.viewHome.tileMedia.setOnLongClickListener {
            showAppPickerForSlot(PrefsManager.SLOT_MEDIA, getString(R.string.slot_media_title))
            true
        }

        binding.viewHome.tilePhone.setOnClickListener { launchSlot(PrefsManager.SLOT_PHONE) }
        binding.viewHome.tilePhone.setOnLongClickListener {
            showAppPickerForSlot(PrefsManager.SLOT_PHONE, getString(R.string.slot_phone_title))
            true
        }

        binding.viewHome.tileFourth.setOnClickListener { launchSlot(PrefsManager.SLOT_FOURTH) }
        binding.viewHome.tileFourth.setOnLongClickListener {
            showAppPickerForSlot(PrefsManager.SLOT_FOURTH, getString(R.string.slot_fourth_title))
            true
        }

        // Now Playing Strip
        binding.viewHome.layoutNowPlayingInfo.setOnClickListener {
            val state = latestMediaState
            if (state != null && !state.hasPermission) {
                openNotificationListenerSettings()
            } else {
                val activePkg = mediaHelper.getActivePackageName()
                    ?: prefs.getSlotPackage(PrefsManager.SLOT_MEDIA)
                launchAppOrToast(activePkg)
            }
        }
        binding.viewHome.btnMediaPrev.setOnClickListener { mediaHelper.skipPrevious() }
        binding.viewHome.btnMediaPlayPause.setOnClickListener { mediaHelper.togglePlayPause() }
        binding.viewHome.btnMediaNext.setOnClickListener { mediaHelper.skipNext() }

        // Settings Screen: Vehicle Configuration
        binding.viewSettings.rowCarProfileEdit.setOnClickListener {
            showCarProfileDialog()
        }
        binding.viewSettings.rowCarPresetE46.setOnClickListener {
            prefs.setCarModel("BMW 320d (E46)")
            prefs.setCarTrim("M-SPORT PACK // SEDAN")
            prefs.setCarEngine("2.0L M47 DIESEL • COILOVERS")
            prefs.setCarYear("2004")
            prefs.setCarPlate("MM • E46 • BMW")
            renderVehicleCard()
            renderSettingsValues()
            Toast.makeText(this, "Loaded BMW E46 M-Sport Profile", Toast.LENGTH_SHORT).show()
        }
        binding.viewSettings.rowCarPanelVisibility.setOnClickListener {
            prefs.setCarPanelVisible(!prefs.isCarPanelVisible())
            renderVehicleCard()
            renderSettingsValues()
        }
        binding.viewSettings.rowSpeedUnit.setOnClickListener {
            val next = if (prefs.getSpeedUnit() == PrefsManager.SPEED_KMH) {
                PrefsManager.SPEED_MPH
            } else {
                PrefsManager.SPEED_KMH
            }
            prefs.setSpeedUnit(next)
            latestTelemetry?.let { renderTelemetry(it) }
            renderSettingsValues()
        }

        // Settings Screen: Tile Assignments
        binding.viewSettings.rowSlotNav.setOnClickListener {
            showAppPickerForSlot(PrefsManager.SLOT_NAV, getString(R.string.slot_nav_title))
        }
        binding.viewSettings.rowSlotMedia.setOnClickListener {
            showAppPickerForSlot(PrefsManager.SLOT_MEDIA, getString(R.string.slot_media_title))
        }
        binding.viewSettings.rowSlotPhone.setOnClickListener {
            showAppPickerForSlot(PrefsManager.SLOT_PHONE, getString(R.string.slot_phone_title))
        }
        binding.viewSettings.rowSlotFourth.setOnClickListener {
            showAppPickerForSlot(PrefsManager.SLOT_FOURTH, getString(R.string.slot_fourth_title))
        }

        // Settings Screen: Appearance & Animations
        binding.viewSettings.swatchOrange.setOnClickListener {
            updateAccentColor(PrefsManager.ACCENT_ORANGE)
        }
        binding.viewSettings.swatchMBlue.setOnClickListener {
            updateAccentColor(PrefsManager.ACCENT_M_BLUE)
        }
        binding.viewSettings.swatchMLightBlue.setOnClickListener {
            updateAccentColor(PrefsManager.ACCENT_M_LIGHT_BLUE)
        }
        binding.viewSettings.swatchMRed.setOnClickListener {
            updateAccentColor(PrefsManager.ACCENT_M_RED)
        }
        binding.viewSettings.swatchWhite.setOnClickListener {
            updateAccentColor(PrefsManager.ACCENT_WHITE)
        }

        binding.viewSettings.rowAnimationsToggle.setOnClickListener {
            prefs.setAnimationsEnabled(!prefs.areAnimationsEnabled())
            latestMediaState?.let { renderNowPlaying(it) }
            renderSettingsValues()
        }

        binding.viewSettings.rowClockFormat.setOnClickListener {
            prefs.set24HourClock(!prefs.is24HourClock())
            updateClockAndDate()
            renderSettingsValues()
        }

        binding.viewSettings.rowDimToggle.setOnClickListener {
            prefs.setDimEnabled(!prefs.isDimEnabled())
            applyDimOverlay()
            renderSettingsValues()
        }

        binding.viewSettings.btnDimLow.setOnClickListener {
            prefs.setDimAlpha(0.30f)
            prefs.setDimEnabled(true)
            applyDimOverlay()
            renderSettingsValues()
        }
        binding.viewSettings.btnDimMed.setOnClickListener {
            prefs.setDimAlpha(0.50f)
            prefs.setDimEnabled(true)
            applyDimOverlay()
            renderSettingsValues()
        }
        binding.viewSettings.btnDimHigh.setOnClickListener {
            prefs.setDimAlpha(0.70f)
            prefs.setDimEnabled(true)
            applyDimOverlay()
            renderSettingsValues()
        }

        // Settings Screen: System Integrations
        binding.viewSettings.rowGpsPermission.setOnClickListener {
            requestLocationPermissionForSpeedo()
        }
        binding.viewSettings.rowBluetoothSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            } catch (_: Exception) {
            }
        }
        binding.viewSettings.rowNotificationAccess.setOnClickListener {
            openNotificationListenerSettings()
        }
        binding.viewSettings.rowDefaultLauncher.setOnClickListener {
            openHomeLauncherSettings()
        }
        binding.viewSettings.rowAndroidSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    private fun switchScreen(target: ScreenMode) {
        if (currentMode == target) return
        currentMode = target

        val homeView = binding.viewHome.root
        val drawerView = binding.viewDrawer.root
        val settingsView = binding.viewSettings.root

        homeView.visibility = View.GONE
        drawerView.visibility = View.GONE
        settingsView.visibility = View.GONE

        val activeView = when (target) {
            ScreenMode.HOME -> {
                binding.tvHeaderTitle.text = getString(R.string.header_home)
                binding.ivDockModeIcon.setImageResource(R.drawable.ic_drawer_grid)
                homeView
            }
            ScreenMode.DRAWER -> {
                binding.tvHeaderTitle.text = getString(R.string.header_drawer)
                binding.ivDockModeIcon.setImageResource(R.drawable.ic_home)
                drawerView
            }
            ScreenMode.SETTINGS -> {
                binding.tvHeaderTitle.text = getString(R.string.header_settings)
                binding.ivDockModeIcon.setImageResource(R.drawable.ic_home)
                settingsView
            }
        }

        binding.btnDockModeSwitch.isSelected = (target == ScreenMode.DRAWER)
        binding.btnDockSettings.isSelected = (target == ScreenMode.SETTINGS)

        activeView.visibility = View.VISIBLE
        if (prefs.areAnimationsEnabled()) {
            activeView.alpha = 0f
            activeView.translationX = 16f
            activeView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(150L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            activeView.alpha = 1f
            activeView.translationX = 0f
        }
    }

    private fun updateAccentColor(color: Int) {
        prefs.setAccentColor(color)
        applyThemeAndSettings()
        val pinnedSet = prefs.getAllPinnedPackages().filterNotNull().toSet()
        drawerAdapter.submitData(appRepo.getCachedApps(), pinnedSet, color)
    }

    private fun applyThemeAndSettings() {
        val accent = prefs.getAccentColor()
        val density = resources.displayMetrics.density
        val stroke1dp = (1f * density).toInt().coerceAtLeast(1)

        val dockViews = listOf(
            binding.btnDockShortcut1,
            binding.btnDockShortcut2,
            binding.btnDockShortcut3,
            binding.btnDockModeSwitch,
            binding.btnDockSettings
        )
        for (v in dockViews) {
            v.background = ThemeHelper.createDockButtonBackground(accent, 14f * density, stroke1dp)
        }

        val iconTint = ThemeHelper.createIconTintList(accent)
        binding.ivDockModeIcon.imageTintList = iconTint
        binding.ivDockSettingsIcon.imageTintList = iconTint

        binding.btnHeaderVolDown.background =
            ThemeHelper.createDockButtonBackground(accent, 12f * density, stroke1dp)
        binding.btnHeaderVolUp.background =
            ThemeHelper.createDockButtonBackground(accent, 12f * density, stroke1dp)
        binding.btnHeaderDim.background =
            ThemeHelper.createDockButtonBackground(accent, 12f * density, stroke1dp)

        // Hero Vehicle Card & Home Tiles
        binding.viewHome.cardVehicleHero.background =
            ThemeHelper.createHeroVehicleBackground(accent, 14f * density, stroke1dp)
        binding.viewHome.tvCarTrimBadge.background =
            ThemeHelper.createPillBadgeBackground(accent, 8f * density, stroke1dp)
        binding.viewHome.tvCarTrimBadge.setTextColor(accent)
        binding.viewHome.viewVehicleAccentBar.setBackgroundColor(accent)

        val homeCards = listOf(
            binding.viewHome.tileNav,
            binding.viewHome.tileMedia,
            binding.viewHome.tilePhone,
            binding.viewHome.tileFourth,
            binding.viewHome.cardNowPlaying
        )
        for (card in homeCards) {
            card.background =
                ThemeHelper.createCarPlayCardBackground(accent, 14f * density, stroke1dp)
        }

        val tileTags = listOf(
            binding.viewHome.tvTileNavTag,
            binding.viewHome.tvTileMediaTag,
            binding.viewHome.tvTilePhoneTag,
            binding.viewHome.tvTileFourthTag,
            binding.viewHome.tvMediaSourceBadge
        )
        for (tag in tileTags) {
            tag.background = ThemeHelper.createPillBadgeBackground(accent, 6f * density, stroke1dp)
            tag.setTextColor(accent)
        }

        binding.viewHome.equalizerView.setAccentColor(accent)

        val mediaBtns = listOf(
            binding.viewHome.btnMediaPrev,
            binding.viewHome.btnMediaPlayPause,
            binding.viewHome.btnMediaNext
        )
        for (btn in mediaBtns) {
            btn.background =
                ThemeHelper.createDockButtonBackground(accent, 14f * density, stroke1dp)
        }

        // Settings Rows
        val settingsRows = listOf(
            binding.viewSettings.rowCarProfileEdit,
            binding.viewSettings.rowCarPresetE46,
            binding.viewSettings.rowCarPanelVisibility,
            binding.viewSettings.rowSpeedUnit,
            binding.viewSettings.rowSlotNav,
            binding.viewSettings.rowSlotMedia,
            binding.viewSettings.rowSlotPhone,
            binding.viewSettings.rowSlotFourth,
            binding.viewSettings.rowAnimationsToggle,
            binding.viewSettings.rowClockFormat,
            binding.viewSettings.rowDimToggle,
            binding.viewSettings.rowGpsPermission,
            binding.viewSettings.rowBluetoothSettings,
            binding.viewSettings.rowNotificationAccess,
            binding.viewSettings.rowDefaultLauncher,
            binding.viewSettings.rowAndroidSettings
        )
        for (row in settingsRows) {
            row.background =
                ThemeHelper.createCarPlayCardBackground(accent, 12f * density, stroke1dp)
        }

        binding.viewSettings.swatchOrange.background = ThemeHelper.createSwatchDrawable(
            PrefsManager.ACCENT_ORANGE,
            accent == PrefsManager.ACCENT_ORANGE,
            0xFFF2F4F7.toInt(),
            stroke1dp
        )
        binding.viewSettings.swatchMBlue.background = ThemeHelper.createSwatchDrawable(
            PrefsManager.ACCENT_M_BLUE,
            accent == PrefsManager.ACCENT_M_BLUE,
            0xFFF2F4F7.toInt(),
            stroke1dp
        )
        binding.viewSettings.swatchMLightBlue.background = ThemeHelper.createSwatchDrawable(
            PrefsManager.ACCENT_M_LIGHT_BLUE,
            accent == PrefsManager.ACCENT_M_LIGHT_BLUE,
            0xFFF2F4F7.toInt(),
            stroke1dp
        )
        binding.viewSettings.swatchMRed.background = ThemeHelper.createSwatchDrawable(
            PrefsManager.ACCENT_M_RED,
            accent == PrefsManager.ACCENT_M_RED,
            0xFFF2F4F7.toInt(),
            stroke1dp
        )
        binding.viewSettings.swatchWhite.background = ThemeHelper.createSwatchDrawable(
            PrefsManager.ACCENT_WHITE,
            accent == PrefsManager.ACCENT_WHITE,
            0xFFFF6A1A.toInt(),
            stroke1dp
        )

        renderVehicleCard()
        renderHomeTiles()
        renderDockShortcuts()
        applyDimOverlay()
        renderSettingsValues()
    }

    private fun renderVehicleCard() {
        val visible = prefs.isCarPanelVisible()
        binding.viewHome.cardVehicleHero.visibility = if (visible) View.VISIBLE else View.GONE
        binding.viewHome.tvCarModelTitle.text = prefs.getCarModel()
        binding.viewHome.tvCarTrimBadge.text = prefs.getCarTrim()
        binding.viewHome.tvCarEngineSpec.text =
            "${prefs.getCarYear()}  •  ${prefs.getCarEngine()}  •  [${prefs.getCarPlate()}]"
    }

    private fun renderTelemetry(snapshot: TelemetrySnapshot) {
        val isMph = prefs.getSpeedUnit() == PrefsManager.SPEED_MPH
        val displaySpeed = if (isMph) {
            (snapshot.speedKmh * 0.621371f).toInt()
        } else {
            snapshot.speedKmh
        }
        binding.viewHome.tvSpeedValue.text = if (snapshot.hasGpsFix) {
            displaySpeed.toString()
        } else {
            "0"
        }
        binding.viewHome.tvSpeedUnit.text = if (isMph) "MPH" else "KM/H"

        binding.tvHeaderTelemetry.text = buildString {
            append(if (snapshot.hasGpsFix) "GPS LOCK" else "GPS SEARCH")
            append("  •  ")
            append(if (snapshot.btConnected) "BT ON" else "BT OFF")
            append("  •  ")
            append(snapshot.voltageOrBattery)
        }
    }

    private fun applyDimOverlay() {
        val enabled = prefs.isDimEnabled()
        binding.btnHeaderDim.isSelected = enabled
        if (enabled) {
            binding.nightDimOverlay.visibility = View.VISIBLE
            binding.nightDimOverlay.alpha = prefs.getDimAlpha()
            binding.tvHeaderDimStatus.text = getString(R.string.dim_on)
        } else {
            binding.nightDimOverlay.visibility = View.GONE
            binding.tvHeaderDimStatus.text = getString(R.string.dim_off)
        }
    }

    private fun renderHomeTiles() {
        bindTile(
            slot = PrefsManager.SLOT_NAV,
            fallbackLabel = getString(R.string.slot_nav_title),
            tag = getString(R.string.slot_nav_tag),
            iconView = binding.viewHome.ivTileNavIcon,
            titleView = binding.viewHome.tvTileNavTitle,
            tagView = binding.viewHome.tvTileNavTag
        )
        bindTile(
            slot = PrefsManager.SLOT_MEDIA,
            fallbackLabel = getString(R.string.slot_media_title),
            tag = getString(R.string.slot_media_tag),
            iconView = binding.viewHome.ivTileMediaIcon,
            titleView = binding.viewHome.tvTileMediaTitle,
            tagView = binding.viewHome.tvTileMediaTag
        )
        bindTile(
            slot = PrefsManager.SLOT_PHONE,
            fallbackLabel = getString(R.string.slot_phone_title),
            tag = getString(R.string.slot_phone_tag),
            iconView = binding.viewHome.ivTilePhoneIcon,
            titleView = binding.viewHome.tvTilePhoneTitle,
            tagView = binding.viewHome.tvTilePhoneTag
        )
        bindTile(
            slot = PrefsManager.SLOT_FOURTH,
            fallbackLabel = getString(R.string.slot_fourth_title),
            tag = getString(R.string.slot_fourth_tag),
            iconView = binding.viewHome.ivTileFourthIcon,
            titleView = binding.viewHome.tvTileFourthTitle,
            tagView = binding.viewHome.tvTileFourthTag
        )
    }

    private fun bindTile(
        slot: Int,
        fallbackLabel: String,
        tag: String,
        iconView: ImageView,
        titleView: TextView,
        tagView: TextView
    ) {
        val pkg = prefs.getSlotPackage(slot)
        val app = appRepo.findAppByPackage(pkg)
        tagView.text = tag
        if (app != null) {
            iconView.setImageDrawable(app.icon)
            iconView.imageTintList = null
            titleView.text = app.label
        } else {
            iconView.setImageResource(R.drawable.ic_drawer_grid)
            titleView.text = "$fallbackLabel (Tap to set)"
        }
    }

    private fun renderDockShortcuts() {
        bindDockIcon(PrefsManager.SLOT_NAV, binding.ivDockShortcut1)
        bindDockIcon(PrefsManager.SLOT_MEDIA, binding.ivDockShortcut2)
        bindDockIcon(PrefsManager.SLOT_PHONE, binding.ivDockShortcut3)
    }

    private fun bindDockIcon(slot: Int, imageView: ImageView) {
        val app = appRepo.findAppByPackage(prefs.getSlotPackage(slot))
        if (app != null) {
            imageView.setImageDrawable(app.icon)
            imageView.imageTintList = null
        } else {
            imageView.setImageResource(R.drawable.ic_drawer_grid)
        }
    }

    private fun renderNowPlaying(state: NowPlayingState) {
        binding.viewHome.tvTrackTitle.text = state.title
        binding.viewHome.tvTrackArtist.text = state.artist
        binding.viewHome.tvMediaSourceBadge.text = state.sourceApp
        binding.viewHome.ivMediaPlayPause.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        binding.viewHome.equalizerView.setPlaying(state.isPlaying, prefs.areAnimationsEnabled())

        if (state.albumArt != null) {
            binding.viewHome.ivAlbumArt.setImageBitmap(state.albumArt)
            binding.viewHome.ivAlbumArt.scaleType = ImageView.ScaleType.CENTER_CROP
            binding.viewHome.ivAlbumArt.setPadding(0, 0, 0, 0)
        } else {
            binding.viewHome.ivAlbumArt.setImageResource(R.drawable.ic_music_note)
            binding.viewHome.ivAlbumArt.scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = (14 * resources.displayMetrics.density).toInt()
            binding.viewHome.ivAlbumArt.setPadding(pad, pad, pad, pad)
        }
    }

    private fun renderSettingsValues() {
        binding.viewSettings.tvCarProfileSummary.text =
            "${prefs.getCarModel()} • ${prefs.getCarYear()}"
        binding.viewSettings.tvCarPanelVisibilityValue.text =
            if (prefs.isCarPanelVisible()) "Shown on Home Screen" else "Hidden"
        binding.viewSettings.tvSpeedUnitValue.text =
            if (prefs.getSpeedUnit() == PrefsManager.SPEED_KMH) "KM/H (Metric)" else "MPH (Imperial)"

        binding.viewSettings.tvSlotNavValue.text =
            appRepo.findAppByPackage(prefs.getSlotPackage(PrefsManager.SLOT_NAV))?.label
                ?: getString(R.string.not_assigned)
        binding.viewSettings.tvSlotMediaValue.text =
            appRepo.findAppByPackage(prefs.getSlotPackage(PrefsManager.SLOT_MEDIA))?.label
                ?: getString(R.string.not_assigned)
        binding.viewSettings.tvSlotPhoneValue.text =
            appRepo.findAppByPackage(prefs.getSlotPackage(PrefsManager.SLOT_PHONE))?.label
                ?: getString(R.string.not_assigned)
        binding.viewSettings.tvSlotFourthValue.text =
            appRepo.findAppByPackage(prefs.getSlotPackage(PrefsManager.SLOT_FOURTH))?.label
                ?: getString(R.string.not_assigned)

        binding.viewSettings.tvAnimationsValue.text =
            if (prefs.areAnimationsEnabled()) "Enabled (Smooth Transitions)" else "Disabled (Instant)"

        binding.viewSettings.tvClockFormatValue.text =
            if (prefs.is24HourClock()) "24-Hour (14:30)" else "12-Hour (2:30 PM)"

        val dimPercent = (prefs.getDimAlpha() * 100).toInt()
        binding.viewSettings.tvDimToggleValue.text =
            if (prefs.isDimEnabled()) "Enabled ($dimPercent%)" else "Disabled"

        val density = resources.displayMetrics.density
        val stroke1dp = (1f * density).toInt().coerceAtLeast(1)
        val accent = prefs.getAccentColor()

        binding.viewSettings.btnDimLow.background =
            ThemeHelper.createDockButtonBackground(accent, 10f * density, stroke1dp)
        binding.viewSettings.btnDimMed.background =
            ThemeHelper.createDockButtonBackground(accent, 10f * density, stroke1dp)
        binding.viewSettings.btnDimHigh.background =
            ThemeHelper.createDockButtonBackground(accent, 10f * density, stroke1dp)

        binding.viewSettings.btnDimLow.isSelected = dimPercent in 25..35
        binding.viewSettings.btnDimMed.isSelected = dimPercent in 45..55
        binding.viewSettings.btnDimHigh.isSelected = dimPercent >= 65

        val gpsGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        binding.viewSettings.tvGpsPermissionValue.text =
            if (gpsGranted) "Granted (Live GPS Speed)" else "Tap to Grant Location"

        val notifGranted = MediaNotificationListener.isPermissionGranted(this)
        binding.viewSettings.tvNotificationAccessValue.text =
            if (notifGranted) "Granted (Now Playing Active)" else "Not Granted — Tap to Enable"
    }

    private fun showCarProfileDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dlgBinding = DialogCarProfileBinding.inflate(layoutInflater)
        dialog.setContentView(dlgBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.72).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val density = resources.displayMetrics.density
        val stroke1dp = (1f * density).toInt().coerceAtLeast(1)
        val accent = prefs.getAccentColor()
        dlgBinding.root.background =
            ThemeHelper.createCarPlayCardBackground(accent, 16f * density, stroke1dp)
        dlgBinding.btnCarCancel.background =
            ThemeHelper.createDockButtonBackground(accent, 12f * density, stroke1dp)
        dlgBinding.btnCarSave.background =
            ThemeHelper.createDockButtonBackground(accent, 12f * density, stroke1dp)
        dlgBinding.btnCarSave.isSelected = true

        dlgBinding.etCarModel.setText(prefs.getCarModel())
        dlgBinding.etCarTrim.setText(prefs.getCarTrim())
        dlgBinding.etCarEngine.setText(prefs.getCarEngine())
        dlgBinding.etCarYear.setText(prefs.getCarYear())
        dlgBinding.etCarPlate.setText(prefs.getCarPlate())

        dlgBinding.btnCarCancel.setOnClickListener { dialog.dismiss() }
        dlgBinding.btnCarSave.setOnClickListener {
            prefs.setCarModel(dlgBinding.etCarModel.text.toString())
            prefs.setCarTrim(dlgBinding.etCarTrim.text.toString())
            prefs.setCarEngine(dlgBinding.etCarEngine.text.toString())
            prefs.setCarYear(dlgBinding.etCarYear.text.toString())
            prefs.setCarPlate(dlgBinding.etCarPlate.text.toString())
            renderVehicleCard()
            renderSettingsValues()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun requestLocationPermissionForSpeedo() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            telemetryManager.startGpsIfPermitted()
            Toast.makeText(this, "GPS Speedometer already active", Toast.LENGTH_SHORT).show()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1001
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            telemetryManager.startGpsIfPermitted()
            renderSettingsValues()
        }
    }

    private fun launchSlot(slot: Int) {
        val pkg = prefs.getSlotPackage(slot)
        if (pkg.isNullOrBlank() || appRepo.findAppByPackage(pkg) == null) {
            val slotName = when (slot) {
                PrefsManager.SLOT_NAV -> getString(R.string.slot_nav_title)
                PrefsManager.SLOT_MEDIA -> getString(R.string.slot_media_title)
                PrefsManager.SLOT_PHONE -> getString(R.string.slot_phone_title)
                else -> getString(R.string.slot_fourth_title)
            }
            showAppPickerForSlot(slot, slotName)
            return
        }
        launchAppOrToast(pkg)
    }

    private fun launchAppOrToast(packageName: String?) {
        if (!appRepo.launchApp(packageName)) {
            Toast.makeText(this, R.string.err_app_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppPickerForSlot(slot: Int, slotTitle: String) {
        val apps = appRepo.getCachedApps()
        if (apps.isEmpty()) return

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val pickerBinding = DialogAppPickerBinding.inflate(layoutInflater)
        dialog.setContentView(pickerBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.65).toInt(),
            (resources.displayMetrics.heightPixels * 0.80).toInt()
        )

        pickerBinding.tvPickerTitle.text = "Select App for $slotTitle"
        pickerBinding.btnPickerClose.setOnClickListener { dialog.dismiss() }

        pickerBinding.rvPickerList.layoutManager = LinearLayoutManager(this)
        pickerBinding.rvPickerList.adapter = PickerAdapter(apps, prefs.getAccentColor()) { selectedApp ->
            prefs.setSlotPackage(slot, selectedApp.packageName)
            renderHomeTiles()
            renderDockShortcuts()
            renderSettingsValues()
            val pinnedSet = prefs.getAllPinnedPackages().filterNotNull().toSet()
            drawerAdapter.submitData(appRepo.getCachedApps(), pinnedSet, prefs.getAccentColor())
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showPinSlotDialog(app: AppInfo) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val pickerBinding = DialogAppPickerBinding.inflate(layoutInflater)
        dialog.setContentView(pickerBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.60).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val currentSlot = prefs.findSlotForPackage(app.packageName)
        pickerBinding.tvPickerTitle.text = if (currentSlot >= 0) {
            "${app.label} (Pinned to Tile ${currentSlot + 1})"
        } else {
            "Pin ${app.label} to Home"
        }
        pickerBinding.btnPickerClose.setOnClickListener { dialog.dismiss() }

        val options = mutableListOf(
            PickerOption(0, "Pin to Tile 1 (Navigation)"),
            PickerOption(1, "Pin to Tile 2 (Media)"),
            PickerOption(2, "Pin to Tile 3 (Phone)"),
            PickerOption(3, "Pin to Tile 4 (Utility)")
        )
        if (currentSlot >= 0) {
            options.add(PickerOption(-1, "Unpin from Home Screen"))
        }

        pickerBinding.rvPickerList.layoutManager = LinearLayoutManager(this)
        pickerBinding.rvPickerList.adapter = SlotOptionAdapter(options, prefs.getAccentColor()) { opt ->
            if (opt.slot == -1) {
                prefs.unpinPackage(app.packageName)
            } else {
                prefs.setSlotPackage(opt.slot, app.packageName)
            }
            renderHomeTiles()
            renderDockShortcuts()
            renderSettingsValues()
            val pinnedSet = prefs.getAllPinnedPackages().filterNotNull().toSet()
            drawerAdapter.submitData(appRepo.getCachedApps(), pinnedSet, prefs.getAccentColor())
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun openNotificationListenerSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "Open Android Settings > Notification Access", Toast.LENGTH_LONG).show()
        }
    }

    private fun openHomeLauncherSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    private fun updateClockAndDate() {
        val now = Date()
        val timePattern = if (prefs.is24HourClock()) "HH:mm" else "hh:mm"
        val timeText = DateFormat.format(timePattern, now).toString()
        val dateText = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
            .format(now)
            .uppercase(Locale.getDefault())

        binding.tvDockClock.text = timeText
        binding.tvDockDate.text = dateText
    }

    private fun registerTimeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(timeReceiver, filter)
    }

    private fun unregisterTimeReceiver() {
        try {
            unregisterReceiver(timeReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    private class PickerAdapter(
        private val apps: List<AppInfo>,
        private val accentColor: Int,
        private val onPick: (AppInfo) -> Unit
    ) : RecyclerView.Adapter<PickerAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivPickerIcon)
            val label: TextView = view.findViewById(R.id.tvPickerLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_picker_row, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = apps[position]
            val density = holder.itemView.resources.displayMetrics.density
            holder.itemView.background = ThemeHelper.createCarPlayCardBackground(
                accentColor,
                10f * density,
                (1f * density).toInt().coerceAtLeast(1)
            )
            holder.icon.visibility = View.VISIBLE
            holder.icon.setImageDrawable(item.icon)
            holder.label.text = item.label
            holder.itemView.setOnClickListener { onPick(item) }
        }

        override fun getItemCount(): Int = apps.size
    }

    private data class PickerOption(val slot: Int, val title: String)

    private class SlotOptionAdapter(
        private val options: List<PickerOption>,
        private val accentColor: Int,
        private val onPick: (PickerOption) -> Unit
    ) : RecyclerView.Adapter<SlotOptionAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivPickerIcon)
            val label: TextView = view.findViewById(R.id.tvPickerLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_picker_row, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = options[position]
            val density = holder.itemView.resources.displayMetrics.density
            holder.itemView.background = ThemeHelper.createCarPlayCardBackground(
                accentColor,
                10f * density,
                (1f * density).toInt().coerceAtLeast(1)
            )
            holder.icon.visibility = View.GONE
            holder.label.text = item.title
            holder.itemView.setOnClickListener { onPick(item) }
        }

        override fun getItemCount(): Int = options.size
    }
}
