/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.carrotdata.membench;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipCompressor {

    public static byte[] compress(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return data;
        }

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
            gzipOutputStream.write(data);
        }

        return byteArrayOutputStream.toByteArray();
    }

    public static byte[] decompress(byte[] compressedData) throws IOException {
      if (compressedData == null || compressedData.length == 0) {
          return compressedData;
      }

      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedData);
           GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream)) {

          byte[] buffer = new byte[1024];
          int bytesRead;
          while ((bytesRead = gzipInputStream.read(buffer)) != -1) {
              byteArrayOutputStream.write(buffer, 0, bytesRead);
          }
      }

      return byteArrayOutputStream.toByteArray();
  }
    
    public static void main(String[] args) {
        String originalString = "This is a test string to be compressed";
        byte[] originalBytes = originalString.getBytes();

        try {
            byte[] compressedBytes = compress(originalBytes);
            System.out.println("Original size: " + originalBytes.length);
            System.out.println("Compressed size: " + compressedBytes.length);
            byte[] decompressedBytes = decompress(compressedBytes);
            System.out.println("Original string: " + originalString);
            System.out.println("Decompressed string: " + new String(decompressedBytes));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
