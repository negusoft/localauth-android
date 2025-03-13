package com.negusoft.localauth.serialization

import com.negusoft.localauth.crypto.Keys
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.security.PublicKey

@Serializable
@SerialName("PublicKey")
private class PublicKeySurrogate(
    val encoded: ByteArray,
    val format: String?,
    val algorithm: String?
)

class PublicKeyX509Serializer : KSerializer<PublicKey> {

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor = SerialDescriptor("com.negusoft.localauth.PublicKey", PublicKeySurrogate.serializer().descriptor)

    override fun serialize(encoder: Encoder, value: PublicKey) {
        val surrogate = PublicKeySurrogate(value.encoded, value.format, value.algorithm)
        encoder.encodeSerializableValue(PublicKeySurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): PublicKey {
        val surrogate = decoder.decodeSerializableValue(PublicKeySurrogate.serializer())
        return Keys.RSA.decodePublicKey(surrogate.encoded, surrogate.algorithm) ?: error("Failed to decode public key")
    }
}