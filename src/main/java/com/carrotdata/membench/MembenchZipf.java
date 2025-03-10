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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.math3.distribution.ZipfDistribution;
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
import com.carrotdata.membench.util.Percentile;

import net.rubyeye.xmemcached.XMemcachedClient;
import net.rubyeye.xmemcached.exception.MemcachedException;

public class MembenchZipf {
  
  private static Logger logger = LogManager.getLogger(MembenchZipf.class);

  static Benchmark bench;
  static int numThreads = 1;
  static String host = "localhost";
  static int port = 11211;
  static long baseRecords = 1_000_000;
  static int recMultiplier = 10;
  static long numRecords = baseRecords * recMultiplier;
  static double zipfAlpha = 0.9;
  // Time to replace whole data set
  static int dataReplaceTime = 12 * 3600; // 12 hours
  static int testRunTime = 3600; // 3600 seconds
  static String[] data;
  static byte[][] compressedData;
  
  static boolean compressValue;
  static int targetRPS = 100_000;
  static AtomicLong totalSize = new AtomicLong();
  static AtomicLong totalRequests = new AtomicLong();
  static AtomicLong compressed = new AtomicLong();
  static int batchSize = 50;
  
  static int[] ttl = new int[] {300, 1000, 2000, 3000, 10000};
  
  public final static void main(String[] args) throws IOException {
    parseArgs(args);
    if (bench == null) {
      usage();
    }
    runBenchmark();
  }
  
  /**
   * 
   * @param timeElapsed
   * @return
   */
  private static long getHead(long timeElapsed) {
    long rt = 1000L * dataReplaceTime;
    return (long) (numRecords * (1 + (double) timeElapsed / rt)); 
  }
  
  private static int getTtl() {
    ThreadLocalRandom r = ThreadLocalRandom.current();
    int index = r.nextInt(ttl.length);
    return ttl[index];
  }
  
  private static void compressData() throws IOException {
    compressedData = new byte[data.length][];
    for (int i = 0; i < data.length; i++) {
      byte[] value = data[i].getBytes();
      compressedData[i] = GzipCompressor.compress(value);
    }
  }
  
  private static void runBenchmark() throws IOException {
    int toLoad = 10_000_000;

    logger.info("Running benchmark (LOAD DATA): ", bench.getName());
    logger.info("Preparing up to {} records", toLoad);
    data = bench.getDataRecords(toLoad);
    if (compressValue) {
      compressData();
    }
    logger.info("Prepared {} records, average size={} bytes", data.length, bench.getAvgRecordSize());
    logger.info("Reading {} records in {} threads from server {}:{}", numRecords, numThreads, host,
      port);
    

    Runnable r = () -> {
      long startTime = System.currentTimeMillis();
      long runTime = 1000L * testRunTime; 

      Percentile add_perc = new Percentile(10000, (int) numRecords);
      Percentile get_perc = new Percentile(10000, (int) numRecords);
      XMemcachedClient client = null;
            
      try {
        long loaded = 0;
        client = new XMemcachedClient(host, port);
        ZipfDistribution dist = new ZipfDistribution((int) numRecords, zipfAlpha);
        long hits = 0;
        while (System.currentTimeMillis() - startTime < runTime) {
          long head = getHead(System.currentTimeMillis() - startTime);
          try {
            long t1 = System.nanoTime();
            Collection<String> keys = getKeys(dist, head, batchSize);
            get_perc.add(System.nanoTime() - t1);
            Map<String, Object> result = client.get(keys);
            Collection<String> keysToLoad = result == null? keys: removeKeys(keys, result.keySet());
            hits += result == null? 0: result.size();
            t1 = System.nanoTime();
            for (String k : keysToLoad) {
              long index = Long.parseLong(k.substring(4));
              byte[] value = null;
              if (compressValue) {
                value = compressedData[(int) (index % data.length)];
                compressed.addAndGet(value.length);
              } else {
                value = data[(int) (index % data.length)].getBytes();
                totalSize.addAndGet(value.length);
              }
              client.addWithNoReply(k, getTtl(), value);
            }
            add_perc.add(System.nanoTime() - t1);
            loaded += batchSize;
            totalRequests.addAndGet(batchSize);
            if (loaded % 100000 == 0) {
              logger.info("{} queried {} records, hit ratio={}", Thread.currentThread().getName(),
                loaded, (double) hits / loaded);
            }
            rateLimit(startTime, loaded);            
          } catch (InterruptedException e) {
            logger.error("Error", e);
          } catch (MemcachedException e) {
            logger.error("Error", e);
            return;
          } catch (TimeoutException e) {
            logger.error("Error", e);
            return;
          } 
        }
      } catch (IOException e) {
        logger.error("Error", e);
        return;
      } finally {
        if (client != null) {
          try {
            client.shutdown();
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }
      }
      logger.info(" ADD, min={} max={}, p50={}, p90={} p99={} p99.9={} p99.99={}", add_perc.min(),
        add_perc.max(), add_perc.value(0.5), add_perc.value(0.9), add_perc.value(0.99),
        add_perc.value(0.999), add_perc.value(0.9999));
      logger.info(" GET, min={} max={}, p50={}, p90={} p99={} p99.9={} p99.99={}", get_perc.min(),
        get_perc.max(), get_perc.value(0.5), get_perc.value(0.9), get_perc.value(0.99),
        get_perc.value(0.999), get_perc.value(0.9999));
    };

    long start = System.currentTimeMillis();
    Thread[] pool = new Thread[numThreads];

    for (int i = 0; i < numThreads; i++) {
      pool[i] = new Thread(r);
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
    logger.info(
      "Done benchmark[{}] queried {} records avg size={} in {} ms RPS={}, compression={}, Server RSS (est.)={}",
      bench.getName(), totalRequests.get(), bench.getAvgRecordSize(), (end - start),
      totalRequests.get() * 1000 / (end - start),
      compressed.get() == 0 ? "n/a" : (double) totalSize.get() / compressed.get(),
      format(memoryUsed()));
  }

  private static void rateLimit(long startTime, long loaded) {
    double rateLimit = (double) targetRPS / numThreads;
    long expectedMax = (long)(rateLimit * (System.currentTimeMillis() - startTime) / 1000);
    if (expectedMax >= loaded) {
      return;
    }
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
    }
  }

  private static Collection<String> getKeys(ZipfDistribution dist, long head, int batch){
    List<String> keys = new ArrayList<String>();
    String key = "KEY:";
    for (int i = 0; i < batch; i++) {
      long index = head - dist.sample();
      keys.add(key + index);
    }
    return keys;
  }
  
  private static Collection<String> removeKeys(Collection<String> keys, Set<String> result) {
    for (String k: result) {
      keys.remove(k);
    }
    return keys;
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
        case "-m" : {
          recMultiplier = Integer.parseInt(args[i]);
          numRecords = recMultiplier * baseRecords;
          break;
        }
        case "-w" : {
          numThreads = Integer.parseInt(args[i]);
          break;
        }
        case "-t" : {
          ttl = parseTtl(args[i]);
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
        case "-r" : { 
          dataReplaceTime = Integer.parseInt(args[i]);
          break;
        }
        case "-e" : { 
          testRunTime = Integer.parseInt(args[i]);
          break;
        }
        case "-c": {
          compressValue = true;
          break;
        }
        case "-z" : { 
          zipfAlpha = Double.parseDouble(args[i]);
          break;
        }
        case "-g" : {
          targetRPS = Integer.parseInt(args[i]);
          break;
        }
        default: usage();  
      }
    }
  }
  
  private static int[] parseTtl(String s) {
    String[] values = s.trim().split(",");
    int[] ttls = new int[values.length];
    for (int i = 0; i < values.length; i++) {
      ttls[i] = Integer.parseInt(values[i]);
    }
    return ttls;
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
    System.out.println("Usage: membench.sh -b benchmark_name [-m number_records] [-w number_threads] [-s host] [-p port] [-c gzip] ...");
    System.out.println("     -b   benchmark name. Available benchmarks: amazon_product_review, airbnb, arxiv, dblp, github, ohio, reddit, spotify, twitter, ");
    System.out.println("          twitter_sentiments. ");
    System.out.println("     -m   data set size - number of records (in millions). Default: 10 (millions)");
    System.out.println("     -w   number of client threads (workers). Default: 1");
    System.out.println("     -r   data set replacement time (seconds). Default: 43200 (12 hours)");
    System.out.println("     -e   test execution time (seconds). Default: 3600");
    System.out.println("     -s   memcached server address. Default: localhost");
    System.out.println("     -p   memcached port number. Default: 11211");
    System.out.println("     -z   zipfian alpha value. Default: 0.9");
    System.out.println("     -c   compression codec name for client-side compression. Do not use it with Memcarrot. Supported: gzip. Default: none");
    System.out.println("     -t   TTL list of values (in seconds), comma-separated. Default: 300,1000,2000,3000,10000"); 
    System.out.println("     -g   target RPS. Default: 100000"); 
    System.out.println("     -h   help.");
    System.exit(-1);
  }
}
