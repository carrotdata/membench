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

import java.io.IOException;

public interface Benchmark {

  /**
   * Get up to
   * @param n total number of records
   * @return array of records (may be less than n in size)
   * @throws IOException 
   */
  public String[] getDataRecords(int n) throws IOException;
  
  /**
   * Get benchmark name
   * @return
   */
  public String getName();
  
  /**
   * Get benchmark description
   * @return 
   */
  public default String getDescription() {
    return null;
  }
  
  /**
   * Get average record size
   * @return
   */
  public double getAvgRecordSize();
}
