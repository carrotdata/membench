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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GithubBenchmark extends BenchmarkBase {

  private static String path = "./data/github";
  
  public GithubBenchmark() {
    super(path);
    this.name = "github";
  }
  
  protected GithubBenchmark(String path) {
    super(path);
  }

  @Override
  public String[] getDataRecords(int n) throws IOException {
    File dir = new File(path);
    String[] files = dir.list(new FilenameFilter() {

      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith("json");
      }
      
    });
    long total = 0;
    String[] result = new String[files.length];
    for (int i = 0; i < files.length; i++) {
      Path p = Path.of(files[i]);
      p = Path.of(path + File.separator + p.toString());
      result[i]= Files.readString(p);
      total += result[i].length();
    }
    this.avgRecordSize = total / result.length;
    return result;
  }
  
}
