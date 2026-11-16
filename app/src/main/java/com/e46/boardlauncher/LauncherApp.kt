package com.e46.boardlauncher

import android.app.Application

class LauncherApp : Application() {
    companion object {
        @Volatile
        var hasShownColdSplash: Boolean = false
    }
}
