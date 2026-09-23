# Build and install the APK

## Fastest option: build online with GitHub Actions

This project already includes `.github/workflows/build-apk.yml`. It builds the installable debug APK and uploads it as a downloadable workflow artifact.

### From a phone or computer

1. Create or sign in to a GitHub account.
2. Create a new repository, for example `FloatRate`.
3. Upload the **contents of this `FloatRate` folder** into the repository. The repository root must contain `settings.gradle.kts`, `build.gradle.kts`, `app/`, and `.github/`.
4. Open the repository's **Actions** tab.
5. Select **Build FloatRate APK**.
6. Tap **Run workflow** and choose the `main` branch.
7. Wait for the green check mark.
8. Open the completed workflow run, scroll to **Artifacts**, and download `FloatRate-debug-apk`.
9. Extract the downloaded ZIP. The file inside is `app-debug.apk`.
10. Open the APK on your Android phone and allow installation from that source when Android asks.

The debug APK is signed with Android's debug key and is suitable for installing on your own phone. It is not a Play Store release. For Play Store distribution, create a private release keystore and build a signed release/AAB instead; never commit the keystore or its passwords to GitHub.

### If the workflow fails

- Confirm that the repository root is the `FloatRate` project itself, not a parent folder containing `FloatRate/FloatRate`.
- Open the failed job and read the first Gradle error.
- If GitHub reports an Android SDK package issue, rerun the workflow; the runner installs Android platform 35 and build tools 35.0.0.

## Recommended local option: Android Studio

On Windows, macOS, or Linux:

1. Install Android Studio and JDK 17.
2. Open the `FloatRate` folder.
3. Let Gradle sync.
4. Connect an Android phone with Developer Options and USB debugging enabled, or create an emulator.
5. Press **Run** to install directly, or use **Build > Build APK(s)**.
6. The debug APK will be at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Building only on an Android phone

It is possible but less reliable than GitHub Actions. Use a maintained Android IDE such as AndroidIDE if it supports the current Android Gradle Plugin and JDK 17, then clone/import the project and run the debug build. Modern Android builds need substantial storage and memory, and older apps such as AIDE may not support this project's Gradle/Kotlin versions.

For a phone-only workflow, GitHub Actions is usually easier: upload the files from your browser, run the workflow, then download the APK directly to the phone.

## First launch permissions

After installing:

1. Open FloatRate.
2. Allow **Display over other apps**.
3. Allow notifications when prompted; Android uses the notification for the persistent foreground service.
4. Select a token, chart style, and time preset.
5. Tap **FLOAT ... ABOVE OTHER APPS**.
6. Open Binance and drag the widget using its handle.
