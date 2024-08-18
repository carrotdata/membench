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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.carrotdata.membench.benchmarks.AirbnbBenchmark;
import com.carrotdata.membench.benchmarks.AmazonProductReviewsBenchmark;
import com.carrotdata.membench.benchmarks.ArxivBenchmark;
import com.carrotdata.membench.benchmarks.Benchmark;
import com.carrotdata.membench.benchmarks.DblpBenchmark;
import com.carrotdata.membench.benchmarks.GithubBenchmark;
import com.carrotdata.membench.benchmarks.OhioStateEmployeeBenchmark;
import com.carrotdata.membench.benchmarks.RedditBenchmark;
import com.carrotdata.membench.benchmarks.SpotifyBenchmark;
import com.carrotdata.membench.benchmarks.TwitterBenchmark;
import com.carrotdata.membench.benchmarks.TwitterSentimentsBenchmark;

import net.rubyeye.xmemcached.XMemcachedClient;
import net.rubyeye.xmemcached.exception.MemcachedException;

public class Membench {
  
  private static enum Mode {
    SET, SET_GET, GET
  }
  
  private static Logger logger = LogManager.getLogger(Membench.class);

  static Benchmark bench;
  static int numThreads = 1;
  static String host = "localhost";
  static int port = 11211;
  static long numRecords = 10_000_000;
  static Mode mode = Mode.SET;
  static int batchSize = 50;
  
  static String[] data;
  
  static boolean compressValue;
    
  static AtomicLong currentId = new AtomicLong();
  
  static AtomicLong total = new AtomicLong();
  static AtomicLong compressed = new AtomicLong();
  static AtomicLong totalRead = new AtomicLong();
  
  public final static void main(String[] args) throws IOException {
    parseArgs(args);
    
    if (bench == null) {
      usage();
    }
    
    if (mode != Mode.GET) {
      runDataLoad();
    }
    if (mode != Mode.SET) {
      runDataGet();
    }
  }
  
  private static void runDataGet() throws IOException {

    logger.info("Running benchmark (READ): ", bench.getName());
    long toRead = 1000000;

    if (mode == Mode.GET) {
      int toLoad = 1_000_000;
      logger.info("Preparing {} records", toLoad);
      data = bench.getDataRecords(toLoad);
      logger.info("Prepared {} records", data.length);
    }
    logger.info("Reading {} random records in {} threads to server {}:{} batch size={}", numRecords , numThreads, host, port, batchSize);
    
    currentId.set(0);
    Runnable reader = () -> {

      long start = 0;
      long total = 0;
      long expected = 0;
      XMemcachedClient client = null;
      Random r = new Random();
      try {
        client = new XMemcachedClient(host, port);
        
        while (true) {
          start = r.nextLong();
          start = Math.abs(start) % (numRecords - batchSize);//currentId.getAndAdd(batchSize);
          if (start >= numRecords) {
            break;
          }
          int n = (int) Math.min(batchSize, numRecords - start);

          Collection<String> keys = getKeys(start, n);

          try {
            
            Map<String, Object> result = client.get(keys); 
            
            expected += n;
            for (Map.Entry<String, Object> entry: result.entrySet()){
              total++;
              totalRead.incrementAndGet();
  
              String key = entry.getKey();
              Object value = entry.getValue();
              byte[] bvalue = (byte[]) value;
              String expValue = getValue(key);
              if (value == null) {
                continue;
              }
              if (compressValue) {
                bvalue = GzipCompressor.decompress(bvalue);
              }
              if (Arrays.compare(expValue.getBytes(), bvalue) != 0) {
                logger.error("{} read failed on key={}", Thread.currentThread(), key);
                System.exit(-1);
              } 
            }
            if (expected % 100000 == 0) {
              logger.info("{} read {} records, failed={}, collisions={}%", Thread.currentThread().getName(), expected, expected - total, (double)(expected-total) * 100/expected);
            }
            
            if (expected >= toRead) {
              break;
            }
          } catch (TimeoutException | MemcachedException e) {
            logger.error("Error", e);
            return;
          } catch (InterruptedException e) {
            logger.error("Error", e);
          }
        }
      } catch (IOException e) {
        logger.error("Error", e);
        return;
      } finally {
        try {
          if (client != null) {
            client.shutdown();
          }
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    };

    long start = System.currentTimeMillis();
    Thread[] pool = new Thread[numThreads];

    for (int i = 0; i < numThreads; i++) {
      pool[i] = new Thread(reader);
      pool[i].start();
    }

    for (int i = 0; i < numThreads; i++) {
      try {
        pool[i].join();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }

    long end = System.currentTimeMillis();
    logger.info("Done benchmark[{}] read {} records off {} avg size={} in {} ms. RPS={} f={} s={}",
      bench.getName(), totalRead.get(), numRecords, bench.getAvgRecordSize(), (end - start),
      toRead * numThreads * 1000 / (end - start), toRead * numThreads * 1000, end - start);
  }
  
  private static Collection<String> getKeys(long start, int n) {
    List<String> result = new ArrayList<String>();
    for (int i = 0; i < n; i++) {
      result.add("KEY:" + (start + i));
    }
    return result;
  }
  
  private static long getId(String key) {
    return Long.parseLong(key.substring(4));
  }
  
  private static String getValue(String key) {
    
    long id = getId(key);
    return data[(int)(id % data.length)];
  }
  
  private static void runDataLoad() throws IOException {
    int toLoad = 1_000_000;

    logger.info("Running benchmark (LOAD): ", bench.getName());
    logger.info("Preparing {} records", toLoad);
    data = bench.getDataRecords(toLoad);
    logger.info("Prepared {} records", data.length);
    logger.info("Loading {} records in {} threads to server {}:{} batch size={}", numRecords , numThreads, host, port, batchSize);

    Runnable loader = () -> {

      long start = 0;
      String key = "KEY:";

      XMemcachedClient client = null;
      try {
        client = new XMemcachedClient(host, port);

        long loaded = 0;
        
        
        while (true) {
          start = currentId.getAndAdd(batchSize);
          if (start >= numRecords) {
            break;
          }
          int n = (int) Math.min(batchSize, numRecords - start);

          for (int i = 0; i < n - 1; i++) {
            int index = (int) ((start + i) % data.length);
            try {
              byte[] value = data[index].getBytes();
              total.addAndGet(value.length);
              if (compressValue) {
                value = GzipCompressor.compress(value);
                compressed.addAndGet(value.length);
              }
              client.setWithNoReply(key + (start + i), 10000, value);
              loaded++;
              if (loaded % 100000 == 0) {
                logger.info("{} loaded {} records", Thread.currentThread().getName(), loaded);
              }
            } catch (InterruptedException e) {
              logger.error("Error", e);
            } catch (MemcachedException e) {
              logger.error("Error", e);
              return;
            }
          }
          int index = (int) ((start + n - 1) % data.length);

          try {
            byte[] value = data[index].getBytes();
            total.addAndGet(value.length);
            if (compressValue) {
              value = GzipCompressor.compress(value);
              compressed.addAndGet(value.length);
            }
            client.set(key + (start + n - 1), 10000, value);
            loaded++;
            if (loaded % 100000 == 0) {
              logger.info("{} loaded {} records", Thread.currentThread().getName(), loaded);
            }
          } catch (TimeoutException | MemcachedException e) {
            logger.error("Error", e);
            return;
          } catch (InterruptedException e) {
            logger.error("Error", e);
          }
        }
      } catch (IOException e) {
        logger.error("Error", e);
        return;
      } finally {
        try {
          client.shutdown();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } 
      }
    };

    long start = System.currentTimeMillis();
    Thread[] pool = new Thread[numThreads];

    for (int i = 0; i < numThreads; i++) {
      pool[i] = new Thread(loader);
      pool[i].start();
    }

    for (int i = 0; i < numThreads; i++) {
      try {
        pool[i].join();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    
    long end = System.currentTimeMillis();
    logger.info("Done benchmark[{}] loaded {} records avg size={} in {} ms RPS={}, compresssion={}, Server RSS (est.)={}",
      bench.getName(), numRecords, bench.getAvgRecordSize(), (end - start),
      numRecords * 1000 / (end - start), compressed.get() == 0? "n/a": (double) total.get() / compressed.get(), format(memoryUsed()));
  }

  private static double memoryUsed() throws IOException {
    XMemcachedClient client = null;
    try {
      client = new XMemcachedClient(host, port);
      if (client != null) {
        Map<InetSocketAddress, Map<String, String>> res;
        try {
          res = client.getStats();
          Map<String, String> map = res.values().iterator().next();
          for (Map.Entry<String, String> entry : map.entrySet()) {
            String name = entry.getKey();
            if (name.endsWith("allocated_memory")) {
              // Memcarrot has RAM overhead of~ 190Mb (Java VM)
              return Long.parseLong(entry.getValue()) + 190_000_000;
            }
          }
          // Memcached
          res = client.getStatsByItem("slabs");
          map = res.values().iterator().next();
          for (Map.Entry<String, String> entry : map.entrySet()) {
            String name = entry.getKey();
            if (name.equals("total_malloced")) {
              return 1.05 * Long.parseLong(entry.getValue());
            }
          }
        } catch (MemcachedException | InterruptedException | TimeoutException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    } finally {
      if (client != null) {
        client.shutdown();
      }
    }
    return -1;
  }
  
  private static String format (double n) {
    long GB = 1024L * 1024 * 1024;
    long MB = 1024 * 1024;
    String scale = null;
    double v = 0;
    if (n >= GB) {
      v = (double) n / GB;
      scale = "GB";
    } else {
      v = (double) n / MB;
      scale = "MB";
    }
    String s = Double.toString(v);
    int index = s.indexOf(".");
    if (index >= 0) {
      int up = Math.min(index + 3, s.length());
      return s.substring(0, up) + scale;
    }
    return s + scale;
  }
  
  private static void parseArgs(String[] args) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      checkNextArg(args, i);
      i++;
      switch(arg) {
        case "-h" : {
          usage();
        }
        case "-b" : {
          initBenchmark(args[i]);
          break;
        }
        case "-n" : {
          numRecords = Long.parseLong(args[i]);
          break;
        }
        case "-t" : {
          numThreads = Integer.parseInt(args[i]);
          break;
        }
        case "-s" : {
          host = args[i];
          break;
        }
        case "-p" : { 
          port = Integer.parseInt(args[i]);
          break;
        }
        case "-c": 
          compressValue = true;
          break;
        case "-m":
          setMode(args[i]);
          break;
        case "-a":
          setBatchSize(args[i]);
          break;
        default: usage();  
      }
    }
  }
  
  private static void setBatchSize(String string) {
    
    try {
      batchSize = Integer.parseInt(string);
      if (batchSize <= 0 || batchSize > 1000) throw new NumberFormatException();
    } catch (NumberFormatException e) {
      logger.error("Wrong value for batch size: {}, expected integer number between 1 and 1000 (batchSize", string);
    }
  }

  private static void setMode(String s) {
    switch (s) {
      case "load" : mode = Mode.SET;
        break;
      case "load_read": mode = Mode.SET_GET;
        break;
      case "read" : mode = Mode.GET;
        break;
      default:
        throw new IllegalArgumentException("Unrecognized benchmark mode: " + s);
    }
  }

  private static void initBenchmark(String name) {
    
    switch(name) {
      
      case "airbnb" : 
        bench = new AirbnbBenchmark();
        break;
      case "amazon_books":
        logger.warn("Benchmark 'amazon_books' is not supported yet");
        System.exit(-1);
        break;
      case "amazon_product_reviews":
        bench = new AmazonProductReviewsBenchmark();
        break;
      case "arxiv" :
        bench = new ArxivBenchmark();
        break;
      case "dblp" :
        bench = new DblpBenchmark();
        break;
      case "github" :
        bench = new GithubBenchmark();
        break;
      case "ohio" :
        bench = new OhioStateEmployeeBenchmark();
        break;
      case "reddit" :
        bench = new RedditBenchmark();
        break;
      case "spotify" :
        bench = new SpotifyBenchmark();
        break;
      case "twitter" :
        bench = new TwitterBenchmark();
        break;
      case "twitter_sentiments":
        bench = new TwitterSentimentsBenchmark();
        break;
      default : {  
        logger.error("Unrecognized benchmark name: `{}`, valid benchmarks: airbnb, amazon_product_review, arxiv, dblp, " +
                      "github, ohio, reddit, spotify, twitter, twitter_sentiments", name);
        System.exit(-1);;
      }
    }
  }

  private static void checkNextArg(String[] args, int i) {
    if (i == args.length - 1) {
      usage();
    }
  }

  private static void usage() {
    System.out.println("Usage: membench.sh -b benchmark_name [-n number_records] [-t number_threads] [-s host] [-p port] -c [gzip] -m [load | load_read | read");
    System.out.println("     -a   batch size for set/get operations. Default: 50");
    System.out.println("     -b   benchmark name. Available benchmarks: amazon_product_review, airbnb, arxiv, dblp, github, ohio, reddit, spotify, twitter, ");
    System.out.println("          twitter_sentiments. ");
    System.out.println("     -n   number of records to load to the cache. Default: 10000000");
    System.out.println("     -t   number of client threads. Default: 1");
    System.out.println("     -s   memcached server address. Default: localhost");
    System.out.println("     -p   memcached port number. Default: 11211");
    System.out.println("     -c   compression codec name for client-side compression. Do not use it with Memcarrot. Supported: gzip. Default: none");
    System.out.println("     -m   mode of operation: load, load_read, read. Default: load");
    System.out.println("     -h   help.");
    System.exit(-1);
  }
}
