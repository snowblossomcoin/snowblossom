package snowblossom.lib.tls;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.ManagerFactoryParameters;

import java.security.cert.X509Certificate;
import java.net.Socket;
import java.security.KeyStore;

import java.security.cert.CertificateException;
import java.security.Provider;


public class SnowTrustManagerFactory extends TrustManagerFactory
{
  public SnowTrustManagerFactory(TrustManagerFactorySpi spi, Provider provider, String algo)
  {
    super(spi, provider, algo);
  }
}
