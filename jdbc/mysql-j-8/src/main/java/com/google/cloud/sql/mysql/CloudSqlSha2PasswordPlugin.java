package com.google.cloud.sql.mysql;

import com.mysql.cj.Messages;
import com.mysql.cj.conf.PropertyKey;
import com.mysql.cj.exceptions.CJException;
import com.mysql.cj.exceptions.ExceptionFactory;
import com.mysql.cj.exceptions.UnableToConnectException;
import com.mysql.cj.protocol.Security;
import com.mysql.cj.protocol.a.NativeConstants;
import com.mysql.cj.protocol.a.NativeConstants.StringLengthDataType;
import com.mysql.cj.protocol.a.NativeConstants.StringSelfDataType;
import com.mysql.cj.protocol.a.NativePacketPayload;
import com.mysql.cj.protocol.a.authentication.CachingSha2PasswordPlugin;
import com.mysql.cj.util.StringUtils;
import java.security.DigestException;
import java.util.List;

public class CloudSqlSha2PasswordPlugin extends CachingSha2PasswordPlugin {
  public static String PLUGIN_NAME = "cloudsql_sha256_password";

  public static String getPluginName() {
    return PLUGIN_NAME;
  }

  @Override
  public String getProtocolPluginName() {
    return CachingSha2PasswordPlugin.PLUGIN_NAME;
  }

  private enum AuthStage {
    FAST_AUTH_SEND_SCRAMBLE,
    FAST_AUTH_READ_RESULT,
    FAST_AUTH_COMPLETE,
    FULL_AUTH
  }

  private AuthStage stage = AuthStage.FAST_AUTH_SEND_SCRAMBLE;

  @Override
  public boolean nextAuthenticationStep(
      NativePacketPayload fromServer, List<NativePacketPayload> toServer) {
    toServer.clear();

    if (this.password == null || this.password.length() == 0 || fromServer == null) {
      // no password
      NativePacketPayload packet = new NativePacketPayload(new byte[] {0});
      toServer.add(packet);

    } else {
      try {
        if (this.stage == AuthStage.FAST_AUTH_SEND_SCRAMBLE) {
          // send a scramble for fast auth
          this.seed = fromServer.readString(StringSelfDataType.STRING_TERM, null);
          toServer.add(
              new NativePacketPayload(
                  Security.scrambleCachingSha2(
                      StringUtils.getBytes(
                          this.password,
                          this.protocol
                              .getServerSession()
                              .getCharsetSettings()
                              .getPasswordCharacterEncoding()),
                      this.seed.getBytes())));
          this.stage = AuthStage.FAST_AUTH_READ_RESULT;
          return true;

        } else if (this.stage == AuthStage.FAST_AUTH_READ_RESULT) {
          int fastAuthResult = fromServer.readBytes(StringLengthDataType.STRING_FIXED, 1)[0];
          switch (fastAuthResult) {
            case 3:
              this.stage = AuthStage.FAST_AUTH_COMPLETE;
              return true;
            case 4:
              this.stage = AuthStage.FULL_AUTH;
              break;
            default:
              throw ExceptionFactory.createException(
                  "Unknown server response after fast auth.",
                  this.protocol.getExceptionInterceptor());
          }
        }

        if (this.serverRSAPublicKeyFile.getValue() != null) {
          // encrypt with given key, don't use "Public Key Retrieval"
          NativePacketPayload packet = new NativePacketPayload(encryptPassword());
          toServer.add(packet);

        } else {
          if (!this.protocol
              .getPropertySet()
              .getBooleanProperty(PropertyKey.allowPublicKeyRetrieval)
              .getValue()) {
            throw ExceptionFactory.createException(
                UnableToConnectException.class,
                Messages.getString("Sha256PasswordPlugin.2"),
                this.protocol.getExceptionInterceptor());
          }

          // We must request the public key from the server to encrypt the password
          if (this.publicKeyRequested
              && fromServer.getPayloadLength()
                  > NativeConstants.SEED_LENGTH + 1) { // auth data is null terminated
            // Servers affected by Bug#70865 could send Auth Switch instead of key after Public Key
            // Retrieval,
            // so we check payload length to detect that.

            // read key response
            this.publicKeyString = fromServer.readString(StringSelfDataType.STRING_TERM, null);
            NativePacketPayload packet = new NativePacketPayload(encryptPassword());
            toServer.add(packet);
            this.publicKeyRequested = false;
          } else {
            // build and send Public Key Retrieval packet
            NativePacketPayload packet =
                new NativePacketPayload(new byte[] {2}); // was 1 in sha256_password
            toServer.add(packet);
            this.publicKeyRequested = true;
          }
        }
      } catch (CJException | DigestException e) {
        throw ExceptionFactory.createException(
            e.getMessage(), e, this.protocol.getExceptionInterceptor());
      }
    }

    return true;
  }
}
