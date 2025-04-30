# LocalAuth Android

Protect critical user data by defining authentication methods to access it, such as a password or biometric verification.


## Motivation

Apps commonly use an online login mechanism using, for example, a username and a password. It is common practice to receive a 'refresh token' on success and store it. Or even to store the username/password credentials directly (although not advised 🫣).

This is very sensitive information, that would give access to your account in case it was compromised. We want to store it securely by encrypting it with the KeyStore and additionally, we may want to add additional protections, such as a password or biometric verification.

LocalAuth makes it easy to set up such flows, ensuring security best practices.


## Core Concepts

LocalAuth is built on top of the LocalVault. You can see it as mailbox, anybody can put letters in, but only the owner can open it to see the content. For this purpose, when we create a LocalVault, we will set up a Lock. We can open the LocalVault if we have a key to the Lock.

In practice, you will not interact directly with the LocalVault. LocalAuthenticator encapsulates LocalVault, making it easier to work with and expanding on it's capabilities. This is why we will focus on LocalAuthenticator for the rest of the document. If you want to dive deeper on the technical details, you can refer to "Technical Details" section.

Typically, an app will have one instance of LocalAuthenticator. On successful login (ex. with username & password), you will initialize the authenticator, set up the locks, store the required information and save the authencticator.

The next time the app is launched, you restore the authenticator and unlock it using one of the locks in order to read the secret data. You can now use this data (ex. the _refresh token_) to sign the user in.


## Setup

In your gradle build file, add the [Maven Central][1] dependency:
 - Kotlin
```groovy
implementation("com.negusoft.localauth:localauth:0.8.0")
```
 - Groovy
```groovy
implementation 'com.negusoft.localauth:localauth:0.8.0'
```

## Locks

There are three Lock types out of the box:
- **PasswordLock**: Uses the KeyStore to encrypt the data, on top of that, it uses the provided password to re-encrypt the data.
- **BiometicLock**: Uses the KeyStore to encrypt the data with biometric protection. The biometric check is required to decrypt the data.
- **SimpleLock**: Uses the KeyStore to encrypt the data. It only needs the device to be unlocked to access the data.

In all the cases, only the app that created the locks can access the data.


## LocalAuthenticator

LocaAuthenticator takes care of protecting and storing the user data. All the data is stored in binary format (ByteArray), but there are some extensions available from to conver to/from common types.

Let's look at the operations that LocaAuthenticator provides:

### Initialize

First you need to initialize the authenticator and register one or more locks:

```kotlin
val authenticator: LocalAuthenticator = LocalAuthenticator.create()
authenticator.initialize {
    registerPasswordLock(lockId = "password_lock", Password("11111"))
}
```
Note that we provide an identifier to each lock so we can reference them later.

### Write properties

You can now store values in the authenticator. Three kinds are available:
- **secret**: The main secret. No identifier needs to be specified.
```kotlin
authenticator.updateSecret("secret_token".toByteArray())
```
- **secret properties**: A set of secrets referenced by a string indentifier.
```kotlin
authenticator.updateSecretProperty("the_answer", "42".toByteArray())
```
- **public properties**: A set of values referenced by a string indentifier. They can be read without unlocking the authenticator.
```kotlin
authenticator.updatePublicProperty("user_name", "Philip J. Fry".toByteArray())
authenticator.publicProperty("user_name")
```
### Read properties

In order to read the secret values, you first need to authenticate using one of the registered locks:
```kotlin
val session = authenticator.authenticateWithPasswordLock("password_lock", Password("11111"))
val secretToken = session.secret()
val theAnswer = session.secretProperty("the_answer")
```
It may be more convenient to use the 'authenticated' method for reading a single value:
```kotlin
val session = authenticator.authenticatedWithPasswordLock("password_lock", Password("11111")) {
    secret()
}
```

### Edit the authenticator

Same as for reading values, if you need to add or modify a lock, you need to authenticate first, then edit:
```kotlin
authenticator.authenticatedWithPasswordLock("password_lock", Password("11111")) {
    edit {
        registerBiometricLock("biometric_lock")
    }
}
```
You can override a lock to modify it. For instance, you can modify the password by registering a lock with the new password:
```kotlin
authenticator.authenticatedWithPasswordLock("password_lock", Password("11111")) {
    edit {
        registerPasswordLock("password", Password("22222"))
    }
}
```

### Persistence

Once we have our authenticator, we want to save it and restore it on the next app launch. LocalAuth doesn't provide a storage strategy, it is up to you to define where to save it.

However, there are two ways to serialize the data available:
- **KotlinX Serialization**: LocalAuthenticator implements @Serializable, so we could convert it to a JSon string and store it in our database.
 ```kotlin
val authenticatorJson = Json.encodeToString(authenticator)
val authenticatorRestored = Json.decodeFromString<LocalAuthenticator>(authenticatorJson)
```
- **Encode/decode**: LocalAuthenticator can be encoded to a ByteArray and store that in stead:
 ```kotlin
val authenticatorBytes = authenticator.encode()
val authenticatorRestored = LocalAuthenticator.restore(authenticatorBytes)
```


# Technical Details

LocalAuth is based on well established cryptographic best practices, but it makes it accessible by hiding the complexity. We can now dive deeper in the implementation details.

## LocalVault

At the very core, we have the LocalVault. It uses public-key cryptography to protect the data. A LocalVault is associated with a key-pair, that is, a private key and the associated public key.

The LocalVault only holds a reference to the public key. This way, following the public cryptography principles, we can use this public key to encrypt data. As such, anybody can encrypt the data.

However, in order to decrypt the data, we require the private key. For this, we need to 'open' the vault and then use the OpenVault's private key to decrypt the data.

So now, the security relies on the capacity to savely store the private key. This is where the lock mechanism comes in.

## Locks

A lock is basically a way of protecting the private key. When registering, the lock receives the private key bytes and returns a Token representing the protected private key. When using the lock to open the vault, the private key will be restored from the Token.

For example, a given lock implementation might use the password to encrypt the private key and return that as the Token. Then use the token and a password to restore the private key and open the vault:
```kotlin
// Register
val privateKeyEncrypted = openVault.registerLock { privateKey ->
      encryptWithPassword(privateKey, "12345")
}
```
```kotlin
// Open
val openVault = vault.open {
   decryptWithPassword(privateKeyEncrypted, "12345")
}
```

A dumb implementation would be to return the private key as-is. But that would be unsafe, as somebody getting access to the Token could easily open the vault and access the encrypted data.

The provided locks use the KeyStore to encrypt the private key. Some provide additional checks such as a password or biometric verification.

LocalAuthenticator and LocalVault are agnostic of lock implementations. As already mentioned, we can create our Lock implementaions.

## LocalAuthenticator

LocalAuthenticator encapsulates the LocalVault to make it easier to use. It adds property management and keeps track of the registered locks.

Most users will interact with LocalAuthenticator and don't need to know about the implementation details, but if you got to this point, you are not most people, so thanks for reading.


License
--------

    Copyright 2024 Negusoft

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


[1]: https://central.sonatype.com/artifact/com.negusoft.localauth/localauth/overview