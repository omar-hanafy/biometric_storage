# biometric_storage

[![Pub](https://img.shields.io/pub/v/biometric_storage?color=green)](https://pub.dev/packages/biometric_storage/)

Encrypted file storage with optional biometric protection for Flutter apps.
It is designed for small secrets such as passwords, tokens, and key material,
not for large datasets.

Release baseline for this repository:
- Flutter `3.41.6`
- Dart `3.11.4`

Platform behavior:
- Android: AES-GCM via Android Keystore, optional biometric or device credential gating.
- iOS and macOS: LocalAuthentication with Keychain access control.
- Linux: libsecret keyring storage without biometric authentication.
- Windows: Windows Credential Manager without biometric authentication.
- Web: plaintext `localStorage` only. This is **not** secure storage.

Check out [AuthPass Password Manager](https://authpass.app/) for a app which 
makes heavy use of this plugin.

## Getting Started

### Installation

#### Android
- Android API level `23+`.
- Host activity must extend `FlutterFragmentActivity`.
- The activity theme must inherit from `Theme.AppCompat`.

Example:

**android/app/src/main/AndroidManifest.xml**

```xml
<activity
    android:name=".MainActivity"
    android:launchMode="singleTop"
    android:theme="@style/LaunchTheme">
    <meta-data
        android:name="io.flutter.embedding.android.NormalTheme"
        android:resource="@style/NormalTheme" />
</activity>
```

**android/app/src/main/res/values/styles.xml**

```xml
<resources>
  <style name="LaunchTheme" parent="Theme.AppCompat.NoActionBar" />
  <style name="NormalTheme" parent="Theme.AppCompat.NoActionBar" />
</resources>
```

##### Resources

* https://developer.android.com/topic/security/data
* https://developer.android.com/topic/security/best-practices

#### iOS

https://developer.apple.com/documentation/localauthentication/logging_a_user_into_your_app_with_face_id_or_touch_id

- Include the `NSFaceIDUsageDescription` key in your app `Info.plist`.
- This release baseline targets Flutter `3.41.6`.

**Known issue**: local authentication in the iOS simulator is unreliable on recent iOS versions:
https://developer.apple.com/forums/thread/685773

#### Mac OS

- Include the `NSFaceIDUsageDescription` key in your app `Info.plist` if you rely on biometrics.
- Enable signing and Keychain sharing for the host app. Without that you will likely see:
  `SecurityError, Error while writing data: -34018: A required entitlement isn't present.`
- This release baseline targets Flutter `3.41.6`.

#### Windows, Linux, and web

- Linux and Windows currently provide secure OS-backed storage, but no biometric prompt.
- Web currently stores plaintext values in browser `localStorage`.
  Do not treat the web implementation as secure storage.

### Usage

> You basically only need 4 methods.

1. Check whether biometric authentication is supported by the device

```dart
  final response = await BiometricStorage().canAuthenticate()
  if (response != CanAuthenticateResponse.success) {
    // panic..
  }
```

2. Create the access object

```dart
  final store = await BiometricStorage().getStorage('mystorage');
```

3. Read data

```dart
  final data = await store.read();
```

4. Write data

```dart
  final myNewData = 'Hello World';
  await store.write(myNewData);
```

See also the API documentation: https://pub.dev/documentation/biometric_storage/latest/biometric_storage/BiometricStorageFile-class.html#instance-methods
