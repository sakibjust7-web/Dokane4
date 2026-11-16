package com.example.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class PhotoStorageMode(val prefValue: String) {
    SYNC("sync"),
    PHONE_ONLY("phone_only");

    companion object {
        fun fromPrefValue(value: String?): PhotoStorageMode {
            return entries.firstOrNull { it.prefValue.equals(value, ignoreCase = true) } ?: SYNC
        }
    }
}

private val Context.stockUiDataStore: DataStore<Preferences> by preferencesDataStore(name = "stock_ui_prefs")

class StockPreferences(private val context: Context?) {

    private val fallbackGridState = MutableStateFlow(false)
    private val fallbackPhotoStorageMode = MutableStateFlow(PhotoStorageMode.SYNC)
    private val fallbackOwnerPins = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())

    val isStockGridView: Flow<Boolean> = if (context != null) {
        context.applicationContext.stockUiDataStore.data
            .catch { exception ->
                if (exception is IOException || exception is IllegalStateException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { prefs -> prefs[KEY_STOCK_GRID_VIEW] ?: false }
    } else {
        fallbackGridState
    }

    val photoStorageMode: Flow<PhotoStorageMode> = if (context != null) {
        context.applicationContext.stockUiDataStore.data
            .catch { exception ->
                if (exception is IOException || exception is IllegalStateException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { prefs -> PhotoStorageMode.fromPrefValue(prefs[KEY_PHOTO_STORAGE_MODE]) }
    } else {
        fallbackPhotoStorageMode
    }

    fun observeHasOwnerPin(storeId: String): Flow<Boolean> {
        val cleanStoreId = storeId.trim().ifBlank { "default" }
        return if (context != null) {
            context.applicationContext.stockUiDataStore.data
                .catch { exception ->
                    if (exception is IOException || exception is IllegalStateException) {
                        emit(emptyPreferences())
                    } else {
                        throw exception
                    }
                }
                .map { prefs ->
                    !prefs[pinHashKey(cleanStoreId)].isNullOrBlank()
                }
        } else {
            fallbackOwnerPins.map { it.containsKey(cleanStoreId) }
        }
    }

    suspend fun hasOwnerPin(storeId: String): Boolean {
        return observeHasOwnerPin(storeId).first()
    }

    suspend fun setOwnerPin(storeId: String, pin: String) {
        val cleanStoreId = storeId.trim().ifBlank { "default" }
        val cleanPin = pin.trim()
        require(isValidPinFormat(cleanPin)) { "PIN must be 4 to 6 digits." }

        val salt = generateSalt()
        val hash = hashPin(cleanPin, salt)

        if (context != null) {
            try {
                context.applicationContext.stockUiDataStore.edit { prefs ->
                    prefs[pinSaltKey(cleanStoreId)] = salt
                    prefs[pinHashKey(cleanStoreId)] = hash
                }
            } catch (_: Exception) {
                fallbackOwnerPins.value = fallbackOwnerPins.value + (cleanStoreId to (salt to hash))
            }
        } else {
            fallbackOwnerPins.value = fallbackOwnerPins.value + (cleanStoreId to (salt to hash))
        }
    }

    suspend fun verifyOwnerPin(storeId: String, pin: String): Boolean {
        val cleanStoreId = storeId.trim().ifBlank { "default" }
        val cleanPin = pin.trim()
        if (!isValidPinFormat(cleanPin)) return false

        val storedPair: Pair<String, String>? = if (context != null) {
            try {
                val prefs = context.applicationContext.stockUiDataStore.data.first()
                val salt = prefs[pinSaltKey(cleanStoreId)]
                val hash = prefs[pinHashKey(cleanStoreId)]
                if (!salt.isNullOrBlank() && !hash.isNullOrBlank()) {
                    salt to hash
                } else {
                    fallbackOwnerPins.value[cleanStoreId]
                }
            } catch (_: Exception) {
                fallbackOwnerPins.value[cleanStoreId]
            }
        } else {
            fallbackOwnerPins.value[cleanStoreId]
        }

        val (salt, expectedHash) = storedPair ?: return false
        val actualHash = hashPin(cleanPin, salt)
        return constantTimeEquals(actualHash, expectedHash)
    }

    suspend fun changeOwnerPin(storeId: String, currentPin: String, newPin: String): Boolean {
        if (!verifyOwnerPin(storeId, currentPin)) return false
        setOwnerPin(storeId, newPin)
        return true
    }

    suspend fun setStockGridView(isGrid: Boolean) {
        if (context != null) {
            try {
                context.applicationContext.stockUiDataStore.edit { prefs ->
                    prefs[KEY_STOCK_GRID_VIEW] = isGrid
                }
            } catch (_: Exception) {
                fallbackGridState.value = isGrid
            }
        } else {
            fallbackGridState.value = isGrid
        }
    }

    suspend fun setPhotoStorageMode(mode: PhotoStorageMode) {
        if (context != null) {
            try {
                context.applicationContext.stockUiDataStore.edit { prefs ->
                    prefs[KEY_PHOTO_STORAGE_MODE] = mode.prefValue
                }
            } catch (_: Exception) {
                fallbackPhotoStorageMode.value = mode
            }
        } else {
            fallbackPhotoStorageMode.value = mode
        }
    }

    companion object {
        val KEY_STOCK_GRID_VIEW = booleanPreferencesKey("stock_is_grid_view")
        val KEY_PHOTO_STORAGE_MODE = stringPreferencesKey("product_photo_storage_mode")

        fun isValidPinFormat(pin: String): Boolean {
            val trimmed = pin.trim()
            return trimmed.length in 4..6 && trimmed.all { it.isDigit() }
        }

        private fun pinHashKey(storeId: String): Preferences.Key<String> =
            stringPreferencesKey("owner_pin_hash_${storeId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}")

        private fun pinSaltKey(storeId: String): Preferences.Key<String> =
            stringPreferencesKey("owner_pin_salt_${storeId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}")

        private fun generateSalt(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        }

        private fun hashPin(pin: String, salt: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt.toByteArray(StandardCharsets.UTF_8))
            digest.update(":".toByteArray(StandardCharsets.UTF_8))
            digest.update(pin.toByteArray(StandardCharsets.UTF_8))
            return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
        }

        private fun constantTimeEquals(a: String, b: String): Boolean {
            if (a.length != b.length) return false
            var diff = 0
            for (i in a.indices) {
                diff = diff or (a[i].code xor b[i].code)
            }
            return diff == 0
        }
    }
}
