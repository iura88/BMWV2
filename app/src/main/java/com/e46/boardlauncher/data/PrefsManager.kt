package com.e46.boardlauncher.data

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("e46_board_prefs", Context.MODE_PRIVATE)

    companion object {
        const val SLOT_NAV = 0
        const val SLOT_MEDIA = 1
        const val SLOT_PHONE = 2
        const val SLOT_FOURTH = 3

        const val ACCENT_ORANGE = 0xFFFF6A1A.toInt()
        const val ACCENT_M_BLUE = 0xFF0066B1.toInt()
        const val ACCENT_M_LIGHT_BLUE = 0xFF81C4FF.toInt()
        const val ACCENT_M_RED = 0xFFE7222E.toInt()
        const val ACCENT_WHITE = 0xFFE6E8EB.toInt()

        const val SPEED_KMH = 0
        const val SPEED_MPH = 1

        private const val KEY_SLOT_PREFIX = "tile_slot_"
        private const val KEY_DEFAULTS_INITIALIZED = "defaults_initialized"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_USE_24_HOUR = "use_24_hour"
        private const val KEY_DIM_ENABLED = "dim_enabled"
        private const val KEY_DIM_ALPHA = "dim_alpha"

        // Vehicle Profile Keys (Defaulting to BMW E46 M-Pack 320d M47)
        private const val KEY_CAR_MODEL = "car_model"
        private const val KEY_CAR_TRIM = "car_trim"
        private const val KEY_CAR_ENGINE = "car_engine"
        private const val KEY_CAR_YEAR = "car_year"
        private const val KEY_CAR_PLATE = "car_plate"
        private const val KEY_CAR_SHOW_PANEL = "car_show_panel"
        private const val KEY_SPEED_UNIT = "speed_unit"
        private const val KEY_ANIMATIONS_ENABLED = "animations_enabled"
    }

    fun areDefaultsInitialized(): Boolean =
        prefs.getBoolean(KEY_DEFAULTS_INITIALIZED, false)

    fun setDefaultsInitialized() {
        prefs.edit().putBoolean(KEY_DEFAULTS_INITIALIZED, true).apply()
    }

    fun getSlotPackage(slot: Int): String? =
        prefs.getString("$KEY_SLOT_PREFIX$slot", null)

    fun setSlotPackage(slot: Int, packageName: String?) {
        prefs.edit().putString("$KEY_SLOT_PREFIX$slot", packageName).apply()
    }

    fun getAllPinnedPackages(): List<String?> =
        listOf(
            getSlotPackage(SLOT_NAV),
            getSlotPackage(SLOT_MEDIA),
            getSlotPackage(SLOT_PHONE),
            getSlotPackage(SLOT_FOURTH)
        )

    fun findSlotForPackage(packageName: String): Int {
        for (slot in 0..3) {
            if (getSlotPackage(slot) == packageName) return slot
        }
        return -1
    }

    fun unpinPackage(packageName: String) {
        for (slot in 0..3) {
            if (getSlotPackage(slot) == packageName) {
                setSlotPackage(slot, null)
            }
        }
    }

    fun getAccentColor(): Int =
        prefs.getInt(KEY_ACCENT_COLOR, ACCENT_ORANGE)

    fun setAccentColor(color: Int) {
        prefs.edit().putInt(KEY_ACCENT_COLOR, color).apply()
    }

    fun is24HourClock(): Boolean =
        prefs.getBoolean(KEY_USE_24_HOUR, true)

    fun set24HourClock(use24Hour: Boolean) {
        prefs.edit().putBoolean(KEY_USE_24_HOUR, use24Hour).apply()
    }

    fun isDimEnabled(): Boolean =
        prefs.getBoolean(KEY_DIM_ENABLED, false)

    fun setDimEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DIM_ENABLED, enabled).apply()
    }

    fun getDimAlpha(): Float =
        prefs.getFloat(KEY_DIM_ALPHA, 0.50f)

    fun setDimAlpha(alpha: Float) {
        prefs.edit().putFloat(KEY_DIM_ALPHA, alpha.coerceIn(0.20f, 0.80f)).apply()
    }

    // Vehicle Profile & Telemetry Preferences
    fun getCarModel(): String =
        prefs.getString(KEY_CAR_MODEL, "BMW 320d (E46)") ?: "BMW 320d (E46)"

    fun setCarModel(value: String) {
        prefs.edit().putString(KEY_CAR_MODEL, value.trim().ifEmpty { "BMW E46" }).apply()
    }

    fun getCarTrim(): String =
        prefs.getString(KEY_CAR_TRIM, "M-SPORT PACK // SEDAN") ?: "M-SPORT PACK // SEDAN"

    fun setCarTrim(value: String) {
        prefs.edit().putString(KEY_CAR_TRIM, value.trim().ifEmpty { "E46 CHASSIS" }).apply()
    }

    fun getCarEngine(): String =
        prefs.getString(KEY_CAR_ENGINE, "2.0L M47 DIESEL • COILOVERS") ?: "2.0L M47 DIESEL • COILOVERS"

    fun setCarEngine(value: String) {
        prefs.edit().putString(KEY_CAR_ENGINE, value.trim().ifEmpty { "INLINE ENGINE" }).apply()
    }

    fun getCarYear(): String =
        prefs.getString(KEY_CAR_YEAR, "2004") ?: "2004"

    fun setCarYear(value: String) {
        prefs.edit().putString(KEY_CAR_YEAR, value.trim().ifEmpty { "2001-2006" }).apply()
    }

    fun getCarPlate(): String =
        prefs.getString(KEY_CAR_PLATE, "MM • E46 • BMW") ?: "MM • E46 • BMW"

    fun setCarPlate(value: String) {
        prefs.edit().putString(KEY_CAR_PLATE, value.trim().ifEmpty { "E46 BOARD" }).apply()
    }

    fun isCarPanelVisible(): Boolean =
        prefs.getBoolean(KEY_CAR_SHOW_PANEL, true)

    fun setCarPanelVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_CAR_SHOW_PANEL, visible).apply()
    }

    fun getSpeedUnit(): Int =
        prefs.getInt(KEY_SPEED_UNIT, SPEED_KMH)

    fun setSpeedUnit(unit: Int) {
        prefs.edit().putInt(KEY_SPEED_UNIT, unit).apply()
    }

    fun areAnimationsEnabled(): Boolean =
        prefs.getBoolean(KEY_ANIMATIONS_ENABLED, true)

    fun setAnimationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATIONS_ENABLED, enabled).apply()
    }
}
