package dev.tomerklein.dradis.mqtt

import android.util.Log
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.TrustManagerFactory

private const val TAG = "dradis.tls"

/** Builds TLS trust material from user-supplied PEM certificates. */
object TlsCerts {

    /**
     * Build a [TrustManagerFactory] that trusts the given PEM CA certificate(s),
     * or null if [caPem] is blank (caller should fall back to the system trust
     * store) or the PEM can't be parsed.
     */
    fun trustManagerFactory(caPem: String): TrustManagerFactory? {
        if (caPem.isBlank()) return null
        return runCatching {
            val certs = CertificateFactory.getInstance("X.509")
                .generateCertificates(caPem.byteInputStream())
            require(certs.isNotEmpty()) { "no certificates in PEM" }
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                certs.forEachIndexed { i, cert -> setCertificateEntry("ca$i", cert) }
            }
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore) }
        }.onFailure { Log.e(TAG, "Failed to load CA certificate", it) }.getOrNull()
    }
}
