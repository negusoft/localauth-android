package com.negusoft.localauth.vault

import com.negusoft.localauth.persistence.DataStore
import com.negusoft.localauth.vault.LocalVault.OpenVault

/** Create and save a new Vault. */
fun LocalVault.Companion.create(dataStore: DataStore, key: String) = create().also {
    dataStore.set(key, it.vault.encode())
}

/** Create and save a new Vault. */
fun LocalVault.Companion.create(dataStore: DataStore, key: String, config: (OpenVault) -> Unit) = create(config).also {
    dataStore.set(key, it.encode())
}

/** Restore from DataStore. */
fun LocalVault.Companion.restore(dataStore: DataStore, key: String): LocalVault {
    val encoded = dataStore.get(key) ?: throw LocalVaultException("Vault not found in DataStore (key='$key')")
    return restore(encoded)
}

/** Get the decrypted value from the DataStore. */
fun OpenVault.getDecrypted(dataStore: DataStore, key: String): ByteArray {
    val datastoreValue = dataStore.get(key) ?: throw LocalVaultException("Value not found in DataStore (key='$key')")
    val encryptedValue = EncryptedValue.decode(datastoreValue)
    return decrypt(encryptedValue)
}

/** Set the encrypted value in the DataStore. */
fun LocalVault.setEncrypted(value: ByteArray, dataStore: DataStore, key: String) {
    val encryptedData = encrypt(value)
    val value = encryptedData.encode()
    dataStore.set(key, value)
}