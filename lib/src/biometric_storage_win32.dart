import 'dart:convert';
import 'dart:ffi';

import 'package:ffi/ffi.dart';
import 'package:logging/logging.dart';
import 'package:win32/win32.dart';

import './biometric_storage.dart';

final _logger = Logger('biometric_storage_win32');

class Win32BiometricStoragePlugin extends BiometricStorage {
  Win32BiometricStoragePlugin() : super.create();

  static const namePrefix = 'design.codeux.authpass.';

  /// Registers this class as the default instance of [PathProviderPlatform]
  static void registerWith() {
    BiometricStorage.instance = Win32BiometricStoragePlugin();
  }

  @override
  Future<CanAuthenticateResponse> canAuthenticate({
    StorageFileInitOptions? options,
  }) async {
    return CanAuthenticateResponse.errorHwUnavailable;
  }

  @override
  Future<BiometricStorageFile> getStorage(
    String name, {
    StorageFileInitOptions? options,
    bool forceInit = false,
    PromptInfo promptInfo = PromptInfo.defaultValues,
  }) async {
    return BiometricStorageFile(this, namePrefix + name, promptInfo);
  }

  @override
  Future<bool> linuxCheckAppArmorError() async => false;

  @override
  Future<bool> delete(
    String name,
    PromptInfo promptInfo,
  ) async {
    return using((arena) {
      final namePointer = name.toPcwstr(allocator: arena);
      final Win32Result(:value, :error) = CredDelete(
        namePointer,
        CRED_TYPE_GENERIC,
      );
      if (!value) {
        if (error == ERROR_NOT_FOUND) {
          _logger.fine('Unable to find credential of name $name');
        } else {
          _logger.warning('Error deleting credential $name: $error');
        }
        return false;
      }
      return true;
    });
  }

  @override
  Future<String?> read(
    String name,
    PromptInfo promptInfo,
  ) async {
    _logger.finer('read($name)');
    return using((arena) {
      final credPointer = arena<Pointer<CREDENTIAL>>();
      final namePointer = name.toPcwstr(allocator: arena);
      try {
        final result = CredRead(namePointer, CRED_TYPE_GENERIC, credPointer);
        if (!result.value) {
          final errorCode = result.error;
          if (errorCode == ERROR_NOT_FOUND) {
            _logger.fine('Unable to find credential of name $name');
          } else {
            _logger.warning('Error: $errorCode ',
                WindowsException(HRESULT_FROM_WIN32(errorCode)));
          }
          return null;
        }
        final cred = credPointer.value.ref;
        final blob = cred.CredentialBlob.asTypedList(cred.CredentialBlobSize);

        return utf8.decode(blob);
      } finally {
        if (!credPointer.value.isNull) {
          _logger.fine('CredFree()');
          CredFree(credPointer.value);
        }

        _logger.fine('read($name) done.');
      }
    });
  }

  @override
  Future<void> write(
    String name,
    String content,
    PromptInfo promptInfo,
  ) async {
    _logger.fine('write()');
    using((arena) {
      final examplePassword = utf8.encode(content);
      final blob = examplePassword.isEmpty
          ? nullptr
          : examplePassword.toNative(allocator: arena);
      final namePointer = name.toPwstr(allocator: arena);
      final userNamePointer = 'flutter.biometric_storage'.toPwstr(
        allocator: arena,
      );

      final credential = arena<CREDENTIAL>()
        ..ref.Type = CRED_TYPE_GENERIC
        ..ref.TargetName = namePointer
        ..ref.Persist = CRED_PERSIST_LOCAL_MACHINE
        ..ref.UserName = userNamePointer
        ..ref.CredentialBlob = blob
        ..ref.CredentialBlobSize = examplePassword.length;
      final Win32Result(:value, :error) = CredWrite(credential, 0);
      if (!value) {
        throw BiometricStorageException(
          'Error writing credential $name: $error',
        );
      }
      _logger.fine('free done');
    });
  }
}
