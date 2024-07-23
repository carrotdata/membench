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
package com.carrotdata.membench.benchmarks;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TwitterSentimentsBenchmark extends BenchmarkBase {

  private static String path = "./data/twitter_sentiments/twitter_sentiments.csv";
  
  public TwitterSentimentsBenchmark() {
    super(path);
    this.name = "twitter_sentiments";
  }
  
  protected TwitterSentimentsBenchmark(String path) {
    super(path);
  }
  
  @Override
  public String[] getDataRecords(int n) throws IOException {
    
    FileInputStream fis = new FileInputStream(dataFile);
    BufferedInputStream bis = new BufferedInputStream(fis, 1 << 16);
    DataInputStream dis = new DataInputStream(bis);
    String line = null;
    int count = 0;
    long totalSize = 0;
    final String search = "NO_QUERY";
    List<String> list = new ArrayList<String>();
    while ((line = dis.readLine()) != null && ++count < n) {
      int i = line.indexOf(search);
      int idx = line.indexOf(',', i + 10);
      line = line.substring(idx + 1);
      
      list.add(line);
      totalSize += line.length();
      if ((count % 100000) == 0) {
        logger.info("Loaded {} records, last={}", count, line);
      }
    }
    
    avgRecordSize = totalSize / list.size();
    
    String[] arr = new String[list.size()];
    list.toArray(arr);
    dis.close();
    
    return arr;
  }
}
