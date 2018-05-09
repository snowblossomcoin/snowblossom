package snowblossom;

import java.security.MessageDigest;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import snowblossom.proto.AddressSpec;
import snowblossom.proto.SigSpec;


public class AddressUtil
{
  public static AddressSpecHash getHashForSpec(AddressSpec spec)
  {
    return getHashForSpec(spec, DigestUtil.getMDAddressSpec());
  }

  public static AddressSpecHash getHashForSpec(AddressSpec spec, MessageDigest md)
  {
    try
    {
      ByteArrayOutputStream byte_out = new ByteArrayOutputStream(200);
      DataOutputStream data_out = new DataOutputStream(byte_out);

      data_out.writeInt(spec.getRequiredSigners());
      data_out.writeInt(spec.getSigSpecsCount());
      for(SigSpec sig_spec : spec.getSigSpecsList())
      {
        data_out.writeInt(sig_spec.getSignatureType());
        data_out.writeInt(sig_spec.getPublicKey().size());
        data_out.write(sig_spec.getPublicKey().toByteArray());
      }
      data_out.flush();
      md.update(byte_out.toByteArray());

      return new AddressSpecHash(md.digest());
    }
    catch(java.io.IOException e)
    {
      throw new RuntimeException(e);
    }

  }

  public static String getAddressString(String human, AddressSpecHash hash)
  {
    return Duck32.encode(human, hash.getBytes());
  }

  public static AddressSpecHash getHashForAddress(String human, String address)
    throws ValidationException
  {
    return new AddressSpecHash( Duck32.decode(human, address) );
  }

}
