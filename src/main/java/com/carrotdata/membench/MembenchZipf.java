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
import java.util.Arrays;
import java.util.Map;
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
  static long numRecords = 10_000_000;
  static double zipfAlpha = 0.9;
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
    runBenchmark();
  }
  
  private static void runBenchmark() throws IOException {
    int toLoad = 1_000_000;

    logger.info("Running benchmark (LOAD DATA): ", bench.getName());
    logger.info("Preparing {} records", toLoad);
    data = bench.getDataRecords(toLoad);
    logger.info("Prepared {} records", data.length);
    logger.info("Reading {} records in {} threads from server {}:{}", numRecords, numThreads, host,
      port);
    
    final XMemcachedClient client = new XMemcachedClient(host, port);

    Runnable r = () -> {

      String key = "KEY:";

      Percentile add_perc = new Percentile(10000, (int) numRecords);
      Percentile get_perc = new Percentile(10000, (int) numRecords);
      try {
        long loaded = 0;
        ZipfDistribution dist = new ZipfDistribution((int) numRecords, zipfAlpha);
        long hits = 0;
        for (int i = 0; i < 10 * numRecords; i++) {
          int index = (int) (dist.sample() % data.length);
          try {
            byte[] value = data[index].getBytes();
            total.addAndGet(value.length);
            if (compressValue) {
              value = GzipCompressor.compress(value);
              compressed.addAndGet(value.length);
            }
            String skey = key + index;
            long t1 = System.nanoTime();
            byte[] v = client.get(skey);
            if (v == null) {
              t1 = System.nanoTime();
              client.add(skey, 10000, value);
              add_perc.add(System.nanoTime() - t1);
            } else if (Arrays.equals(value, v)){
              hits++;
              get_perc.add(System.nanoTime() - t1);
            } else {
              logger.error("Wrong value for key{}", skey);
              return;
            }
            loaded++;
            if (loaded % 100000 == 0) {
              logger.info("{} queried {} records, hit ratio={}", Thread.currentThread().getName(),
                loaded, (double) hits / i);
            }
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
    try {
      client.shutdown();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    logger.info(
      "Done benchmark[{}] loaded {} records avg size={} in {} ms RPS={}, compresssion={}, Server RSS (est.)={}",
      bench.getName(), numRecords, bench.getAvgRecordSize(), (end - start),
      10 * numThreads * numRecords * 1000 / (end - start),
      compressed.get() == 0 ? "n/a" : (double) total.get() / compressed.get(),
      format(memoryUsed()));
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
        case "-c": {
          compressValue = true;
          break;
        }
        case "-z" : { 
          zipfAlpha = Double.parseDouble(args[i]);
          break;
        }
        default: usage();  
      }
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
    System.out.println("Usage: membench.sh -b benchmark_name [-n number_records] [-t number_threads] [-s host] [-p port] -c [gzip]");
    System.out.println("     -b   benchmark name. Available benchmarks: amazon_product_review, airbnb, arxiv, dblp, github, ohio, reddit, spotify, twitter, ");
    System.out.println("          twitter_sentiments. ");
    System.out.println("     -n   number of queries per thread. Default: 10,000,000");
    System.out.println("     -t   number of client threads. Default: 1");
    System.out.println("     -s   memcached server address. Default: localhost");
    System.out.println("     -p   memcached port number. Default: 11211");
    System.out.println("     -z   zipfian alpha value. Default: 0.9");
    System.out.println("     -c   compression codec name for client-side compression. Do not use it with Memcarrot. Supported: gzip. Default: none");
    System.out.println("     -h   help.");
    System.exit(-1);
  }
}
