package edu.luc.cs.metrics.defect.density.service
/**
 * Created by shilpika on 3/25/16.
 */

import javax.net.ssl.SSLContext
import spray.io._
import org.apache.camel.util.jsse._


trait MySSLConfig {
  implicit def sslContext: SSLContext = {
    //val keyStoreFile = "/Users/eschow/repo/services/jks/keystore.jks"
    val keyStoreFile = "/home/shilpika/keys/myjks.jks"

    val ksp = new KeyStoreParameters()
    ksp.setResource(keyStoreFile);
    ksp.setPassword("abcdef")

    val kmp = new KeyManagersParameters()
    kmp.setKeyStore(ksp)
    kmp.setKeyPassword("abcdef")

    val scp = new SSLContextParameters()
    scp.setKeyManagers(kmp)

    val context = scp.createSSLContext()

    context
  }

  implicit def sslEngineProvider: ServerSSLEngineProvider = {
    ServerSSLEngineProvider { engine =>
      engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_AES_128_CBC_SHA"))
      engine.setEnabledProtocols(Array("SSLv3", "TLSv1"))
      engine
    }
  }
}