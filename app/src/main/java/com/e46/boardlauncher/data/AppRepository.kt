package com.e46.boardlauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.core.content.ContextCompat
import com.e46.boardlauncher.R
import java.util.concurrent.Executors

class AppRepository(
    private val context: Context,
    private val prefs: PrefsManager
) {
    private val pm: PackageManager = context.packageManager
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val iconCache = LruCache<String, Drawable>(48)

    @Volatile
    private var cachedApps: List<AppInfo> = emptyList()

    private var onAppsUpdatedListener: ((List<AppInfo>) -> Unit)? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            iconCache.evictAll()
            loadAppsAsync(forceRefresh = true)
        }
    }

    fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(packageReceiver, filter)
    }

    fun unregisterPackageReceiver() {
        try {
            context.unregisterReceiver(packageReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    fun setOnAppsUpdatedListener(listener: ((List<AppInfo>) -> Unit)?) {
        onAppsUpdatedListener = listener
    }

    fun getCachedApps(): List<AppInfo> = cachedApps

    fun findAppByPackage(packageName: String?): AppInfo? {
        if (packageName.isNullOrBlank()) return null
        return cachedApps.firstOrNull { it.packageName == packageName }
    }

    fun loadAppsAsync(forceRefresh: Boolean = false) {
        if (!forceRefresh && cachedApps.isNotEmpty()) {
            onAppsUpdatedListener?.invoke(cachedApps)
            return
        }

        executor.execute {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val ownPackage = context.packageName
            val seenPackages = HashSet<String>(resolveInfos.size)
            val loaded = ArrayList<AppInfo>(resolveInfos.size)

            for (ri in resolveInfos) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == ownPackage || !seenPackages.add(pkg)) continue

                val label = ri.loadLabel(pm)?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: pkg
                val icon = getScaledIcon(pkg)
                loaded.add(AppInfo(label = label, packageName = pkg, icon = icon))
            }

            loaded.sortBy { it.label.lowercase() }
            initializeDefaultTilesIfNeeded(loaded)
            cachedApps = loaded

            mainHandler.post {
                onAppsUpdatedListener?.invoke(loaded)
            }
        }
    }

    private fun getScaledIcon(packageName: String): Drawable {
        iconCache.get(packageName)?.let { return it }

        val rawDrawable = try {
            pm.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            ContextCompat.getDrawable(context, R.drawable.ic_drawer_grid)!!
        }

        val targetPx = 96
        val bitmap = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        rawDrawable.setBounds(0, 0, targetPx, targetPx)
        rawDrawable.draw(canvas)

        val scaled = BitmapDrawable(context.resources, bitmap)
        iconCache.put(packageName, scaled)
        return scaled
    }

    private fun initializeDefaultTilesIfNeeded(apps: List<AppInfo>) {
        if (prefs.areDefaultsInitialized() || apps.isEmpty()) return

        val navCandidates = listOf(
            "com.waze",
            "com.google.android.apps.maps",
            "com.sygic.aura",
            "com.navngo.igo.javaclient"
        )
        val mediaCandidates = listOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.maxmpz.audioplayer",
            "com.apple.android.music"
        )
        val phoneCandidates = listOf(
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.contacts"
        )
        val settingsCandidates = listOf(
            "com.android.settings"
        )

        val navPkg = pickFirstInstalled(apps, navCandidates)
            ?: apps.firstOrNull {
                it.packageName.contains("map", true) ||
                    it.packageName.contains("nav", true) ||
                    it.label.contains("map", true) ||
                    it.label.contains("waze", true)
            }?.packageName

        val mediaPkg = pickFirstInstalled(apps, mediaCandidates)
            ?: apps.firstOrNull {
                it.packageName.contains("music", true) ||
                    it.packageName.contains("radio", true) ||
                    it.packageName.contains("audio", true) ||
                    it.label.contains("music", true) ||
                    it.label.contains("radio", true)
            }?.packageName

        val phonePkg = pickFirstInstalled(apps, phoneCandidates)
            ?: apps.firstOrNull {
                it.packageName.contains("dialer", true) ||
                    it.packageName.contains("phone", true) ||
                    it.packageName.contains("bluetooth", true) ||
                    it.label.contains("phone", true) ||
                    it.label.contains("bluetooth", true)
            }?.packageName

        val fourthPkg = pickFirstInstalled(apps, settingsCandidates)
            ?: apps.firstOrNull { it.packageName != navPkg && it.packageName != mediaPkg && it.packageName != phonePkg }?.packageName

        prefs.setSlotPackage(PrefsManager.SLOT_NAV, navPkg)
        prefs.setSlotPackage(PrefsManager.SLOT_MEDIA, mediaPkg)
        prefs.setSlotPackage(PrefsManager.SLOT_PHONE, phonePkg)
        prefs.setSlotPackage(PrefsManager.SLOT_FOURTH, fourthPkg)
        prefs.setDefaultsInitialized()
    }

    private fun pickFirstInstalled(apps: List<AppInfo>, candidates: List<String>): String? {
        for (candidate in candidates) {
            if (apps.any { it.packageName == candidate }) {
                return candidate
            }
        }
        return null
    }

    fun launchApp(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(launchIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
