# biometric_storage

[![Pub](https://img.shields.io/pub/v/biometric_storage?color=green)](https://pub.dev/packages/biometric_storage/)

Encrypted storage with optional biometric protection for Flutter apps.
It is designed for small secrets such as passwords, tokens, and key material,
not for large datasets.

Release baseline for this repository:
- Flutter `3.44`
- Dart `3.12`

## Platform support

| Platform | Backing store | Authentication gate |
|---|---|---|
| Android 7.0+ (API 24) | AES-GCM key in the Android Keystore (StrongBox when available, TEE fallback) | BiometricPrompt: Class 3 biometrics, optional device credential fallback |
| iOS 13+ | Keychain with access control | Face ID / Touch ID, optional passcode fallback |
| macOS 10.15+ | Keychain with access control | Touch ID / Apple Watch, optional password fallback |
| Linux | libsecret keyring | none |
| Windows | Credential Manager | none |
| Web | plaintext `localStorage`, **not secure** | none |

Check out [AuthPass Password Manager](https://authpass.app/) for an app which
makes heavy use of this plugin.

## Getting started

### Android

- Android 7.0 (API level `24`) or newer.
- The host activity must extend `FlutterFragmentActivity`, otherwise every
  authenticated operation fails with `AuthExceptionCode.failedToStart`.
- The activity theme must inherit from `Theme.AppCompat`.

**android/app/src/main/kotlin/.../MainActivity.kt**

```kotlin
import io.flutter.embedding.android.FlutterFragmentActivity

class MainActivity : FlutterFragmentActivity()
```

**android/app/src/main/res/values/styles.xml**

```xml
<resources>
  <style name="LaunchTheme" parent="Theme.AppCompat.NoActionBar" />
  <style name="NormalTheme" parent="Theme.AppCompat.NoActionBar" />
</resources>
```

### iOS

- Include the `NSFaceIDUsageDescription` key in your app's `Info.plist`;
  without it, iOS terminates the app when Face ID is first used.

**Known issue**: local authentication in the iOS simulator is unreliable on
recent iOS versions; test biometric flows on a real device.
https://developer.apple.com/forums/thread/685773

### macOS

- Include the `NSFaceIDUsageDescription` key in your app's `Info.plist` if
  you rely on biometrics.
- Enable signing and Keychain Sharing capability for the host app. Without
  it you will see a `BiometricStoragePluginException` with code
  `SecurityError` and message `-34018: A required entitlement isn't present.`

### Linux, Windows, and web

- Linux and Windows store values through the OS (libsecret / Credential
  Manager) without any authentication prompt; `canAuthenticate` reports
  `errorHwUnavailable` there.
- Web stores plaintext values in browser `localStorage`. Do not treat the
  web implementation as secure storage; it exists so cross-platform code
  keeps working during development.

## Usage

You only need four calls for the whole lifecycle:

```dart
import 'package:biometric_storage/biometric_storage.dart';

// 1. Check what the device supports.
final support = await BiometricStorage().canAuthenticate();
if (support != CanAuthenticateResponse.success &&
    support != CanAuthenticateResponse.statusUnknown) {
  // Inspect the value: no hardware, nothing enrolled, locked out, ...
  return;
}

// 2. Open (or create) a named store.
final store = await BiometricStorage().getStorage('my_token');

// 3. Write a value (may show the system authentication prompt).
await store.write('my secret');

// 4. Read it back (may prompt again, depending on the options).
final value = await store.read(); // null when nothing was stored
```

### Options

Options are applied when a store is first created; see
`StorageFileInitOptions` for the full documentation of each flag.

```dart
final store = await BiometricStorage().getStorage(
  'my_token',
  options: StorageFileInitOptions(
    // One authentication unlocks the store for 30 seconds (Android).
    androidAuthenticationValidityDuration: Duration(seconds: 30),
    // Allow PIN/pattern/password fallback (requires the duration above).
    androidBiometricOnly: false,
    // Allow the device passcode as fallback on iOS/macOS.
    darwinBiometricOnly: false,
    // Reuse one authenticated context for 30 seconds (iOS/macOS).
    darwinTouchIDAuthenticationForceReuseContextDuration:
        Duration(seconds: 30),
  ),
  promptInfo: const PromptInfo(
    androidPromptInfo: AndroidPromptInfo(title: 'Unlock your vault'),
    iosPromptInfo: DarwinPromptInfo(accessTitle: 'Unlock your vault'),
  ),
);
```

Set `authenticationRequired: false` to store values through the platform
keystore without any authentication prompt (useful as a fallback when
`canAuthenticate` reports that biometry is unavailable).

### Error handling

Every failure is a subtype of the sealed `BiometricStorageException`, so a
single `switch` handles all outcomes exhaustively:

```dart
try {
  final value = await store.read();
} on BiometricStorageException catch (e) {
  switch (e) {
    case AuthException(code: AuthExceptionCode.userCanceled):
      break; // The user knows they canceled; no error UI needed.
    case AuthException(code: AuthExceptionCode.lockedOut):
      showSnackBar('Too many attempts. Try again in a few seconds.');
    case AuthException(code: AuthExceptionCode.lockedOutPermanently):
      showSnackBar('Biometry locked. Unlock your device with PIN first.');
    case AuthException(:final code):
      showSnackBar('Authentication failed: $code');
    case StorageInvalidatedException():
      // The value is permanently unrecoverable, for example after the user
      // changed biometric enrollment on Android. Re-provision the secret.
      await store.delete();
      await promptUserToSignInAgain();
    case BiometricStoragePluginException(:final code):
      reportToCrashlytics('biometric_storage error $code: ${e.message}');
  }
}
```

`AuthException` means the user could not or did not authenticate; the stored
data is untouched and the call can be retried. `StorageInvalidatedException`
means the data is gone for good. `BiometricStoragePluginException` wraps
unexpected platform errors and preserves the raw `code` and `details`.

## Migrating from 5.x to 6.0

Stored values written by 5.x remain fully readable; all changes are at the
Dart source level.

| 5.x | 6.0 |
|---|---|
| `on PlatformException` around storage calls | `on BiometricStorageException` (raw platform exceptions no longer escape) |
| `AuthExceptionCode.unknown` for lockout, failed auth, missing hardware, ... | Dedicated codes: `lockedOut`, `lockedOutPermanently`, `authenticationFailed`, `noBiometricEnrolled`, `noHardware`, `hardwareUnavailable`, `passcodeNotSet`, `securityUpdateRequired`, `failedToStart` |
| `PlatformException` with code `StorageError:KeyInvalidated` | `StorageInvalidatedException` with `StorageInvalidatedReason.keyInvalidated` |
| `IosPromptInfo` | `DarwinPromptInfo` (the old name still compiles as a deprecated alias) |
| `authenticationValidityDurationSeconds: 30` | `androidAuthenticationValidityDuration: Duration(seconds: 30)` and/or the two darwin durations |
| `darwinTouchIDAuthenticationAllowableReuseDuration` implicitly also set force-reuse | Set `darwinTouchIDAuthenticationForceReuseContextDuration` explicitly |
| `canAuthenticate()` returned `errorHwUnavailable` for lockout and pending security updates | `errorLockedOut` (iOS/macOS) and `errorSecurityUpdateRequired` (Android) |
| Invalid storage names / option combinations failed natively at first use | `getStorage` throws `ArgumentError` immediately |

If you use exhaustive `switch` statements over `AuthExceptionCode` or
`CanAuthenticateResponse`, add the new cases (the analyzer points them out).

## Resources

- API documentation: https://pub.dev/documentation/biometric_storage/latest/
- Android data security: https://developer.android.com/topic/security/data
- Apple keychain + biometry: https://developer.apple.com/documentation/localauthentication/accessing-keychain-items-with-face-id-or-touch-id
