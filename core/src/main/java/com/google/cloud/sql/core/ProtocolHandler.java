/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.sql.core;

import com.google.cloud.sql.core.mdx.MetadataExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.net.ssl.SSLSocket;

/** ProtocolHandler manages the sockets for the Metadata Exchange Protocol. */
class ProtocolHandler {
  private static final byte[] SIGNATURE;

  static {
    SIGNATURE = "CSQLMDEX".getBytes(StandardCharsets.UTF_8);
  }

  private final String userAgent;

  ProtocolHandler(String userAgent) {
    this.userAgent = userAgent;
  }

  /**
   * Create a socket wrapper that writes MDX request on the first OutputStream.write() operation,
   * and peek's to read the MDX response on the first InputStream.read() operation.
   *
   * @param socket the socket to wrap
   * @return The wrapped socket
   * @throws IOException if there is an exception.
   */
  MdxSocket connect(SSLSocket socket, String mdxProtocolType) throws IOException {
    return new MdxSocket(this, socket, convertClientProtocolType(mdxProtocolType));
  }

  void sendMdx(
      OutputStream out,
      MetadataExchange.MetadataExchangeRequest.ClientProtocolType mdxClientProtocolType)
      throws IOException {
    MetadataExchange.MetadataExchangeRequest req =
        MetadataExchange.MetadataExchangeRequest.newBuilder()
            .setClientProtocolType(mdxClientProtocolType)
            .setUserAgent(userAgent)
            .build();
    int size = req.getSerializedSize();

    // Write the protocoal header
    out.write(SIGNATURE);
    // Write the uint32 size big-endian
    out.write((size >>> 24) & 0xFF);
    out.write((size >>> 16) & 0xFF);
    out.write((size >>> 8) & 0xFF);
    out.write(size & 0xFF);
    // Write the protobuf
    req.writeTo(out);
    out.flush();
  }

  private static MetadataExchange.MetadataExchangeRequest.ClientProtocolType
      convertClientProtocolType(String mdxClientProtocolType) {
    MetadataExchange.MetadataExchangeRequest.ClientProtocolType clientProtocolType;
    switch (mdxClientProtocolType) {
      case "tcp":
        clientProtocolType = MetadataExchange.MetadataExchangeRequest.ClientProtocolType.TCP;
        break;
      case "tls":
        clientProtocolType = MetadataExchange.MetadataExchangeRequest.ClientProtocolType.TLS;
        break;
      case "uds":
        clientProtocolType = MetadataExchange.MetadataExchangeRequest.ClientProtocolType.UDS;
        break;
      default:
        clientProtocolType =
            MetadataExchange.MetadataExchangeRequest.ClientProtocolType
                .CLIENT_PROTOCOL_TYPE_UNSPECIFIED;
    }
    return clientProtocolType;
  }

  MetadataExchange.MetadataExchangeResponse readMdxResponse(InputStream in) throws IOException {
    // Mark the input stream so we can reset it if the server doesn't speak MDX.
    // 8 bytes is enough for the header (8)
    in.mark(8);

    // Read the 8-byte header.
    byte[] headerBytes = new byte[8];
    int bytesRead = in.read(headerBytes);
    if (bytesRead < 8 || !Arrays.equals(headerBytes, SIGNATURE)) {
      // Not enough bytes for a header, assume it's not an MDX response.
      in.reset();
      return null;
    }

    // Read the 4-byte big-endian size.
    int sizeL = (in.read() << 24) | (in.read() << 16) | (in.read() << 8) | in.read();

    // Read the response bytes.
    byte[] responseBytes = new byte[sizeL];
    int protoBytesRead = 0;
    while (protoBytesRead < sizeL) {
      int newBytes = in.read(responseBytes, protoBytesRead, sizeL - protoBytesRead);
      if (newBytes == -1) {
        throw new IOException(
            "Failed to read MDX response: stream ended before all "
                + sizeL
                + " bytes were read. Only received "
                + protoBytesRead
                + " bytes.");
      }
      protoBytesRead += newBytes;
    }

    return MetadataExchange.MetadataExchangeResponse.parseFrom(responseBytes);
  }
}
