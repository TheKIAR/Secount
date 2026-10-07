package com.secount.app.logic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BiometricProofTest {
    @Test
    fun unlockRequiresTheExpectedKeystoreProof() {
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val encryptor = Cipher.getInstance("AES/GCM/NoPadding")
        encryptor.init(Cipher.ENCRYPT_MODE, key)
        val proof = encryptBiometricProof(encryptor)!!

        val decryptor = Cipher.getInstance("AES/GCM/NoPadding")
        decryptor.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, proof.copyOfRange(0, 12)))

        assertTrue(authenticateBiometricProof(decryptor, proof.copyOfRange(12, proof.size)))
    }

    @Test
    fun unlockRejectsTamperedAndMissingProofs() {
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val encryptor = Cipher.getInstance("AES/GCM/NoPadding")
        encryptor.init(Cipher.ENCRYPT_MODE, key)
        val proof = encryptBiometricProof(encryptor)!!
        val decryptor = Cipher.getInstance("AES/GCM/NoPadding")
        decryptor.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, proof.copyOfRange(0, 12)))

        proof[12] = (proof[12].toInt() xor 1).toByte()
        assertFalse(authenticateBiometricProof(decryptor, proof.copyOfRange(12, proof.size)))
        assertFalse(authenticateBiometricProof(null, proof.copyOfRange(12, proof.size)))
        assertFalse(authenticateBiometricProof(decryptor, null))
    }
}
