package com.html2apk.builder

import com.android.apksig.ApkSigner
import sun.security.x509.AlgorithmId
import sun.security.x509.CertificateAlgorithmId
import sun.security.x509.CertificateIssuerName
import sun.security.x509.CertificateSerialNumber
import sun.security.x509.CertificateSubjectName
import sun.security.x509.CertificateValidity
import sun.security.x509.CertificateVersion
import sun.security.x509.CertificateX509Key
import sun.security.x509.X500Name
import sun.security.x509.X509CertImpl
import sun.security.x509.X509CertInfo
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Helper signing Level 3.
 *
 * Menggunakan `com.android.apksig` (library resmi Android build tools) untuk
 * menandatangani APK, dan `sun.security.x509` (tersedia di runtime Android)
 * untuk membuat sertifikat self-signed tanpa library pihak ketiga.
 *
 * Catatan: APK hasil signing dengan kunci debug ini dapat di-install,
 * tetapi TIDAK layak untuk distribusi produksi.
 */
object SignerHelper {

    private const val KEYSTORE_PASSWORD = "password"
    private const val KEY_ALIAS = "html2apk"
    private const val SIGNER_NAME = "HTML2APK"
    private const val KEY_SIZE = 2048

    /**
     * Membuat file PKCS12 keystore berisi keypair RSA 2048 dan sertifikat
     * self-signed. Jika file sudah ada, tidak ditimpa.
     */
    fun generateKeystore(keystoreFile: File) {
        if (keystoreFile.exists()) return
        keystoreFile.parentFile?.mkdirs()

        // 1. Generate keypair RSA.
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(KEY_SIZE)
        val keyPair = keyPairGenerator.generateKeyPair()

        // 2. Bangun sertifikat self-signed memakai sun.security.x509.
        val cert = createSelfSignedCertificate(
            privateKey = keyPair.private,
            publicKey = keyPair.public,
            subject = "CN=HTML2APK Builder, O=HTML2APK, C=ID"
        )

        // 3. Simpan ke PKCS12 keystore.
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry(
            KEY_ALIAS,
            keyPair.private,
            KEYSTORE_PASSWORD.toCharArray(),
            arrayOf(cert)
        )
        keystoreFile.outputStream().use { output ->
            keyStore.store(output, KEYSTORE_PASSWORD.toCharArray())
        }
    }

    /**
     * Menandatangani [inputApk] dan menulis hasilnya ke [outputApk].
     * Skema v1, v2, dan v3 diaktifkan agar kompatibel dengan minSdk 26+.
     */
    fun signApk(inputApk: File, outputApk: File, keystoreFile: File) {
        require(inputApk.exists()) { "APK input tidak ditemukan: ${inputApk.absolutePath}" }
        require(keystoreFile.exists()) {
            "Keystore tidak ditemukan: ${keystoreFile.absolutePath}"
        }

        if (outputApk.exists() && !outputApk.delete()) {
            throw IllegalStateException("Gagal menghapus output lama: ${outputApk.absolutePath}")
        }
        outputApk.parentFile?.mkdirs()

        // 1. Load private key + certificate chain dari keystore.
        val keyStore = KeyStore.getInstance("PKCS12")
        keystoreFile.inputStream().use { input ->
            keyStore.load(input, KEYSTORE_PASSWORD.toCharArray())
        }

        val privateKey = keyStore.getKey(
            KEY_ALIAS,
            KEYSTORE_PASSWORD.toCharArray()
        ) as PrivateKey

        val certificateChain = keyStore.getCertificateChain(KEY_ALIAS)
            ?.map { it as X509Certificate }
            ?: throw IllegalStateException("Certificate chain kosong di keystore")

        // 2. Bangun SignerConfig.
        val signerConfig = ApkSigner.SignerConfig.Builder(
            SIGNER_NAME,
            privateKey,
            certificateChain
        ).build()

        // 3. Sign APK.
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }

    /**
     * Membuat sertifikat X.509 self-signed dari keypair RSA.
     * Memakai API internal JDK yang tersedia di Android (`sun.security.x509`).
     */
    private fun createSelfSignedCertificate(
        privateKey: PrivateKey,
        publicKey: java.security.PublicKey,
        subject: String
    ): X509Certificate {
        val dn = X500Name(subject)
        val serial = BigInteger.valueOf(System.currentTimeMillis())
        val notBefore = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        val notAfter = Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365 * 10))

        val certInfo = X509CertInfo()
        certInfo.set(X509CertInfo.VERSION, CertificateVersion(CertificateVersion.V3))
        certInfo.set(X509CertInfo.SERIAL_NUMBER, CertificateSerialNumber(serial))
        certInfo.set(X509CertInfo.SUBJECT, CertificateSubjectName(dn))
        certInfo.set(X509CertInfo.ISSUER, CertificateIssuerName(dn))
        certInfo.set(X509CertInfo.VALIDITY, CertificateValidity(notBefore, notAfter))
        certInfo.set(X509CertInfo.KEY, CertificateX509Key(publicKey))

        val algorithmId = AlgorithmId.get("SHA256withRSA")
        certInfo.set(
            X509CertInfo.ALGORITHM_ID,
            CertificateAlgorithmId(algorithmId)
        )

        val cert = X509CertImpl(certInfo)
        cert.sign(privateKey, "SHA256withRSA")
        return cert
    }
}