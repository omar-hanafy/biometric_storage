package design.codeux.biometric_storage

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.time.Duration

/** How an initialized cipher is going to be used. */
enum class CipherMode {
    Encrypt,
    Decrypt,
}

typealias ErrorCallback = (errorInfo: AuthenticationErrorInfo) -> Unit

/** Storage behavior configured once per storage file from the Dart `init` call. */
data class InitOptions(
    /**
     * When null, every access requires a fresh Class 3 (strong) biometric
     * authentication bound to the cipher through a `CryptoObject`
     * (auth-per-use key). When set, one successful authentication unlocks the
     * key for the given window (time-bound key) and the device credential is
     * accepted as a fallback on Android 11+.
     */
    val androidAuthenticationValidityDuration: Duration? = null,
    val authenticationRequired: Boolean = true,
    val androidBiometricOnly: Boolean = true,
)

/** Texts and behavior of the system authentication prompt, from the Dart side. */
data class AndroidPromptInfo(
    val title: String,
    val subtitle: String?,
    val description: String?,
    val negativeButton: String,
    val confirmationRequired: Boolean,
)

/**
 * Result of `canAuthenticate`. The wire format is the constant name and the
 * Dart side rejects unknown names, so constants must never be renamed and new
 * ones require a Dart-side mapping first.
 */
enum class CanAuthenticateResponse {
    Success,
    ErrorHwUnavailable,
    ErrorNoBiometricEnrolled,
    ErrorNoHardware,
    ErrorStatusUnknown,

    /** Neither a biometric nor a device credential is set up. */
    ErrorPasscodeNotSet,

    /** Maps to `CanAuthenticateResponse.unsupported` on the Dart side. */
    ErrorUnknown,
    ;

    companion object {
        fun fromBiometricManagerCode(code: Int): CanAuthenticateResponse = when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> Success
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> ErrorHwUnavailable
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> ErrorNoBiometricEnrolled
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> ErrorNoHardware
            // Biometrics are unusable until a security update is installed;
            // "hardware unavailable" is the closest supported wire name.
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> ErrorHwUnavailable
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> ErrorUnknown
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> ErrorStatusUnknown
            else -> ErrorStatusUnknown
        }
    }
}

/**
 * Error group reported to Dart as the `AuthError:<name>` error code.
 * The Dart side maps [UserCanceled], [Canceled] and [Timeout] to dedicated
 * exception codes; every other name maps to `AuthExceptionCode.unknown`.
 */
enum class AuthenticationError(private vararg val codes: Int) {
    Canceled(BiometricPrompt.ERROR_CANCELED),
    Timeout(BiometricPrompt.ERROR_TIMEOUT),
    UserCanceled(BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON),

    /** Too many failed attempts; locked out temporarily or until credential unlock. */
    LockedOut(BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT),
    Unknown(-1),

    /** The authentication flow could not be started at all. */
    Failed(-2),
    ;

    companion object {
        fun forCode(code: Int) = entries.firstOrNull { it.codes.contains(code) } ?: Unknown
    }
}

data class AuthenticationErrorInfo(
    val error: AuthenticationError,
    val message: CharSequence,
    val errorDetails: String? = null,
) {
    constructor(error: AuthenticationError, message: CharSequence, cause: Throwable) :
        this(error, message, cause.toCompleteString())
}

/** Errors with a well known error code that the Dart side can act upon. */
class MethodCallException(
    val errorCode: String,
    val errorMessage: String?,
    val errorDetails: Any? = null,
) : Exception(errorMessage ?: errorCode)

open class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A stored payload exists but cannot be decrypted or parsed safely. */
class CorruptedStorageDataException(message: String, cause: Throwable? = null) :
    StorageException(message, cause)

/** The Android Keystore entry backing a storage file is permanently unusable. */
class InvalidatedStorageKeyException(message: String, cause: Throwable? = null) :
    StorageException(message, cause)

fun Throwable.toCompleteString(): String {
    val out = StringWriter()
    printStackTrace(PrintWriter(out))
    return "$this\n$out"
}
