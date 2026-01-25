package eu.tgx03.ddns;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a server that manages DNS updates and supports operations
 * for dynamically modifying DNS zone files through HTTP requests.
 * The server handles client requests to update IPv4, IPv6 addresses, and
 * IPv6 prefixes for DNS records, and schedules corresponding updates to
 * the DNS providers.
 */
public class Server {

    /**
     * This lock is used to stop the update thread
     * to throttle updates.
     */
    private final Lock updateLock = new ReentrantLock();
    /**
     * This is the barrier the update thread waits on to trigger updates.
     * This is used to make sure if IPv4 and IPv6 addresses are updated at the same time,
     * which usually happens,
     * this doesn't result in three zonefile updates in quick succession.
     */
    private final Condition updateBarrier = updateLock.newCondition();
    /**
     * This map holds all the DNSProviders as well as their corresponding zonefiles.
     */
    private final Map<DNSProvider, Zonefile> zonefiles;
    private final HttpServer server;

    /**
     * Constructs a new Server instance with the specified zonefiles map.
     * The Server is initialized to listen on the default port 80.
     *
     * @param zonefiles A map where the key is a DNSProvider and the value is its corresponding Zonefile.
     *                  This map represents the DNS setup for the server, linking providers to their zonefile configurations.
     */
    public Server(Map<DNSProvider, Zonefile> zonefiles) throws IOException {
        this.zonefiles = zonefiles;
        server = HttpServer.create(new InetSocketAddress(80), 0);

        Thread update = new Thread(new Updater());
        update.setDaemon(true);
        update.start();
    }

    /**
     * Constructs a new Server instance with the specified map of DNS zonefiles and a designated port.
     *
     * @param zonefiles A map where the key is a DNSProvider and the value is its corresponding Zonefile.
     *                  This map represents the DNS setup for the server, linking providers to their zonefile configurations.
     * @param port      The port on which the server will listen for incoming network connections.
     */
    public Server(Map<DNSProvider, Zonefile> zonefiles, int port) throws IOException {
        this.zonefiles = zonefiles;
        server = HttpServer.create(new InetSocketAddress(port), 0);

        Thread update = new Thread(new Updater());
        update.setDaemon(true);
        update.start();
    }

    /**
     * Starts the server on the specified port and listens for incoming requests.
     *
     * @throws IOException In case the server could not be created or started.
     */
    public void start() {
        this.server.createContext("/update", new Handler());
        this.server.setExecutor(null);
        this.server.start();
    }

    public void stop() {
        this.server.stop(0);
    }

    /**
     * Informs the update thread that data was changed and it should run.
     * The update thread will wait 5 seconds before running again to prevent multiple updates in quick succession.
     */
    private void scheduleUpdate() {
        updateLock.lock();
        updateBarrier.signalAll();
        updateLock.unlock();
    }

    /**
     * Update all DNS providers with the current zonefiles.
     */
    private void update() throws IOException {
        for (Map.Entry<DNSProvider, Zonefile> entry : zonefiles.entrySet())
            entry.getKey().sendZonefile(entry.getValue());
    }

    /**
     * The handler for handling incoming HTTP requests.
     * It listens for incoming queries, answering with appropriate responsde codes,
     * updating the zonefiles, and scheduling updates accordingly.
     * It does not ever send data back to the client,
     * a successful update is indicated by a 204 response code,
     * and errors usually by 400 unless a better code is known.
     */
    private class Handler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            // Stop invalid methods
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            // Check correct path
            URI requestURI = exchange.getRequestURI();
            if (!requestURI.getPath().equals("/update")) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            try {
                Map<String, String> query = parseQuery(requestURI.getQuery());
                int id = Integer.parseInt(query.get("id"));
                if (query.containsKey("ip4")) {
                    if (query.size() != 2) throw new IllegalArgumentException("Invalid query string: " + query);
                    else updateIP4(id, query.get("ip4"));
                } else if (query.containsKey("ip6")) {
                    if (query.size() != 2) throw new IllegalArgumentException("Invalid query string: " + query);
                    else updateIP6(id, query.get("ip6"));
                } else if (query.containsKey("prefix")) {
                    if (query.size() != 3) throw new IllegalArgumentException("Invalid query string: " + query);
                    else updatePrefix(id, query.get("prefix"), Integer.parseInt(query.get("length")));
                } else throw new IllegalArgumentException("Invalid query string: " + query);
                exchange.sendResponseHeaders(204, -1);
            } catch (RuntimeException | UnknownHostException e) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
            }


        }

        /**
         * Parses a URI query string into a map of key-value pairs.
         * The query string should contain key-value pairs separated by an '&' character,
         * with keys and values separated by an '=' character.
         *
         * @param query The query string to parse, must not be null or empty
         * @return A map of key-value pairs extracted from the query string
         * @throws IllegalArgumentException If the query string is invalid, contains duplicate keys,
         *                                  or does not comply with the expected format
         */
        private static Map<String, String> parseQuery(String query) {
            String[] params = query.split("&");
            Map<String, String> result = new HashMap<>();
            for (String param : params) {
                String[] pair = param.split("=");
                if (pair.length != 2 || result.containsKey(pair[0]))
                    throw new IllegalArgumentException("Invalid query string: " + query);
                result.put(pair[0], pair[1]);
            }
            return result;
        }

        /**
         * Updates the IPv4 address associated with a specific identifier in all zone files.
         * After the update, the method triggers a scheduled update for propagating changes.
         *
         * @param id The unique identifier for which the IPv4 address should be updated.
         * @param ip The string representation of the IPv4 address to be updated.
         * @throws UnknownHostException If the provided IP address string is invalid or cannot be resolved.
         */
        private void updateIP4(int id, String ip) throws UnknownHostException {
            Inet4Address address = (Inet4Address) Inet4Address.getByName(ip);
            for (Map.Entry<DNSProvider, Zonefile> entry : zonefiles.entrySet())
                entry.getValue().setIpv4Address(id, address);
            scheduleUpdate();
        }

        /**
         * Updates the IPv6 address associated with a specific identifier in all zone files.
         * After the updates, the method triggers a scheduled update for propagating changes.
         *
         * @param id The unique identifier for which the IPv6 address should be updated.
         * @param ip The string representation of the IPv6 address to be updated.
         * @throws UnknownHostException If the provided IP address string is invalid or cannot be resolved.
         */
        private void updateIP6(int id, String ip) throws UnknownHostException {
            Inet6Address address = (Inet6Address) Inet6Address.getByName(ip);
            for (Map.Entry<DNSProvider, Zonefile> entry : zonefiles.entrySet())
                entry.getValue().setIpv6Address(id, address);
            scheduleUpdate();
        }

        /**
         * Updates the IPv6 address associated with a specific identifier in all zone files.
         * After the updates, the method triggers a scheduled update for propagating changes.
         *
         * @param id     The unique identifier for which the IPv6 prefix should be updated.
         * @param prefix The string representation of the IPv6 prefix to be updated.
         * @throws UnknownHostException If the provided IP address string is invalid or cannot be resolved.
         */
        private void updatePrefix(int id, String prefix, int prefixLength) throws UnknownHostException {
            Inet6Address address = (Inet6Address) Inet6Address.getByName(prefix);
            IPv6Prefix ip6prefix = new IPv6Prefix(prefixLength, address);
            for (Map.Entry<DNSProvider, Zonefile> entry : zonefiles.entrySet())
                entry.getValue().setIpv6Prefix(id, ip6prefix);
            scheduleUpdate();
        }
    }

    /**
     * The class representing the update thread.
     * It waits until the first update is scheduled,
     * and after each schedule occurs, it waits for 5 seconds before running,
     * and then waits for the next schedule.
     */
    private class Updater implements Runnable {

        @Override
        public void run() {
            updateLock.lock();
            while (true) {
                updateBarrier.awaitUninterruptibly();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ignored) {
                }
                try {
                    update();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
