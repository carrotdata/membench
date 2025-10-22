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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.carrotdata.cache.Builder;
import com.carrotdata.cache.index.SubCompactBaseNoSizeIndexFormat;
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
import com.carrotdata.membench.client.Client;
import com.carrotdata.membench.client.ClientType;
import com.github.benmanes.caffeine.cache.Caffeine;

import net.rubyeye.xmemcached.GetsResponse;
import net.rubyeye.xmemcached.XMemcachedClient;
import net.rubyeye.xmemcached.XMemcachedClientBuilder;
import net.rubyeye.xmemcached.command.BinaryCommandFactory;
import net.rubyeye.xmemcached.exception.MemcachedException;
import net.rubyeye.xmemcached.utils.AddrUtil;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import org.ehcache.CacheManager;
import org.ehcache.PersistentCacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.core.spi.service.StatisticsService;
import org.ehcache.core.statistics.CacheStatistics;
import org.ehcache.core.internal.statistics.DefaultStatisticsService;

public class Membench {
  private static Logger logger = LogManager.getLogger(Membench.class);

  private static enum Mode {
    SET, SET_GET, GET
  }
  
  static Benchmark bench;
  static int prefixLength;
  static int numThreads = 1;
  static String host = "127.0.0.1";
  static int port = 11211;
  static long numRecords = 10_000_000;
  static Mode mode = Mode.SET;
  static ClientType type = ClientType.MEMCACHED;
  static int batchSize = 50;
  static String[] data;
  static boolean compressValue;
  static AtomicLong currentId = new AtomicLong();
  static AtomicLong total = new AtomicLong();
  static AtomicLong compressed = new AtomicLong();
  static AtomicLong totalRead = new AtomicLong();
  
  private static class MemcachedClient implements Client {
    XMemcachedClient client;
    
    MemcachedClient(String host , int port) throws IOException {
   // 1.  Build with your server list
//      XMemcachedClientBuilder builder =
//              new XMemcachedClientBuilder(AddrUtil.getAddresses("localhost:11211"));
//      builder.setOpTimeout(2000000);
//      // 2.  Enable the binary protocol
//      builder.setCommandFactory(new BinaryCommandFactory());
//
//      // 3.  Create the client
//      client = (XMemcachedClient) builder.build();
      this.client = new XMemcachedClient(host, port);
      //this.client.
    }
    
    @Override
    public Map<String, Object> get(Collection<String> keys) throws IOException {
      try {
        return client.get(keys);
      } catch (Exception e) {
        throw new IOException(e);
      }
    }

    @Override
    public long set(long start, int n, long loaded) throws IOException {
      String key = bench.getName() +":";

      int ttl = 10000;
      for (int i = 0; i < n - 1; i++) {
        int index = (int) ((start + i) % data.length);
        try {
          byte[] value = data[index].getBytes();
          //logger.info("{}: size={}", index, data[index].length());
          total.addAndGet(value.length);
          if (compressValue) {
            value = GzipCompressor.compress(value);
            compressed.addAndGet(value.length);
          }
          client.setWithNoReply(key + (start + i), ttl, value); 
          loaded++;
          if (loaded % 100000 == 0) {
            logger.info("{} loaded {} records", Thread.currentThread().getName(), loaded);
          }
        } catch (Exception e) {
          logger.error("Error", e);
          throw new IOException (e);
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
        //logger.info("{}: size={}", index, data[index].length());

        client.set(key + (start + n - 1), ttl, value);
        loaded++;
        if (loaded % 100000 == 0) {
          logger.info("{} loaded {} records", Thread.currentThread().getName(), loaded);
        }
      } catch (Exception e) {
        logger.error("Error", e);
        throw new IOException (e);
      } 
      return loaded;
    }

    @Override
    public void close() throws IOException {
      client.shutdown();
    }
  }
  
  
  private static class RedisClient implements Client {
    JedisPool pool ;
    
    RedisClient(String host, int port) {
      pool = new JedisPool(host, port);
    }
    
    @Override
    public Map<String, Object> get(Collection<String> keys) throws IOException {
      try (Jedis jedis = pool.getResource()) {
        byte[][] arr = new byte[keys.size()][];    
        int i = 0;
        for (String key: keys) {
          arr[i] = key.getBytes();
          i++;
        }
        List<byte[]> list = jedis.mget(arr);
        Map<String, Object> result = new HashMap<String, Object>();
        for (i = 0; i < list.size(); i++) {
          byte[] value = list.get(i);
          if (compressValue) {
            value = GzipCompressor.decompress(value);
          }
          String key = new String(arr[i]);
          if (value != null) {
            result.put(key,  value);
          }
        }
        return result;
      }
    }

    @Override
    public long set(long start, int n, long loaded) throws IOException {
      String key = bench.getName() + ":";

      try (Jedis jedis = pool.getResource()) {
        for (int i = 0; i < n; i++) {
          int index = (int) ((start + i) % data.length);
          byte[] value = data[index].getBytes();
          total.addAndGet(value.length);
          if (compressValue) {
            value = GzipCompressor.compress(value);
            compressed.addAndGet(value.length);
          }
          long expire = 10000;
          jedis.setex((key + (start + i)).getBytes(), expire, value);
          loaded++;
          if (loaded % 100000 == 0) {
            logger.info("{} loaded {} records", Thread.currentThread().getName(), loaded);
          }
        }
        return loaded;
      }
    }

    @Override
    public void close() throws IOException {
      pool.close();
    }
  }

  public static class CarrotCacheClient implements Client {

    static com.carrotdata.cache.Cache cache;

    public CarrotCacheClient() {
      synchronized (CarrotCacheClient.class) {
        if (cache != null) {
          return;
        }
        Builder b = new Builder("membench");
        b.withCacheMaximumSize(30_000_000_000L).withCacheDataSegmentSize(16_000_000)
          .withMainQueueIndexFormat(SubCompactBaseNoSizeIndexFormat.class.getName())
          .withCacheCompressionEnabled(true);
        try {
          cache = b.buildMemoryCache();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    @Override
    public Map<String, Object> get(Collection<String> keys) throws IOException {
      Map<String, Object> result = new HashMap<>();
      for (String key : keys) {
        byte[] bkey = key.getBytes();
        byte[] value = cache.get(bkey);
        if (value != null) {
          result.put(key, new String(value));
        }
      }
      return result;
    }

    @Override
    public long set(long start, int n, long loaded) throws IOException {
      String key = bench.getName() + ":";
      for (int i = 0; i < n; i++) {
        int index = (int) ((start + i) % data.length);
        String value = data[index];
        total.addAndGet(value.length());
        long expire = 0;
        cache.put((key + (start + i)).getBytes(), value.getBytes(), expire);
        loaded++;
        if (loaded % 100000 == 0) {
          logger.info("{} loaded {} records", Thread.currentThread().getName(), loaded);
        }
      }
      return loaded;
    }

    @Override
    public void close() throws IOException {
      cache.shutdown();
    }

    @Override
    public boolean isLocal() {
      return true;
    }
    
    @Override
    public long memoryUsed() {
      return cache.getStorageAllocated();
    }
  }
  
  public static class CaffeineClient implements Client {

    static com.github.benmanes.caffeine.cache.Cache<String, byte[]> cache;

    CaffeineClient(long size) {
      synchronized (CaffeineClient.class) {
        if (cache != null) {
          return;
        }
        cache = Caffeine.newBuilder().maximumSize(size).build();
      }
    }

    @Override
    public Map<String, Object> get(Collection<String> keys) throws IOException {
      Map<String, Object> result = new HashMap<>();
      for (String key : keys) {
        byte[] value = cache.getIfPresent(key);
        if (value != null) {
          if (compressValue) {
            value = GzipCompressor.decompress(value);
          }
          result.put(key, value);
        }
      }
      return result;
    }

    @Override
    public long set(long start, int n, long loaded) throws IOException {
      String key = bench.getName() + ":";
      for (int i = 0; i < n; i++) {
        int index = (int) ((start + i) % data.length);
        byte[] value = data[index].getBytes();
        total.addAndGet(value.length);
        if (compressValue) {
          value = GzipCompressor.compress(value);
        }
        cache.put(key + (start + i), value);
        loaded++;
        if (loaded % 100000 == 0) {
          logger.info("{} loaded {} records", Thread.currentThread().getName(), loaded);
        }
      }
      return loaded;
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public boolean isLocal() {
      return true;
    }

  }
 
 public static class EHCacheClient implements Client {
   
   static org.ehcache.Cache<String, byte[]> cache;
   static StatisticsService service;
   
   EHCacheClient() {
     
     synchronized(EHCacheClient.class) {
       if (cache != null) {
         return;
       }
       service = new DefaultStatisticsService();
       
       // Remove previous
       File f = new File("./ehcache");
       f.deleteOnExit();
     
       // Create CacheManager with off-heap storage
       PersistentCacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
             .using(service)
             .with(CacheManagerBuilder.persistence(f))
             .withCache("membench",
                     CacheConfigurationBuilder.newCacheConfigurationBuilder(
                             String.class, byte[].class,
                             ResourcePoolsBuilder.newResourcePoolsBuilder()
                                     .offheap(40, MemoryUnit.GB)
                                     .disk(50, MemoryUnit.GB, true)// Define off-heap memory size
                     )
             )
             .build(true);

       // Retrieve cache instance
       cache = cacheManager.getCache("membench", String.class, byte[].class);
     }
   }
   
   @Override
   public Map<String, Object> get(Collection<String> keys) throws IOException {
     Map<String, Object> result = new HashMap<>();
     for (String key : keys) {
       byte[] value =  cache.get(key);
       if (value != null) {
         if (compressValue) {
           value = GzipCompressor.decompress(value);
         }
         result.put(key, value);
       }
     }
     return result;
   }

   @Override
   public long set(long start, int n, long loaded) throws IOException {
     String key = bench.getName() + ":";
     for (int i = 0; i < n; i++) {
       int index = (int) ((start + i) % data.length);
       byte[] value = data[index].getBytes();
       total.addAndGet(value.length);
       if (compressValue) {
         value = GzipCompressor.compress(value);
       }
       cache.put(key + (start + i), value);
       loaded++;
       if (loaded % 100000 == 0) {
         logger.info("{} loaded {} records", Thread.currentThread().getName(), loaded);
       }
     }
     return loaded;
   }

   @Override
   public void close() throws IOException {     
   }
    
   @Override
   public boolean isLocal() {
     return true;
   }
   
   /*
    *  Dirty hack
    */
   public static long allocatedMemory() {
     CacheStatistics stats = service.getCacheStatistics("membench");
     return stats.getTierStatistics().get("OffHeap").getAllocatedByteSize();
   }
  }
 
  public final static void main(String[] args) throws IOException {
    parseArgs(args);
        
    if (bench == null) {
      usage();
    }
    try (var s = new java.net.Socket("127.0.0.1", 11212)) {
      System.out.println("OK");
    }
    prefixLength = bench.getName().length() + 1;
    if (mode != Mode.GET) {
      runDataLoad();
    }
    if (mode != Mode.SET) {
      runDataGet();
    }
    shutdown();
  }
  
  private static void shutdown() throws IOException {
    //logger.info("Press any button ...");
    //System.in.read();
    // Shutdown if required
    if (isLocalClient()) {
      Client c = getClient();
      c.close();
    }
  }
  
  private static boolean isLocalClient() throws IOException {
    
    switch (type) {
      case MEMCACHED:
      case REDIS:
        return false;
      case CARROTCACHE:
      case CAFFEINE:
      case EHCACHE:
      default:
        return true;
    }
  }
  
  private static Client getClient() throws IOException {
    
    switch (type) {
      case MEMCACHED:
        return new MemcachedClient(host, port);
      case REDIS:
        return new RedisClient(host, port);
      case CARROTCACHE:
        return new CarrotCacheClient();
      case CAFFEINE:
        return new CaffeineClient(2 * numRecords);
      case EHCACHE:
        return new EHCacheClient();
      default:
        return null;
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
      Client client = null;
      Random r = new Random();
      try {
        client = getClient();
        
        while (true) {
          start = r.nextLong();
          start = numRecords == 1? 0:Math.abs(start) % (numRecords - batchSize);
          if (start >= numRecords) {
            break;
          }
          int n = (int) Math.min(batchSize, numRecords - start);
          Collection<String> keys = getKeys(start, n);
          String k = keys.iterator().next();
          try {
            
            Map<String, Object> result = client.get(keys); 
            if (result == null || result.isEmpty()) {
              logger.error("read failed on key={} size={} total={}", k, getValue(k).length(), total);
              continue;
            }
            expected += n;
            total += n;
//            for (Map.Entry<String, Object> entry: result.entrySet()){
//              total++;
//              totalRead.incrementAndGet();
//  
//              String key = entry.getKey();
//              Object value = entry.getValue();
//              byte[] bvalue = (byte[]) value;
//              String expValue = getValue(key);
//              if (value == null) {
//                continue;
//              }
//              if (compressValue) {
//                bvalue = GzipCompressor.decompress(bvalue);
//              }
//              if (Arrays.compare(expValue.getBytes(), bvalue) != 0) {
//                logger.error("{} read failed on key={} value={} size={} total={}", 
//                  Thread.currentThread(), key, new String(bvalue), bvalue.length, total);
//                System.exit(-1);
//              } 
//            }
            if (expected % 100000 == 0) {
              logger.info("{} read {} records, failed={}, collisions={}%", Thread.currentThread().getName(), expected, 
                expected - total, (double)(expected - total) * 100/expected);
            }
            
            if (expected >= toRead) {
              break;
            }
          } catch (Exception e) {
            logger.error("Error", e);
            return;
          } 
        }
      } catch (IOException e) {
        logger.error("Error", e);
        return;
      } finally {
        try {
          if (client != null && !client.isLocal()) {
            client.close();
          }
        } catch (IOException e) {
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
      result.add(bench.getName() + ":" + (start + i));
    }
    return result;
  }
  
  private static long getId(String key) {
    return Long.parseLong(key.substring(prefixLength));
  }
  
  
  @SuppressWarnings("unused")
  private static String getValue(String key) {
    long id = getId(key);
    return data[(int)(id % data.length)];
  }
  
  private static void verifyBinProtoCommands() {
    
    XMemcachedClient xclient ;
    try {
      Client client = getClient();
      if (client instanceof MemcachedClient) {
        xclient = ((MemcachedClient) client).client;
        if (xclient == null) {
          logger.error("XMemcachedClient is null");
          return;
        }
        logger.info("XMemcachedClient is ready");
      } else {
        logger.error("Not a MemcachedClient instance: {}", client.getClass().getName());
        return;
      }
      String longValue = "LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL";
      String shortValue = "SSSSSSSSS";
      verifyBinProtoCommands(xclient, longValue);
      verifyBinProtoCommands(xclient, shortValue);
    } catch (Exception e) {
      logger.error("Error", e);
      return;
    }
  }
  
  private static void verifyBinProtoCommands(XMemcachedClient client, String value) throws TimeoutException, InterruptedException, MemcachedException {
    // SET/GETS
    int exp = 10000;
    String key = "key" ;
    boolean result = client.set(key, exp, value);
    GetsResponse<Object> v = client.gets(key);
    String val = (String) v.getValue();
    if(!value.equals(val)) {
      logger.error("Value mismatch: expected={}, actual={}", value, val);
      System.exit(-1);
    } else {
      logger.info("Value matches: {}", val);
    }
    long cas = v.getCas();
    // CAS +
    result = client.cas(key, exp, value + value, cas);
    if (!result) {
      logger.error("CAS failed for key={}", key);
      System.exit(-1);
    } else {
      logger.info("CAS succeeded for key={}", key);
    }
    
    // CAS -
    result = client.cas(key, exp, value + value, cas);
    if (result) {
      logger.error("Wrong CAS succeeded for key={}", key);
      System.exit(-1);
    } else {
      logger.info("Wrong CAS not succeeded for key={}", key);
    }
    // GAT
    Object o =client.getAndTouch(key, exp + 1000);
    val = (String) o;
    if (!val.equals(value + value)) {
      logger.error("Get and touch failed for key={} value={}", key, val);
      System.exit(-1);
    } else {
      logger.info("Get and touch succeeded for key={} value={}", key, val);
    }
    // REPLACE
    result = client.replace(key, exp, value);
    if (!result) {
      logger.error("Replace failed for key={}", key);
      System.exit(-1);
    } else {
      logger.info("Replace succeeded for key={}", key);
    }
    
    // GETS
    v = client.gets(key);
    val = (String)v.getValue();
    if (!val.equals(value)) {
      logger.error("Gets failed for key={} value={}", key, val);
      System.exit(-1);
    } else {
      logger.info("Gets succeeded for key={} value={}", key, val);
    }
    cas = v.getCas();
    // CAS again
    result = client.cas(key, exp, value + value, cas);
    if (!result) {
      logger.error("CAS failed for key={}", key);
      System.exit(-1);
    } else {
      logger.info("CAS succeeded for key={}", key);
    }
    // ADD
    result = client.add(key, exp, value);
    if (result) {
      logger.error("Add succeeded (unexpected) for key={} but should fail", key);
      System.exit(-1);
    } else {
      logger.info("Add not succeeded (expected) for key={}", key);
    }
    
    // DELETE
    result = client.delete(key);
    if (!result) {
      logger.error("Delete failed for key={}", key);
      System.exit(-1);
    } else {
      logger.info("Delete succeeded for key={}", key);
    }
    
    // SET empty  
    result = client.set(key, exp, "");
    // APPEND
    client.append(key, value);
    // PREPEND
    client.prepend(key, value);
    
    o =client.get(key);
    val = (String) o;
    if (!val.equals(value + value)) {
      logger.error("Get failed for key={} value={}", key, val + val);
      System.exit(-1);
    } else {
      logger.info("Get succeeded for key={} value={}", key, val + val);
    }
    
    client.set(key,  exp,  "0");
    // INCR/DECR
    long num = 1000;
    
    long res = client.incr(key, num);
    
    if (res != num) {
      logger.error("Incr failed for key={} value={}", key, res);
      System.exit(-1);
    } else {
      logger.info("Incr succeeded for key={} value={}", key, res);
    }
    res = client.decr(key, num);
    if (res != 0) {
      logger.error("Decr failed for key={} value={}", key, res);
      System.exit(-1);
    } else {
      logger.info("Decr succeeded for key={} value={}", key, res);
    }
    
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

      Client client = null;
      //int batchSize = 1;
      try {
        client = getClient();
        long loaded = 0;
        while (true) {
          start = currentId.getAndAdd(batchSize);
          if (start >= numRecords) {
            break;
          }
          int n = (int) Math.min(batchSize, numRecords - start);

          loaded = client.set(start, n, loaded);
        }
      } catch (Throwable e) {
        logger.error("Error", e);
        return;
      } finally {
        try {
          if (client != null && !client.isLocal()) {
            client.close();
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

    switch(type) {
      case REDIS: 
        return 0;
      case MEMCACHED:
        return getMemcachedMemory();
      case CARROTCACHE:
        return CarrotCacheClient.cache.getStorageAllocated();
      case CAFFEINE:
        return estimateHeapUsage();
      case EHCACHE:
        return estimateHeapUsage() + EHCacheClient.allocatedMemory();
      default:  
        return 0;
    }
  }
  
  private static long estimateHeapUsage() {
      data = null;
      Runtime r = Runtime.getRuntime();
      r.gc();
      return r.totalMemory() - r.freeMemory();
  }
  
  private static double getMemcachedMemory() throws IOException {
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
    return 0;
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
        case "-l":
          String client = args[i];
          parseClientType(client);
          break;  
        default: usage();  
      }
    }
  }
  
  private static void parseClientType(String client) {
    switch(client) {
      case "redis":
        type = ClientType.REDIS;
        break;
      case "memcached":
        type = ClientType.MEMCACHED;
        break;
      case "carrotcache":
        type = ClientType.CARROTCACHE;
        break;
      case "caffeine":
        type = ClientType.CAFFEINE;
        break;
      case "ehcache":
        type = ClientType.EHCACHE;
        break;
      default:
        throw new IllegalArgumentException(client);
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
    System.out.println("Usage: membench.sh -b benchmark_name [-n number_records] [-t number_threads] [-s host] [-p port] -c [gzip] -m [load | load_read | read] -a [size] -l [memcached | redis]");
    
    System.out.println("     -a   batch size for set/get operations. Default: 50");
    System.out.println("     -l   memcached, redis. carrotcache, caffeine, ehcache. Default: memcached");

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
