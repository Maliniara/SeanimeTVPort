package com.malina.seanimetvport.data.anilist

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator

class TokenEncryptionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun cipher(): TokenCipher {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return TokenCipher { key }
    }

    // Each encryption uses a fresh IV, while preserving the token after decryption.
    @Test
    fun encrypt_sameToken_producesDifferentCiphertexts() {
        val cipher = cipher()
        val first = cipher.encrypt("private-token")
        val second = cipher.encrypt("private-token")
        assertNotEquals(first, second)
        assertFalse(first.contains("private-token"))
        assertEquals("private-token", cipher.decrypt(first))
        assertEquals("private-token", cipher.decrypt(second))
    }

    // GCM rejects a modified payload instead of returning corrupted credentials.
    @Test
    fun decrypt_modifiedCiphertext_failsAuthentication() {
        val cipher = cipher()
        val encrypted = cipher.encrypt("private-token")
        val prefix = encrypted.substringBeforeLast(':') + ":"
        val bytes = Base64.getDecoder().decode(encrypted.substringAfterLast(':'))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) {
            cipher.decrypt(prefix + Base64.getEncoder().encodeToString(bytes))
        }
    }

    // Data encrypted for one installation cannot be decrypted using another key.
    @Test
    fun decrypt_wrongKey_failsAuthentication() {
        val encrypted = cipher().encrypt("private-token")
        assertThrows(AEADBadTagException::class.java) { cipher().decrypt(encrypted) }
    }

    // Plaintext is accepted only by the migration, never by normal reads.
    @Test
    fun decrypt_plaintextOrTruncatedPayload_isRejected() {
        val cipher = cipher()
        assertThrows(IllegalArgumentException::class.java) { cipher.decrypt("private-token") }
        assertThrows(IllegalArgumentException::class.java) { cipher.decrypt("keystore:v1:AA==") }
    }

    // Migration protects both the mirrored active token and every account's JSON token.
    @Test
    fun migration_encryptsAllCredentialFields_withoutChangingOtherPreferences() = runTest {
        val cipher = cipher()
        val tokenKey = stringPreferencesKey("active_token")
        val accountsKey = stringPreferencesKey("accounts_json")
        val unrelatedKey = stringPreferencesKey("other")
        val accounts = """[{"id":1,"token":"first"},{"id":2,"token":"second"}]"""
        val original = preferencesOf(tokenKey to "first", accountsKey to accounts, unrelatedKey to "keep")
        val migration = TokenPreferencesMigration(listOf(tokenKey, accountsKey), cipher)
        assertTrue(migration.shouldMigrate(original))
        val encrypted = migration.migrate(original)
        assertEquals("first", cipher.decrypt(encrypted[tokenKey]!!))
        assertEquals(accounts, cipher.decrypt(encrypted[accountsKey]!!))
        assertEquals("keep", encrypted[unrelatedKey])
        assertFalse(migration.shouldMigrate(encrypted))
        assertEquals(encrypted, migration.migrate(encrypted))
    }

    // A partially migrated store preserves fields that were already encrypted.
    @Test
    fun migration_mixedStore_preservesExistingCiphertext() = runTest {
        val cipher = cipher()
        val active = stringPreferencesKey("active_token")
        val accounts = stringPreferencesKey("accounts_json")
        val encrypted = cipher.encrypt("active")
        val migration = TokenPreferencesMigration(listOf(active, accounts), cipher)
        val result = migration.migrate(preferencesOf(active to encrypted, accounts to "[]"))
        assertEquals(encrypted, result[active])
        assertEquals("[]", cipher.decrypt(result[accounts]!!))
        assertFalse(migration.shouldMigrate(emptyPreferences()))
    }

    // Legacy data is cleared only by cleanup, after the destination has been committed.
    @Test
    fun legacyMigration_keepsSourceUntilCleanup_andEncryptsDestination() = runTest {
        val cipher = cipher()
        val legacy = FakeAuthRepository(initial = "legacy-token")
        val active = stringPreferencesKey("active_token")
        val migration = LegacyTokenMigration(legacy, active, cipher)
        assertTrue(migration.shouldMigrate(emptyPreferences()))
        val result = migration.migrate(emptyPreferences())
        assertEquals("legacy-token", cipher.decrypt(result[active]!!))
        assertEquals("legacy-token", legacy.getToken())
        migration.cleanUp()
        assertNull(legacy.getToken())
        assertFalse(migration.shouldMigrate(result))
    }

    // Importing a stale legacy token must not replace a currently selected account.
    @Test
    fun legacyMigration_existingAccount_winsOverStaleLegacyToken() = runTest {
        val cipher = cipher()
        val active = stringPreferencesKey("active_token")
        val encrypted = cipher.encrypt("current-token")
        val legacy = FakeAuthRepository(initial = "stale-token")
        val migration = LegacyTokenMigration(legacy, active, cipher)
        val result = migration.migrate(preferencesOf(active to encrypted))
        assertEquals(encrypted, result[active])
        migration.cleanUp()
        assertNull(legacy.getToken())
    }

    // A failed encryption must leave the legacy token available for a later retry.
    @Test
    fun datastore_failedMigration_retriesWithoutLosingLegacyToken() = runTest {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        var keyAvailable = false
        val cipher = TokenCipher {
            check(keyAvailable) { "Keystore temporarily unavailable" }
            key
        }
        val legacy = FakeAuthRepository(initial = "legacy-private-token")
        val active = stringPreferencesKey("active_token")
        val file = temporaryFolder.root.resolve("account.preferences_pb")
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            migrations = listOf(LegacyTokenMigration(legacy, active, cipher)),
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { file }
        )
        try {
            var failure: IllegalStateException? = null
            try {
                store.data.first()
            } catch (error: IllegalStateException) {
                failure = error
            }
            assertEquals("Keystore temporarily unavailable", failure?.message)
            assertEquals("legacy-private-token", legacy.getToken())
            keyAvailable = true
            val persisted = store.data.first()[active]!!
            assertEquals("legacy-private-token", cipher.decrypt(persisted))
            assertNull(legacy.getToken())
            assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("legacy-private-token"))
        } finally {
            job.cancelAndJoin()
        }
    }
}
