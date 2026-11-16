# E46 Board Monitor Launcher

## Option 1: Compile APK in the Cloud via GitHub (Zero Local Setup)
1. Create a free private repository on GitHub.
2. Upload all extracted files from `E46BoardLauncher/` to the repository root.
3. Open the **Actions** tab -> select **Build E46 Board Launcher APK** -> click **Run workflow**.
4. Download **E46-Board-Monitor-APK** (`app-debug.apk`) once the build completes (~90 seconds).

## Option 2: Compile Locally in Android Studio
1. Open the extracted `E46BoardLauncher` folder in Android Studio.
2. Wait for Gradle sync to finish, then click **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
3. Grab `app/build/outputs/apk/debug/app-debug.apk`.
