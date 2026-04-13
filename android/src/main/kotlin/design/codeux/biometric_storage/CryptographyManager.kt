// based on https://github.com/isaidamier/blogs.biometrics.cryptoBlog/blob/cryptoObject/app/src/main/java/com/example/android/biometricauth/CryptographyManager.kt

/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package design.codeux.biometric_storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val logger = KotlinLogging.logger {}

open class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

class CorruptedStorageDataException(message: String, cause: Throwable? = null) :
    StorageException(message, cause)

class InvalidatedStorageKeyException(message: String, cause: Throwable? = null) :
    StorageException(message, cause)

interface CryptographyManager {

    /**
     * This method first gets or generates an instance of SecretKey and then initializes the Cipher
     * with the key. The secret key uses [ENCRYPT_MODE][Cipher.ENCRYPT_MODE] is used.
     */
    fun getInitializedCipherForEncryption(keyName: String): Cipher

    /**
     * This method first gets or generates an instance of SecretKey and then initializes the Cipher
     * with the key. The secret key uses [DECRYPT_MODE][Cipher.DECRYPT_MODE] is used.
     */
    fun getInitializedCipherForDecryption(keyName: String, initializationVector: ByteArray): Cipher
    fun getInitializedCipherForDecryption(keyName: String, encryptedDataFile: File): Cipher

    /**
     * The Cipher created with [getInitializedCipherForEncryption] is used here
     */
    fun encryptData(plaintext: String, cipher: Cipher): EncryptedData

    /**
     * The Cipher created with [getInitializedCipherForDecryption] is used here
     */
    fun decryptData(ciphertext: ByteArray, cipher: Cipher): String

}

fun CryptographyManager(configure: KeyGenParameterSpec.Builder.() -> Unit): CryptographyManagerImpl = CryptographyManagerImpl(configure)

@Suppress("ArrayInDataClass")
data class EncryptedData(val encryptedPayload: ByteArray)

class CryptographyManagerImpl(
    private val configure: KeyGenParameterSpec.Builder.() -> Unit
) :
    CryptographyManager {

    companion object {

        private const val KEY_SIZE: Int = 256

        /**
         * Prefix for the key name, to distinguish it from previously written key.
         * kind of namespacing it.
         */
        private const val KEY_PREFIX = "_CM_"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ENCRYPTION_BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val ENCRYPTION_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES

        private const val IV_SIZE_IN_BYTES = 12
        private const val TAG_SIZE_IN_BYTES = 16

    }

    override fun getInitializedCipherForEncryption(keyName: String): Cipher {
        try {
            val cipher = getCipher()
            val secretKey = getOrCreateSecretKey(keyName)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            return cipher
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw InvalidatedStorageKeyException(
                "The Android Keystore entry for '$keyName' was invalidated.",
                e
            )
        }
    }

    override fun getInitializedCipherForDecryption(
        keyName: String,
        initializationVector: ByteArray
    ): Cipher {
        try {
            val cipher = getCipher()
            val secretKey = getOrCreateSecretKey(keyName)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(TAG_SIZE_IN_BYTES * 8, initializationVector)
            )
            return cipher
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw InvalidatedStorageKeyException(
                "The Android Keystore entry for '$keyName' was invalidated.",
                e
            )
        }
    }

    override fun getInitializedCipherForDecryption(
        keyName: String,
        encryptedDataFile: File,
    ): Cipher {
        val iv = ByteArray(IV_SIZE_IN_BYTES)
        val count = encryptedDataFile.inputStream().use { it.read(iv) }
        if (count != IV_SIZE_IN_BYTES) {
            throw CorruptedStorageDataException(
                "Encrypted payload is truncated and does not contain a complete IV."
            )
        }
        return getInitializedCipherForDecryption(keyName, iv)
    }

    override fun encryptData(plaintext: String, cipher: Cipher): EncryptedData {
        val input = plaintext.toByteArray(Charsets.UTF_8)
        val ciphertext = ByteArray(IV_SIZE_IN_BYTES + input.size + TAG_SIZE_IN_BYTES)
        val bytesWritten = cipher.doFinal(input, 0, input.size, ciphertext, IV_SIZE_IN_BYTES)
        cipher.iv.copyInto(ciphertext)
        if (bytesWritten != input.size + TAG_SIZE_IN_BYTES) {
            throw IOException("Cipher output length did not match the expected AES-GCM payload size.")
        }
        if (cipher.iv.size != IV_SIZE_IN_BYTES) {
            throw IOException("Cipher IV length ${cipher.iv.size} did not match expected size $IV_SIZE_IN_BYTES.")
        }
        logger.debug { "encrypted ${input.size} (${ciphertext.size} output)" }
        return EncryptedData(ciphertext)
    }

    override fun decryptData(ciphertext: ByteArray, cipher: Cipher): String {
        logger.debug { "decrypting ${ciphertext.size} bytes (iv: ${IV_SIZE_IN_BYTES}, tag: ${TAG_SIZE_IN_BYTES})" }
        if (ciphertext.size < IV_SIZE_IN_BYTES + TAG_SIZE_IN_BYTES) {
            throw CorruptedStorageDataException(
                "Encrypted payload is too short to contain both IV and authentication tag."
            )
        }
        val iv = ciphertext.sliceArray(IntRange(0, IV_SIZE_IN_BYTES - 1))
        if (!iv.contentEquals(cipher.iv)) {
            throw CorruptedStorageDataException(
                "Encrypted payload IV does not match the initialized cipher IV."
            )
        }
        try {
            val plaintext = cipher.doFinal(
                ciphertext,
                IV_SIZE_IN_BYTES,
                ciphertext.size - IV_SIZE_IN_BYTES
            )
            return String(plaintext, Charset.forName("UTF-8"))
        } catch (e: AEADBadTagException) {
            throw CorruptedStorageDataException(
                "Encrypted payload failed the authentication tag check.",
                e
            )
        } catch (e: GeneralSecurityException) {
            throw CorruptedStorageDataException(
                "Encrypted payload could not be decrypted safely.",
                e
            )
        }
    }

    private fun getCipher(): Cipher {
        val transformation = "$ENCRYPTION_ALGORITHM/$ENCRYPTION_BLOCK_MODE/$ENCRYPTION_PADDING"
        return Cipher.getInstance(transformation)
    }

    fun deleteKey(keyName: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null) // Keystore must be loaded before it can be accessed
        try {
            keyStore.deleteEntry(KEY_PREFIX + keyName)
        } catch (e: KeyStoreException) {
            logger.warn(e) { "Unable to delete key from KeyStore $KEY_PREFIX$keyName" }
        }
    }

    private fun getOrCreateSecretKey(keyName: String): SecretKey {
        val realKeyName = KEY_PREFIX + keyName
        // If Secretkey was previously created for that keyName, then grab and return it.
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null) // Keystore must be loaded before it can be accessed
        keyStore.getKey(realKeyName, null)?.let { return it as SecretKey }

        // if you reach here, then a new SecretKey must be generated for that keyName
        val paramsBuilder = KeyGenParameterSpec.Builder(
            realKeyName,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
        paramsBuilder.apply {
            setBlockModes(ENCRYPTION_BLOCK_MODE)
            setEncryptionPaddings(ENCRYPTION_PADDING)
            setKeySize(KEY_SIZE)
            setUserAuthenticationRequired(true)
            configure()
        }

        val keyGenParams = paramsBuilder.build()
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGenerator.init(keyGenParams)
        return keyGenerator.generateKey()
    }

}
