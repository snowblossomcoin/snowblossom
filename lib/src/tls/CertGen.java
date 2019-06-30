package snowblossom.lib.tls;

import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.cert.Certificate;
import java.security.PrivateKey;
import org.bouncycastle.cert.X509v3CertificateBuilder;

import org.bouncycastle.asn1.x500.X500Name;
import java.math.BigInteger;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import java.util.Date;
import org.bouncycastle.asn1.ASN1Sequence;

import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.bc.BcECContentSignerBuilder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;

import com.google.protobuf.ByteString;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import io.grpc.netty.GrpcSslContexts;
import io.netty.handler.ssl.SslContext;

import snowblossom.proto.WalletKeyPair;
import snowblossom.proto.WalletDatabase;
import snowblossom.proto.AddressSpec;
import snowblossom.lib.KeyUtil;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.Globals;
import snowblossom.proto.SignedMessage;
import snowblossom.proto.SignedMessagePayload;

import java.util.Random;

public class CertGen
{
  public static SslContext getServerSSLContext(WalletDatabase db)
    throws Exception
  {
    if (db.getKeysCount() != 1) throw new RuntimeException("Unexpected number of keys in wallet db");
    if (db.getAddressesCount() != 1) throw new RuntimeException("Unexpected number of addresses in wallet db");
    WalletKeyPair wkp = db.getKeys(0);
    AddressSpec address_spec = db.getAddresses(0);
    
    WalletKeyPair tls_wkp = KeyUtil.generateWalletRSAKey(2048);
    KeyPair tls_pair = KeyUtil.decodeKeypair(tls_wkp);

    X509Certificate cert = generateSelfSignedCert(wkp, tls_wkp, address_spec);
    //System.out.println(cert);

    ByteString pem_cert = pemCodeCert(cert);
    ByteString pem_prv = pemCodeECPrivateKey(tls_pair.getPrivate());

    return GrpcSslContexts.forServer(pem_cert.newInput(), pem_prv.newInput()).build();
  }


  /**
   * @param key_pair Key pair to use to sign the cert inner signed message, the node key
   * @param tls_wkp The temporary key to use just for this cert and TLS sessions
   * @param spec Address for 'key_pair'
   */
  public static X509Certificate generateSelfSignedCert(WalletKeyPair key_pair, WalletKeyPair tls_wkp, AddressSpec spec)
    throws Exception
  {

    AddressSpecHash address_hash = AddressUtil.getHashForSpec(spec);
    String address = AddressUtil.getAddressString(Globals.NODE_ADDRESS_STRING, address_hash);


    byte[] encoded_pub= tls_wkp.getPublicKey().toByteArray();
    SubjectPublicKeyInfo subjectPublicKeyInfo = new SubjectPublicKeyInfo(
      ASN1Sequence.getInstance(encoded_pub));

    String dn=String.format("CN=%s, O=Snowblossom", address);
    X500Name issuer = new X500Name(dn);
    BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
    Date notBefore = new Date(System.currentTimeMillis());
    Date notAfter = new Date(System.currentTimeMillis() + 86400000L * 365L * 10L);
    X500Name subject = issuer;

    X509v3CertificateBuilder cert_builder = new X509v3CertificateBuilder(
      issuer, serial, notBefore, notAfter, subject, subjectPublicKeyInfo);

    //System.out.println(org.bouncycastle.asn1.x509.Extension.subjectAlternativeName);
    ASN1ObjectIdentifier snow_claim_oid = new ASN1ObjectIdentifier("2.5.29.134");

    //System.out.println(spec);

    SignedMessagePayload payload = SignedMessagePayload.newBuilder().setTlsPublicKey(tls_wkp.getPublicKey()).build();
    SignedMessage sm = MsgSigUtil.signMessage(spec, key_pair, payload);

    byte[] sm_data = sm.toByteString().toByteArray();

    cert_builder.addExtension(snow_claim_oid, true, sm_data);

    String algorithm = "SHA256withRSA";

    AsymmetricKeyParameter privateKeyAsymKeyParam = PrivateKeyFactory.createKey(tls_wkp.getPrivateKey().toByteArray());

    AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find(algorithm);
    AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);

    //ContentSigner sigGen = new BcECContentSignerBuilder(sigAlgId, digAlgId).build(privateKeyAsymKeyParam);
    ContentSigner sigGen = new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(privateKeyAsymKeyParam);

    X509CertificateHolder certificateHolder = cert_builder.build(sigGen);

    X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certificateHolder);
    return cert;
  }

  public static ByteString pemCode(byte[] encoded, String type)
  {
    try
    {
      PemObject po = new PemObject(type, encoded);

      ByteArrayOutputStream b_out = new ByteArrayOutputStream();

      PemWriter w = new PemWriter( new OutputStreamWriter(b_out));

      w.writeObject(po);
      w.flush();
      w.close();

      return ByteString.copyFrom(b_out.toByteArray());
    }
    catch(java.io.IOException e)
    {
      throw new RuntimeException(e);
    }

  }
  public static ByteString pemCodeCert(Certificate cert)
    throws java.security.cert.CertificateEncodingException
  {
    return pemCode(cert.getEncoded(), "CERTIFICATE");

  }
  public static ByteString pemCodeECPrivateKey(PrivateKey key)
  {
    return pemCode(key.getEncoded(), "PRIVATE KEY");
  }

}

