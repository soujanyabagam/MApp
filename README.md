# MApp
AR Mobile App
1. Open the right folder
Start Android Studio.
File → Open…
Select the Gradle project root — the directory that contains settings.gradle.kts, app/, gradlew.bat, not the parent Core repo unless that’s where the Android files live.
Choose Trust Project if asked.
Wait for Gradle sync to finish (status bar / “Gradle Build” tool window). If it offers to install SDK Platform 35, Build-Tools, or JDK 17, accept.
2. Connect a phone
Use a physical device with Google Play Services for AR (ARCore usually does not work on typical emulators).
Enable Developer options and USB debugging, plug in USB, allow debugging on the phone.
In Android Studio’s device dropdown (toolbar), pick your phone.
3. Run the app
In the toolbar, confirm the run configuration is app (Android App).
Click the green Run button (or Shift+F10).
Studio builds, installs, and launches the app.
When prompted on the phone, allow Camera permission.
If something fails
No device: fix USB/cable/drivers, or Tools → Device Manager to confirm the device is listed.
Sync errors: open File → Project Structure → SDK Location and ensure an Android SDK path is set; install missing components from Tools → SDK Manager.
ARCore message on screen: install/update Google Play Services for AR from Play Store and open the app again.
Optional: build APK without running
Build → Build Bundle(s) / APK(s) → Build APK(s). Debug APK path: app/build/outputs/apk/debug/app-debug.apk.
