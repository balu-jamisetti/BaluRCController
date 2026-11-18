BALU RC CONTROLLER — BUILD WITHOUT ANDROID STUDIO

You do NOT need Android Studio to build this APK. Use GitHub Actions in a web browser.

WHAT THIS APP DOES
- Connects to an already-paired HC-05 using Bluetooth Classic SPP (not BLE).
- Sends F, B, L, R, S only.
- Fixed motor speed is configured in your Arduino sketch as PWM 160.
- Hold a direction button to move; release to send STOP.
- Android 12+ Nearby Devices permission is requested.

BUILD THE APK USING GITHUB (NO ANDROID STUDIO)
1. On a computer or phone browser, sign in to https://github.com or create a free account.
2. Create a new repository. Give it a name such as BaluRCController.
3. Extract this ZIP on your device.
4. Upload ALL extracted project contents to the repository, preserving folders.
   Important: the .github folder is hidden on some devices/file managers. Make sure
   .github/workflows/android-build.yml is uploaded too.
5. Commit the uploaded files to the main branch.
6. Open the repository's Actions tab.
7. Select "Build Balu RC Controller APK" and press "Run workflow" if it did not
   start automatically after the upload.
8. Wait for the workflow to finish with a green check mark.
9. Open the completed workflow run, scroll to Artifacts, and download
   "Balu-RC-Controller-APK".
10. Extract the downloaded artifact ZIP. It contains app-debug.apk.
11. Transfer app-debug.apk to your Android 14 phone and install it.
    If Android asks, allow installation from that browser/file manager.

PAIR AND CONNECT
1. Pair HC-05 in Android Settings > Bluetooth first (commonly PIN 1234 or 0000).
2. Close Serial Bluetooth Terminal before opening this app; HC-05 normally accepts
   one active Bluetooth client at a time.
3. Open Balu RC Controller, grant Nearby Devices permission, select HC-05, tap CONNECT.

ARDUINO
- HC-05 TXD -> Arduino D10
- HC-05 RXD <- Arduino D11 through a voltage divider
- HC-05 baud rate: 9600
- L298N pins and movement commands must match your existing sketch.
- Commands: F forward, B backward, L left, R right, S stop.

SAFETY
A single L298N is often not suitable for four 12V BO motors. Check the combined
stall current of each motor pair against the driver's continuous and peak ratings.
Do not test with the wheels on the floor until the driver and power supply are verified.

BUILD DETAILS
The GitHub workflow uses JDK 17 and Gradle 8.7 to run assembleDebug.
The APK is unsigned for local testing; it can be installed directly on your phone
after enabling the Android installation permission for the source app.
