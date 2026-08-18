package dev.argus.tracker.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import dev.argus.tracker.worker.ScanSettings
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AppEncryptionManager {
    private const val PREFS_NAME = "argus_encryption"
    private const val KEY_WRAP_SCHEME = "wrap_scheme"
    private const val KEY_WRAPPED_DEK_B64 = "wrapped_dek_b64"
    private const val KEY_WRAP_IV_B64 = "wrap_iv_b64"
    private const val KEY_PIN_SALT_B64 = "pin_salt_b64"
    private const val KEY_PIN_ITERATIONS = "pin_iterations"
    private const val KEY_LAST_BACKGROUND_EPOCH_MS = "last_background_epoch_ms"
    private const val KEY_PIN_FAILED_ATTEMPTS = "pin_failed_attempts"
    private const val KEY_PIN_LOCKOUT_UNTIL_EPOCH_MS = "pin_lockout_until_epoch_ms"
    private const val KEY_PIN_LAST_FAILURE_EPOCH_MS = "pin_last_failure_epoch_ms"
    private const val KEY_LAST_WIPE_EPOCH_MS = "last_wipe_epoch_ms"
    private const val KEY_WRAP_ROTATION_COUNT = "wrap_rotation_count"
    private const val KEY_WRAP_LAST_ROTATED_EPOCH_MS = "wrap_last_rotated_epoch_ms"

    private const val WRAP_SCHEME_KEYSTORE_PLAIN = "keystore_plain_v1"
    private const val WRAP_SCHEME_KEYSTORE_AUTH = "keystore_auth_v1"
    private const val WRAP_SCHEME_PIN = "pin_v1"

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS_PLAIN = "argus_master_plain_v1"
    private const val KEY_ALIAS_AUTH = "argus_master_auth_v1"

    private const val DEK_BYTES = 32
    private const val GCM_TAG_BITS = 128
    private const val BIOMETRIC_VALIDITY_WINDOW_SECONDS = 30
    private const val PIN_MIN_LENGTH = 6
    private const val PASSWORD_MIN_LENGTH = 8
    private const val PIN_PBKDF2_ITERATIONS_DEFAULT = 210_000
    const val PIN_FAIL_WIPE_THRESHOLD = 5

    private val lock = Any()

    @Volatile
    private var sessionDatabaseKey: ByteArray? = null

    data class EncryptionState(
        val enabled: Boolean,
        val unlockMethod: String,
        val canUseBiometric: Boolean,
        val requiresLaunchUnlock: Boolean,
        val pinConfigured: Boolean,
        val unlockedInSession: Boolean
    )

    data class PinUnlockState(
        val failedAttempts: Int,
        val lockoutUntilEpochMs: Long,
        val remainingLockoutMs: Long,
        val lastWipeEpochMs: Long?,
        val attemptsUntilWipe: Int?
    )

    data class WrapRotationState(
        val rotationCount: Int,
        val lastRotationEpochMs: Long?
    )

    fun initialize(context: Context) {
        ensureKeyMaterial(context)
        if (!ScanSettings.isFullEncryptionEnabled(context)) {
            ensureUnlockedForPlainMode(context)
        }
    }

    fun requiresLaunchUnlock(context: Context): Boolean =
        ScanSettings.isFullEncryptionEnabled(context)

    fun isSessionUnlocked(): Boolean = sessionDatabaseKey != null

    fun readState(context: Context): EncryptionState {
        val enabled = ScanSettings.isFullEncryptionEnabled(context)
        val unlockMethod = ScanSettings.getFullEncryptionUnlockMethod(context)
        return EncryptionState(
            enabled = enabled,
            unlockMethod = unlockMethod,
            canUseBiometric = canUseBiometricOrDeviceCredential(context),
            requiresLaunchUnlock = enabled,
            pinConfigured = currentWrapScheme(context) == WRAP_SCHEME_PIN,
            unlockedInSession = isSessionUnlocked()
        )
    }

    fun readPinUnlockState(context: Context): PinUnlockState {
        val prefs = prefs(context)
        val failedAttempts = prefs.getInt(KEY_PIN_FAILED_ATTEMPTS, 0).coerceAtLeast(0)
        val lockoutUntil = prefs.getLong(KEY_PIN_LOCKOUT_UNTIL_EPOCH_MS, 0L).coerceAtLeast(0L)
        val now = System.currentTimeMillis()
        val remaining = (lockoutUntil - now).coerceAtLeast(0L)
        val lastWipe = prefs.getLong(KEY_LAST_WIPE_EPOCH_MS, 0L).takeIf { it > 0L }
        val wipeOnFailEnabled = ScanSettings.isFullEncryptionPinWipeEnabled(context)
        val attemptsUntilWipe = if (wipeOnFailEnabled) {
            (PIN_FAIL_WIPE_THRESHOLD - failedAttempts).coerceAtLeast(0)
        } else {
            null
        }
        return PinUnlockState(
            failedAttempts = failedAttempts,
            lockoutUntilEpochMs = lockoutUntil,
            remainingLockoutMs = remaining,
            lastWipeEpochMs = lastWipe,
            attemptsUntilWipe = attemptsUntilWipe
        )
    }

    fun readWrapRotationState(context: Context): WrapRotationState {
        val prefs = prefs(context)
        val count = prefs.getInt(KEY_WRAP_ROTATION_COUNT, 0).coerceAtLeast(0)
        val last = prefs.getLong(KEY_WRAP_LAST_ROTATED_EPOCH_MS, 0L).takeIf { it > 0L }
        return WrapRotationState(
            rotationCount = count,
            lastRotationEpochMs = last
        )
    }

    fun onAppBackgrounded(context: Context) {
        if (!ScanSettings.isFullEncryptionEnabled(context)) return
        prefs(context).edit()
            .putLong(KEY_LAST_BACKGROUND_EPOCH_MS, System.currentTimeMillis())
            .apply()
    }

    fun onAppForegrounded(context: Context): Boolean {
        if (!ScanSettings.isFullEncryptionEnabled(context)) return isSessionUnlocked()
        if (!isSessionUnlocked()) return false

        val timeoutSeconds = ScanSettings.getFullEncryptionAutoLockTimeoutSeconds(context)
        if (timeoutSeconds <= 0L) return true

        val lastBackground = prefs(context).getLong(KEY_LAST_BACKGROUND_EPOCH_MS, 0L)
        if (lastBackground <= 0L) return true

        val elapsedMs = System.currentTimeMillis() - lastBackground
        if (elapsedMs >= timeoutSeconds * 1000L) {
            clearSession()
            return false
        }
        return true
    }

    fun forceLockNow(context: Context) {
        if (!ScanSettings.isFullEncryptionEnabled(context)) return
        prefs(context).edit()
            .putLong(KEY_LAST_BACKGROUND_EPOCH_MS, System.currentTimeMillis())
            .apply()
        clearSession()
    }

    fun getDatabasePassphrase(): ByteArray {
        val key = synchronized(lock) {
            sessionDatabaseKey?.copyOf()
        }
        return key ?: throw IllegalStateException("Encrypted storage is locked")
    }

    fun clearSession() {
        synchronized(lock) {
            zeroize(sessionDatabaseKey)
            sessionDatabaseKey = null
        }
    }

    fun unlockWithBiometric(context: Context): Boolean {
        if (currentWrapScheme(context) != WRAP_SCHEME_KEYSTORE_AUTH) return false
        val wrapped = readWrappedPayload(context) ?: return false
        val key = runCatching {
            decryptWithKeystore(
                alias = KEY_ALIAS_AUTH,
                iv = wrapped.iv,
                ciphertext = wrapped.ciphertext
            )
        }.getOrNull() ?: return false
        setSessionKey(key)
        resetPinFailureState(context)
        return true
    }

    fun unlockWithPin(context: Context, pin: String): Boolean {
        return unlockWithPasscode(context, pin)
    }

    fun unlockWithPassword(context: Context, password: String): Boolean {
        return unlockWithPasscode(context, password)
    }

    private fun unlockWithPasscode(context: Context, passcode: String): Boolean {
        if (currentWrapScheme(context) != WRAP_SCHEME_PIN) return false
        if (isPinTemporarilyLocked(context)) return false

        val wrapped = readWrappedPayload(context) ?: return false
        val salt = readPinSalt(context) ?: return false
        val iterations = readPinIterations(context)
        val key = runCatching {
            decryptWithPin(
                pin = passcode,
                salt = salt,
                iterations = iterations,
                iv = wrapped.iv,
                ciphertext = wrapped.ciphertext
            )
        }.getOrNull() ?: run {
            registerPinFailure(context)
            return false
        }
        setSessionKey(key)
        resetPinFailureState(context)
        return true
    }

    fun enableLaunchLockWithBiometric(context: Context) {
        val dek = obtainOrCreateCurrentDek(context)
        val wrapped = encryptWithKeystore(alias = KEY_ALIAS_AUTH, plaintext = dek)
        saveWrappedPayload(
            context = context,
            scheme = WRAP_SCHEME_KEYSTORE_AUTH,
            wrapped = wrapped,
            pinSalt = null,
            pinIterations = null
        )
        ScanSettings.setFullEncryptionUnlockMethod(
            context,
            ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_BIOMETRIC
        )
        ScanSettings.setFullEncryptionEnabled(context, true)
        setSessionKey(dek)
    }

    fun enableLaunchLockWithPin(context: Context, pin: String) {
        enableLaunchLockWithPasscode(
            context = context,
            passcode = pin,
            minLength = PIN_MIN_LENGTH,
            unlockMethod = ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_PIN,
            methodLabel = "PIN"
        )
    }

    fun enableLaunchLockWithPassword(context: Context, password: String) {
        enableLaunchLockWithPasscode(
            context = context,
            passcode = password,
            minLength = PASSWORD_MIN_LENGTH,
            unlockMethod = ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_PASSWORD,
            methodLabel = "Password"
        )
    }

    private fun enableLaunchLockWithPasscode(
        context: Context,
        passcode: String,
        minLength: Int,
        unlockMethod: String,
        methodLabel: String
    ) {
        val normalizedPasscode = passcode.trim()
        require(normalizedPasscode.length >= minLength) {
            "$methodLabel must be at least $minLength characters"
        }

        val dek = obtainOrCreateCurrentDek(context)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val wrapped = encryptWithPin(
            pin = normalizedPasscode,
            salt = salt,
            iterations = PIN_PBKDF2_ITERATIONS_DEFAULT,
            plaintext = dek
        )
        saveWrappedPayload(
            context = context,
            scheme = WRAP_SCHEME_PIN,
            wrapped = wrapped,
            pinSalt = salt,
            pinIterations = PIN_PBKDF2_ITERATIONS_DEFAULT
        )
        ScanSettings.setFullEncryptionUnlockMethod(context, unlockMethod)
        ScanSettings.setFullEncryptionEnabled(context, true)
        setSessionKey(dek)
    }

    fun disableLaunchLock(context: Context) {
        val dek = obtainOrCreateCurrentDek(context)
        val wrapped = encryptWithKeystore(alias = KEY_ALIAS_PLAIN, plaintext = dek)
        saveWrappedPayload(
            context = context,
            scheme = WRAP_SCHEME_KEYSTORE_PLAIN,
            wrapped = wrapped,
            pinSalt = null,
            pinIterations = null
        )
        ScanSettings.setFullEncryptionEnabled(context, false)
        setSessionKey(dek)
    }

    fun rotateWrappingMaterial(context: Context, pinForPinMode: String? = null): Boolean {
        val dek = runCatching { obtainOrCreateCurrentDek(context) }.getOrNull() ?: return false
        val scheme = currentWrapScheme(context)

        val result = runCatching {
            when (scheme) {
                WRAP_SCHEME_KEYSTORE_PLAIN -> {
                    val wrapped = encryptWithKeystore(alias = KEY_ALIAS_PLAIN, plaintext = dek)
                    saveWrappedPayload(context, scheme, wrapped, pinSalt = null, pinIterations = null)
                }
                WRAP_SCHEME_KEYSTORE_AUTH -> {
                    val wrapped = encryptWithKeystore(alias = KEY_ALIAS_AUTH, plaintext = dek)
                    saveWrappedPayload(context, scheme, wrapped, pinSalt = null, pinIterations = null)
                }
                WRAP_SCHEME_PIN -> {
                    val pin = pinForPinMode?.trim().orEmpty()
                    val minLength = when (ScanSettings.getFullEncryptionUnlockMethod(context)) {
                        ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_PASSWORD -> PASSWORD_MIN_LENGTH
                        else -> PIN_MIN_LENGTH
                    }
                    if (pin.length < minLength) return false
                    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                    val wrapped = encryptWithPin(
                        pin = pin,
                        salt = salt,
                        iterations = PIN_PBKDF2_ITERATIONS_DEFAULT,
                        plaintext = dek
                    )
                    saveWrappedPayload(
                        context = context,
                        scheme = scheme,
                        wrapped = wrapped,
                        pinSalt = salt,
                        pinIterations = PIN_PBKDF2_ITERATIONS_DEFAULT
                    )
                }
                else -> return false
            }
            val prefs = prefs(context)
            val nextCount = prefs.getInt(KEY_WRAP_ROTATION_COUNT, 0).coerceAtLeast(0) + 1
            prefs.edit()
                .putInt(KEY_WRAP_ROTATION_COUNT, nextCount)
                .putLong(KEY_WRAP_LAST_ROTATED_EPOCH_MS, System.currentTimeMillis())
                .apply()
            true
        }

        return result.getOrDefault(false)
    }

    fun canUseBiometricOrDeviceCredential(context: Context): Boolean {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return BiometricManager.from(context).canAuthenticate(authenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun ensureUnlockedForPlainMode(context: Context) {
        if (isSessionUnlocked()) return
        val scheme = currentWrapScheme(context)
        if (scheme != WRAP_SCHEME_KEYSTORE_PLAIN) return

        val wrapped = readWrappedPayload(context) ?: return
        runCatching {
            decryptWithKeystore(alias = KEY_ALIAS_PLAIN, iv = wrapped.iv, ciphertext = wrapped.ciphertext)
        }.onSuccess { setSessionKey(it) }
    }

    private fun ensureKeyMaterial(context: Context) {
        val prefs = prefs(context)
        if (prefs.contains(KEY_WRAP_SCHEME) && prefs.contains(KEY_WRAPPED_DEK_B64) && prefs.contains(KEY_WRAP_IV_B64)) {
            return
        }

        val dek = ByteArray(DEK_BYTES).also { SecureRandom().nextBytes(it) }
        val wrapped = encryptWithKeystore(alias = KEY_ALIAS_PLAIN, plaintext = dek)
        saveWrappedPayload(
            context = context,
            scheme = WRAP_SCHEME_KEYSTORE_PLAIN,
            wrapped = wrapped,
            pinSalt = null,
            pinIterations = null
        )
        setSessionKey(dek)
    }

    private fun obtainOrCreateCurrentDek(context: Context): ByteArray {
        synchronized(lock) {
            sessionDatabaseKey?.let { return it.copyOf() }
        }

        val payload = readWrappedPayload(context)
            ?: throw IllegalStateException("No encryption key payload found")

        val scheme = currentWrapScheme(context)
        val dek = when (scheme) {
            WRAP_SCHEME_KEYSTORE_PLAIN -> decryptWithKeystore(
                alias = KEY_ALIAS_PLAIN,
                iv = payload.iv,
                ciphertext = payload.ciphertext
            )
            WRAP_SCHEME_KEYSTORE_AUTH -> throw IllegalStateException("Unlock required: biometric/device credential")
            WRAP_SCHEME_PIN -> throw IllegalStateException("Unlock required: PIN/password")
            else -> throw IllegalStateException("Unsupported wrap scheme: $scheme")
        }
        setSessionKey(dek)
        return dek
    }

    private fun currentWrapScheme(context: Context): String =
        prefs(context).getString(KEY_WRAP_SCHEME, WRAP_SCHEME_KEYSTORE_PLAIN)
            .orEmpty()
            .ifBlank { WRAP_SCHEME_KEYSTORE_PLAIN }

    private data class WrappedPayload(
        val iv: ByteArray,
        val ciphertext: ByteArray
    )

    private fun readWrappedPayload(context: Context): WrappedPayload? {
        val prefs = prefs(context)
        val ivB64 = prefs.getString(KEY_WRAP_IV_B64, null) ?: return null
        val ciphertextB64 = prefs.getString(KEY_WRAPPED_DEK_B64, null) ?: return null
        val iv = decodeBase64(ivB64) ?: return null
        val ciphertext = decodeBase64(ciphertextB64) ?: return null
        return WrappedPayload(iv = iv, ciphertext = ciphertext)
    }

    private fun readPinSalt(context: Context): ByteArray? {
        val raw = prefs(context).getString(KEY_PIN_SALT_B64, null) ?: return null
        return decodeBase64(raw)
    }

    private fun readPinIterations(context: Context): Int =
        prefs(context).getInt(KEY_PIN_ITERATIONS, PIN_PBKDF2_ITERATIONS_DEFAULT)
            .coerceAtLeast(150_000)

    private fun saveWrappedPayload(
        context: Context,
        scheme: String,
        wrapped: WrappedPayload,
        pinSalt: ByteArray?,
        pinIterations: Int?
    ) {
        prefs(context).edit()
            .putString(KEY_WRAP_SCHEME, scheme)
            .putString(KEY_WRAP_IV_B64, encodeBase64(wrapped.iv))
            .putString(KEY_WRAPPED_DEK_B64, encodeBase64(wrapped.ciphertext))
            .putString(KEY_PIN_SALT_B64, pinSalt?.let(::encodeBase64))
            .putInt(KEY_PIN_ITERATIONS, pinIterations ?: PIN_PBKDF2_ITERATIONS_DEFAULT)
            .apply()
    }

    private fun isPinTemporarilyLocked(context: Context): Boolean {
        val lockoutUntil = prefs(context).getLong(KEY_PIN_LOCKOUT_UNTIL_EPOCH_MS, 0L)
        return lockoutUntil > System.currentTimeMillis()
    }

    private fun registerPinFailure(context: Context) {
        val prefs = prefs(context)
        val failedAttempts = prefs.getInt(KEY_PIN_FAILED_ATTEMPTS, 0).coerceAtLeast(0) + 1
        val now = System.currentTimeMillis()

        if (ScanSettings.isFullEncryptionPinWipeEnabled(context) && failedAttempts >= PIN_FAIL_WIPE_THRESHOLD) {
            wipeLocalEncryptedData(context)
            return
        }

        val lockoutSeconds = lockoutDurationSecondsForAttempt(failedAttempts)
        val lockoutUntil = if (lockoutSeconds > 0L) now + lockoutSeconds * 1000L else 0L

        prefs.edit()
            .putInt(KEY_PIN_FAILED_ATTEMPTS, failedAttempts)
            .putLong(KEY_PIN_LAST_FAILURE_EPOCH_MS, now)
            .putLong(KEY_PIN_LOCKOUT_UNTIL_EPOCH_MS, lockoutUntil)
            .apply()
    }

    private fun resetPinFailureState(context: Context) {
        prefs(context).edit()
            .putInt(KEY_PIN_FAILED_ATTEMPTS, 0)
            .putLong(KEY_PIN_LOCKOUT_UNTIL_EPOCH_MS, 0L)
            .putLong(KEY_PIN_LAST_FAILURE_EPOCH_MS, 0L)
            .apply()
    }

    private fun lockoutDurationSecondsForAttempt(failedAttempts: Int): Long = when {
        failedAttempts >= 9 -> 900L
        failedAttempts >= 7 -> 300L
        failedAttempts >= 5 -> 60L
        failedAttempts >= 3 -> 15L
        else -> 0L
    }

    private fun wipeLocalEncryptedData(context: Context) {
        clearSession()

        DefaultAppContainer.forceCloseAndDeleteDatabase(context)

        listOf("argus_settings", "nfc_ingest", "argus_mesh_state", PREFS_NAME).forEach { namespace ->
            runCatching { SecureSettingsStore.prefs(context, namespace).edit().clear().commit() }
            runCatching { context.getSharedPreferences(namespace, Context.MODE_PRIVATE).edit().clear().apply() }
        }

        runCatching {
            ScanSettings.setFullEncryptionEnabled(context, false)
            ScanSettings.setFullEncryptionUnlockMethod(
                context,
                ScanSettings.FULL_ENCRYPTION_UNLOCK_METHOD_BIOMETRIC
            )
            ScanSettings.setFullEncryptionPinWipeEnabled(context, false)
        }

        val prefs = prefs(context)
        prefs.edit()
            .putLong(KEY_LAST_WIPE_EPOCH_MS, System.currentTimeMillis())
            .putInt(KEY_PIN_FAILED_ATTEMPTS, 0)
            .putLong(KEY_PIN_LAST_FAILURE_EPOCH_MS, 0L)
            .putLong(KEY_PIN_LOCKOUT_UNTIL_EPOCH_MS, 0L)
            .putLong(KEY_LAST_BACKGROUND_EPOCH_MS, 0L)
            .apply()

        ensureKeyMaterial(context)
        ensureUnlockedForPlainMode(context)
    }

    private fun setSessionKey(key: ByteArray) {
        synchronized(lock) {
            zeroize(sessionDatabaseKey)
            sessionDatabaseKey = key.copyOf()
        }
    }

    private fun encryptWithKeystore(alias: String, plaintext: ByteArray): WrappedPayload {
        val key = getOrCreateKeystoreKey(alias = alias, requireAuth = alias == KEY_ALIAS_AUTH)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        return WrappedPayload(iv = cipher.iv, ciphertext = ciphertext)
    }

    private fun decryptWithKeystore(alias: String, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val key = getOrCreateKeystoreKey(alias = alias, requireAuth = alias == KEY_ALIAS_AUTH)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun encryptWithPin(
        pin: String,
        salt: ByteArray,
        iterations: Int,
        plaintext: ByteArray
    ): WrappedPayload {
        val key = derivePinKey(pin = pin, salt = salt, iterations = iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        return WrappedPayload(iv = cipher.iv, ciphertext = ciphertext)
    }

    private fun decryptWithPin(
        pin: String,
        salt: ByteArray,
        iterations: Int,
        iv: ByteArray,
        ciphertext: ByteArray
    ): ByteArray {
        val key = derivePinKey(pin = pin, salt = salt, iterations = iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun derivePinKey(pin: String, salt: ByteArray, iterations: Int): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun getOrCreateKeystoreKey(alias: String, requireAuth: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)

        if (requireAuth) {
            builder.setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    BIOMETRIC_VALIDITY_WINDOW_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(BIOMETRIC_VALIDITY_WINDOW_SECONDS)
            }
            builder.setInvalidatedByBiometricEnrollment(true)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun encodeBase64(data: ByteArray): String =
        Base64.encodeToString(data, Base64.NO_WRAP)

    private fun decodeBase64(raw: String): ByteArray? =
        runCatching { Base64.decode(raw, Base64.DEFAULT) }.getOrNull()

    private fun zeroize(bytes: ByteArray?) {
        if (bytes == null) return
        for (i in bytes.indices) {
            bytes[i] = 0
        }
    }

    private fun prefs(context: Context) =
        SecureSettingsStore.prefs(context, PREFS_NAME)
}
