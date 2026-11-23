# Sade PDF

An open-source Android PDF reader. Open a PDF, scroll and zoom, find text, or unlock a
password-protected document. Share sends the original file byte for byte, including any
existing password protection.

Sade PDF works on **Android 15 (API 35) and later**. The app runs locally. Search uses
the PDF's text layer. Scanned pages need an existing text layer to be searchable.

[Download the signed APK](https://github.com/yalpayelekon/sade-pdf-android/releases/latest)

## Build

Install JDK 17 and Android SDK Platform 36, Extension Level 19
(`platforms;android-36-ext19`) in Android Studio's SDK Manager. Set `ANDROID_HOME`
or let Android Studio configure the SDK location.

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

On Windows, use `gradlew.bat`. To run the document-sharing tests, start an Android 15
or newer emulator and run `./gradlew :app:connectedDebugAndroidTest`.

## Files and privacy

Sade PDF uses the system file picker and can open PDFs from other apps. It keeps a
temporary local copy so the viewer and the Android Sharesheet use the same bytes.
On startup, the app removes inactive copies older than 24 hours. Sharing grants the
receiving app read access to the selected file.

Released under the Apache License 2.0.

## Privacy

Read the [Sade PDF privacy policy](https://yalpayelekon.github.io/sade-pdf-android/privacy/).
