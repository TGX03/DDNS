package eu.tgx03.ddns;

import eu.tgx03.ddns.providers.Cloudflare;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The DDNS (Dynamic DNS) class is responsible for managing dynamic DNS zone files,
 * integrating with DNS providers, and running a DNS server.
 * This class allows users to specify zone files, configure DNS provider credentials,
 * and control the server through command-line arguments.
 */
public class DDNS {

    /**
     * All the zonefiles that have been loaded,
     * stored to save them when the JVM exits.
     */
    private static final Map<Zonefile, File> ZONEFILES = new HashMap<>();
    /**
     * The server instance if one has been created.
     */
    private static Server server;

    /**
     * The entry point of the dynamic DNS (DDNS) application. This method processes
     * command-line arguments, loads DNS zone files, configures DNS providers, and
     * starts the server.
     * If the zonefile is in a txt format as exported by Bind,
     * it will be overwritten with a Java object if the JVM exits and has permission to write to the file.
     *
     * @param args Command-line arguments. Each argument can be:
     *             - "port=<number>": Specifies the port number for the server to listen on (default is 80).
     *             - "<file>;<provider>;<credentials>": Specifies a zone file path, a DNS provider (e.g., "Cloudflare"),
     *               and provider-specific credentials separated by colons.
     * @throws IOException If an error occurs while reading zone files or setting up the server.
     */
    public static void main(String[] args) throws IOException {
        Map<DNSProvider, Zonefile> zonefiles = new HashMap<>();
        int port = 80;
        for (String arg : args) {
            if (arg.matches("port=\\d+")) {
                port = Integer.parseInt(arg.split("=")[1]);
                continue;
            }

            String[] split = arg.split(";");
            File zonefile = new File(split[0]);
            ObjectInputStream input = new ObjectInputStream(new FileInputStream(zonefile));
            Zonefile zone;
            try {
                zone = (Zonefile) input.readObject();
            } catch (ClassNotFoundException e) {
                zone = new Zonefile(zonefile);
            }
            ZONEFILES.put(zone, zonefile);

            DNSProvider provider;
            switch (split[1]) {
                case "Cloudflare" -> provider = parseCloudflare(Arrays.copyOfRange(split, 2, split.length));
                default -> throw new IllegalArgumentException("Provider not implemented: " + split[1]);
            }
            zonefiles.put(provider, zone);
        }

        server = new Server(zonefiles, port);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown()));
    }

    /**
     * Parses the credentials for Cloudflare from the provided arguments.
     *
     * @param arguments API key and zone ID, separated by a colon.
     * @return The Cloudflare provider instance.
     */
    private static Cloudflare parseCloudflare(String[] arguments) {
        assert arguments.length == 2;
        return new Cloudflare(arguments[0], arguments[1]);
    }

    /**
     * A class used as a shutdown hook to save the zonefiles before the JVM exits.
     */
    private static class Shutdown implements Runnable {
        @Override
        public void run() {
            server.stop();
            for (Map.Entry<Zonefile, File> entry : ZONEFILES.entrySet()) {
                try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(entry.getValue()))) {
                    out.writeObject(entry.getKey());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
