package com.carrotdata.membench;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import net.rubyeye.xmemcached.GetsResponse;
import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.XMemcachedClientBuilder;
import net.rubyeye.xmemcached.command.BinaryCommandFactory;
import net.rubyeye.xmemcached.utils.AddrUtil;

public class McdcGet {
    private static void usage() {
        System.out.println("Usage: java McdcGet <GET|GETS> <key> [host:port]");
        System.out.println("Defaults: host:port = 127.0.0.1:11211, binary protocol");
    }

    public static void main(String[] args) {
        if (args.length < 2) { usage(); return; }

        final String cmd = args[0].toUpperCase(Locale.ROOT);
        final String key = args[1];
        final String server = (args.length >= 3) ? args[2] : "127.0.0.1:11211";

        MemcachedClient client = null;
        try {
            XMemcachedClientBuilder builder =
                new XMemcachedClientBuilder(AddrUtil.getAddresses(server));
            // Force binary protocol
            builder.setCommandFactory(new BinaryCommandFactory());
            
            // Reasonable timeouts
            builder.setOpTimeout(3000);

            client = builder.build();

            switch (cmd) {
                case "GET": {
                    byte[] val = client.get(key);
                    if (val == null) {
                        System.out.println("NOT_FOUND");
                    } else {
                        printValue(val, /*cas*/ null);
                    }
                    break;
                }
                case "GETS": {
                    GetsResponse<Object> casVal = client.gets(key);
                    if (casVal == null) {
                        System.out.println("NOT_FOUND");
                    } else {
                        printValue((byte[])casVal.getValue(), casVal.getCas());
                    }
                    break;
                }
                default:
                    System.err.println("Unsupported command: " + cmd);
                    usage();
                    System.exit(2);
            }
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        } finally {
            if (client != null) {
                try { client.shutdown(); } catch (Exception ignore) {}
            }
        }
    }

    private static void printValue(byte[] val, Long cas) {
        if (cas != null) {
            System.out.printf("CAS: %d%n", cas);
        }
        System.out.printf("VALUE-LEN: %d bytes%n", val.length);

        // Try to show a friendly preview (printable ASCII); fall back to hex
        String ascii = new String(val, StandardCharsets.US_ASCII);
        System.out.println("ASCII: " + ascii);
    }

 }
