package snowblossom.lib.tls;

import java.security.Provider;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;

public class SnowTrustManagerFactory extends TrustManagerFactory
{
  public SnowTrustManagerFactory(TrustManagerFactorySpi spi, Provider provider, String algo)
  {
    super(spi, provider, algo);
  }
}
