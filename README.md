# UNIserverPhoneApp

UNIserverPhoneApp is an Android application that acts as a local server for a Raspberry Pi and smart glasses. It receives image frames and audio data from remote devices, displays a live Pi image feed, plays received audio, and can send text messages to glasses via a connected socket.

## Key Features

- Local TCP server listening on port `8888`
- Displays image frames received from a connected Raspberry Pi
- Receives audio clips from smart glasses and plays audio locally
- Sends text messages from the Android device to connected glasses
- Starts a Bluetooth RFCOMM server for Pi connections
- Shows connection status and debug logs in the UI

## Project Structure

- `app/src/main/java/com/example/UNIserverPhoneApp/MainActivity.java` - Main application logic
- `app/src/main/res/layout/activity_main.xml` - UI layout
- `app/src/main/AndroidManifest.xml` - App manifest and permissions
- `app/build.gradle.kts` - App module Gradle configuration
- `build.gradle.kts` - Root Gradle configuration

## Requirements

- Android Studio
- Android SDK 36
- Java 11 compatibility
- Device running Android 7.0+ (API level 24+) for app installation
- Physical Android device is recommended for Bluetooth functionality

## Permissions

The app requires the following permissions:

- `android.permission.BLUETOOTH_CONNECT`
- `android.permission.INTERNET`
- `android.permission.ACCESS_WIFI_STATE`
- `android.permission.CHANGE_WIFI_STATE`
- `android.permission.ACCESS_NETWORK_STATE`

## Setup and Build

1. Open the project in Android Studio.
2. Let Gradle sync and resolve dependencies.
3. Connect an Android device or configure an emulator.
4. Build and run the `app` module.

### Build from command line

From the project root, run:

```bash
gradlew assembleDebug
```

To install the debug APK on a connected device:

```bash
gradlew installDebug
```

## Running the App

1. Launch the app on your Android device.
2. Press `Start Server` to begin listening on port `8888` and start the RFCOMM Bluetooth server.
3. If a Raspberry Pi connects, the app expects to receive image frames and updates the image view.
4. If smart glasses connect, the app expects to receive audio clips and stores the last clip for playback.
5. Press `Play Audio` to play the latest audio clip received from glasses.
6. Enter a message in the text field and press `Send Message` to send text to the connected glasses.

## UI Overview

- `Server status` - Shows the current connection or server state
- `Start Server` / `Close Server` - Start or stop the app server
- `Play Audio` - Play the latest received audio clip
- `Image from Pi` - Displays incoming image frames from the Raspberry Pi
- `Send Message to Glasses` - Sends typed text to glasses
- `Debug Log` - Displays runtime logs and debug messages

## Communication Behavior

- TCP server: listens on port `8888`
- Pi image handling: accepts image frames as length-prefixed byte arrays, decodes them as bitmaps, and displays them
- Glasses audio handling: accepts length-prefixed PCM audio data and saves it for playback
- Bluetooth RFCOMM: starts a Bluetooth server socket with UUID `00001101-0000-1000-8000-00805F9B34FB`

## Notes and Caveats

- The current device selection logic uses `addr.contains("Pi")` to distinguish between Pi and glasses connections. This may not behave as expected for IP addresses and should be adjusted in production.
- Bluetooth permission handling is partially implemented and may require explicit runtime permission requests on newer Android versions.
- Audio playback is configured for PCM 16-bit mono at 16 kHz.
- The app currently uses classic socket streams and does not implement advanced reconnection logic.

## Dependencies

The app uses the following Android libraries via Gradle:

- AndroidX Activity KTX
- AndroidX AppCompat
- ConstraintLayout
- Material Components
- JUnit for unit tests
- AndroidX Espresso and JUnit extensions for instrumentation tests

## Troubleshooting

- If `Bluetooth adapter null` appears, the device does not support Bluetooth or the adapter is unavailable.
- If image frames or audio do not arrive, verify the remote device is connecting to the Android device on the correct port.
- If the Bluetooth server fails to start, ensure Bluetooth is enabled and permissions are granted.

## Future Improvements

- Improve Pi vs glasses detection logic
- Add proper runtime permission flow for Bluetooth and network permissions
- Add reconnect fallback and connection health checks
- Add UI feedback for message delivery and audio playback status
- Support secure socket communication and device pairing verification

## License

This project does not currently include a license file. Add a license if you intend to publish or share the source publicly.
