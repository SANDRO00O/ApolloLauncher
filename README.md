# TV Launcher (Apple TV style) - Android 5.1+

Build: push this folder to a GitHub repo -> Actions tab -> "Build APK" -> download artifact.

Install and test (does NOT replace the stock launcher yet):
  adb install -r app-debug.apk
  adb shell am start -n com.karrar.tvlauncher/.MainActivity
Then press Home on the remote and choose "TV Launcher" (Just once / Always).

Rollback: adb uninstall com.karrar.tvlauncher   (stock launcher returns as default)
