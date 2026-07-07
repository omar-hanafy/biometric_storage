package design.codeux.biometric_storage

import android.util.Log

/**
 * Minimal logging facade around [Log] so the plugin does not force a logging
 * dependency onto consuming apps.
 *
 * Debug output stays silent unless explicitly enabled on the device:
 * `adb shell setprop log.tag.BiometricStorage DEBUG`
 */
internal object StorageLog {
    const val TAG = "BiometricStorage"

    fun d(message: () -> String) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, message())
        }
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
