/// Encrypted storage with optional biometric protection.
///
/// Start with [BiometricStorage.canAuthenticate] to check device support,
/// then open a store with [BiometricStorage.getStorage] and use
/// [BiometricStorageFile.read], [BiometricStorageFile.write], and
/// [BiometricStorageFile.delete]. Every failure surfaces as a subtype of
/// the sealed [BiometricStorageException].
library;

export 'src/biometric_storage.dart';
export 'src/biometric_storage_win32_fake.dart'
    if (dart.library.io) 'src/biometric_storage_win32.dart';
