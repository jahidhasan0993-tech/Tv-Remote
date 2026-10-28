package com.tvremote.free.adb

import android.content.Context
import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * Generates / loads a 2048-bit RSA keypair used to authenticate with the
 * ADB daemon, in exactly the format `adb` itself uses (the same
 * `adbkey` / `adbkey.pub` scheme), and encodes the public key the way
 * Android's adb_auth_host.c does so the device can show the normal
 * "Allow this computer?" dialog.
 */
class AdbCrypto private constructor(private val keyPair: KeyPair) {

    private val privateKey get() = keyPair.private as RSAPrivateKey
    private val publicKey get() = keyPair.public as RSAPublicKey

    /** Raw RSA signature (no hash OID) of the 20-byte token the device sends. */
    fun signToken(token: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        return cipher.doFinal(token)
    }

    /** Builds the ADB-specific public key blob (see android_pubkey_encode in AOSP). */
    fun getAdbPublicKeyPayload(): ByteArray {
        val modulus = publicKey.modulus
        val exponent = publicKey.publicExponent

        val wordBits = 32
        val numWords = 64            // 2048-bit modulus / 32
        val numBytes = numWords * 4  // 256

        val b32 = BigInteger.ONE.shiftLeft(wordBits)
        val n0 = modulus.mod(b32)
        val n0inv = b32.subtract(n0.modInverse(b32)).mod(b32)

        val r = BigInteger.ONE.shiftLeft(wordBits * numWords) // 2^2048
        val rr = r.multiply(r).mod(modulus)                   // 2^4096 mod n

        val out = java.io.ByteArrayOutputStream()
        out.write(leUint32(numWords))
        out.write(leUint32(n0inv.toLong()))
        out.write(bigIntToLeBytes(modulus, numBytes))
        out.write(bigIntToLeBytes(rr, numBytes))
        out.write(leUint32(exponent.toLong()))

        val struct = out.toByteArray()
        val b64 = Base64.encodeToString(struct, Base64.NO_WRAP)
        return "$b64 tvremote@free\u0000".toByteArray(Charsets.UTF_8)
    }

    private fun leUint32(value: Long): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun bigIntToLeBytes(value: BigInteger, size: Int): ByteArray {
        var be = value.toByteArray()
        // strip a leading zero sign byte BigInteger may add
        if (be.size > size && be[0].toInt() == 0) {
            be = be.copyOfRange(1, be.size)
        }
        val padded = ByteArray(size)
        val offset = size - be.size
        System.arraycopy(be, 0, padded, offset, be.size)
        padded.reverse() // big-endian -> little-endian
        return padded
    }

    companion object {
        private const val PRIV_FILE = "tvremote_adb_key.priv"
        private const val PUB_FILE = "tvremote_adb_key.pub"

        /** Loads the saved keypair, or generates and persists a new one on first run. */
        fun loadOrGenerate(context: Context): AdbCrypto {
            val privFile = File(context.filesDir, PRIV_FILE)
            val pubFile = File(context.filesDir, PUB_FILE)

            if (privFile.exists() && pubFile.exists()) {
                val kf = KeyFactory.getInstance("RSA")
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(privFile.readBytes()))
                val pub = kf.generatePublic(X509EncodedKeySpec(pubFile.readBytes()))
                return AdbCrypto(KeyPair(pub, priv))
            }

            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            val pair = generator.generateKeyPair()

            privFile.writeBytes(pair.private.encoded)
            pubFile.writeBytes(pair.public.encoded)

            return AdbCrypto(pair)
        }
    }
}
