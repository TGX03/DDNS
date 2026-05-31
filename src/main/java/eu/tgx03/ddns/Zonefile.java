package eu.tgx03.ddns;

import java.io.*;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The Zonefile class parses a zonefile,
 * receives IPv4 addresses, IPv6 addresses, and IPv6 prefixes,
 * and, upon invocation of getZoneFile adjusts the previously loaded zonefile
 * so the placeholders are replaced, thereby especially making sure IPv6 addresses have their prefixes adjusted.
 */
public class Zonefile implements Serializable {

    /**
     * The regex not actually being used to identify IPv4 addresses to be replaced.
     */
    private static final String IPV4_REGEX = "IP4_(\\d+)";
    /**
     * The regex not actually being used to identify IPv6 addresses to be replaced.
     */
    private static final String IPV6_REGEX = "IP6_(\\d+)";
    /**
     * A regular expression pattern that matches IPv6 suffix definitions in zone files.
     * The pattern is designed to parse strings representing mappings of an index to
     * an IPv6 suffix in the format "IP6_P_<index>=<IPv6-suffix>".
     * <p>
     * - The first capturing group (\d+) captures the numeric index.
     * - The second capturing group ([0-9a-f:]{2,39}) captures the IPv6 suffix,
     * which is represented in hexadecimal colon-separated notation.
     */
    private static final String IPv6_PREFIX_REGEX = "IP6_P_(\\d+)=([0-9a-f:]{2,39})";
    private static final String SOA_REGEX = "^[^\\s]+\\s\\d+\\s[^\\s]+\\sSOA\\s[^\\s]+\\s[^\\s]+\\s(\\d+)\\s\\d+\\s\\d+\\s\\d+\\s\\d+$";

    /**
     * The original zone file as it was loaded into the program.
     */
    private String zoneBase;
    private final Lock ipv4ReadLock;
    private final Lock ipv4WriteLock;
    private final Lock ipv6ReadLock;
    private final Lock ipv6WriteLock;
    private final Lock ipv6PrefixReadLock;
    private final Lock ipv6PrefixWriteLock;
    private final Lock soaLock = new ReentrantLock();

    /**
     * An array holding all the IPv4 addresses to be put into the zonefile later on.
     */
    private Inet4Address[] ipv4Addresses = new Inet4Address[1];
    /**
     * An array holding all the IPv6 addresses to be put into the zonefile later on.
     */
    private Inet6Address[] ipv6Addresses = new Inet6Address[0];
    /**
     * An array holding all the IPv6 prefixes to be merged with the suffixes in the zone file.
     */
    private IPv6Prefix[] prefixes = new IPv6Prefix[1];

    {
        ReentrantReadWriteLock ipv4Lock = new ReentrantReadWriteLock();
        this.ipv4ReadLock = ipv4Lock.readLock();
        this.ipv4WriteLock = ipv4Lock.writeLock();
        ReentrantReadWriteLock ipv6Lock = new ReentrantReadWriteLock();
        this.ipv6ReadLock = ipv6Lock.readLock();
        this.ipv6WriteLock = ipv6Lock.writeLock();
        ReentrantReadWriteLock ipv6PrefixLock = new ReentrantReadWriteLock();
        this.ipv6PrefixReadLock = ipv6PrefixLock.readLock();
        this.ipv6PrefixWriteLock = ipv6PrefixLock.writeLock();
    }

    /**
     * Creates a new instance of Zonefile from the provided String.
     * The String contains the plain zonefile data and replace operations will be executed directly upon it.
     *
     * @param zoneBase The String of the zonefile
     */
    public Zonefile(String zoneBase) {
        this.zoneBase = zoneBase;
    }

    /**
     * Constructs a new Zonefile instance using the provided InputStream.
     * The InputStream supplies the zonefile data, which will be processed as a single string.
     *
     * @param input The InputStream from which the zonefile data is read.
     */
    public Zonefile(InputStream input) {
        this.zoneBase = new BufferedReader(new InputStreamReader(input)).lines().collect(Collectors.joining(System.lineSeparator()));
    }

    /**
     * Constructs a new Zonefile instance using the provided File.
     * The File should contain the zonefile data, which is read
     * and stored as a single string for further processing.
     *
     * @param source The File that contains the zonefile data to load.
     * @throws IOException If an I/O error occurs while reading the file.
     */
    public Zonefile(File source) throws IOException {
        try (FileReader reader = new FileReader(source)) {
            this.zoneBase = new BufferedReader(reader).lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }

    /**
     * Sets the IPv4 address which should be inserted into the zonefile at index {@code index}.
     *
     * @param index   The zero-based index at which to set the IPv4 address.
     * @param address The IPv4 address to set at the specified index.
     */
    public void setIpv4Address(int index, Inet4Address address) {
        if (index >= this.ipv4Addresses.length) {
            ipv4WriteLock.lock();
            if (index >= this.ipv4Addresses.length) this.ipv4Addresses = Arrays.copyOf(this.ipv4Addresses, index + 1);
            ipv4WriteLock.unlock();
        }
        ipv4ReadLock.lock();
        this.ipv4Addresses[index] = address;
        ipv4ReadLock.unlock();
    }

    /**
     * Sets the IPv6 address which should be inserted into the zonefile at index {@code index}.
     *
     * @param index   The zero-based index at which to set the IPv4 address.
     * @param address The IPv6 address to set at the specified index.
     */
    public void setIpv6Address(int index, Inet6Address address) {
        if (index >= this.ipv6Addresses.length) {
            ipv6WriteLock.lock();
            if (index >= this.ipv6Addresses.length) this.ipv6Addresses = Arrays.copyOf(this.ipv6Addresses, index + 1);
            ipv6WriteLock.unlock();
        }
        ipv6ReadLock.lock();
        this.ipv6Addresses[index] = address;
        ipv6ReadLock.unlock();
    }

    /**
     * Sets the IPv6 prefix at the specified index in the zonefile.
     * If the index exceeds the current capacity of the prefix array,
     * the array is resized to accommodate the specified index.
     *
     * @param index  The zero-based index at which to set the IPv6 prefix.
     * @param prefix The IPv6Prefix object to set at the specified index.
     */
    public void setIpv6Prefix(int index, IPv6Prefix prefix) {
        if (index >= this.prefixes.length) {
            ipv6PrefixWriteLock.lock();
            if (index >= this.prefixes.length) this.prefixes = Arrays.copyOf(this.prefixes, index + 1);
            ipv6PrefixWriteLock.unlock();
        }
        ipv6PrefixReadLock.lock();
        this.prefixes[index] = prefix;
        ipv6PrefixReadLock.unlock();
    }

    /**
     * Get the current SOA serial number if it exists.
     *
     * @return The found SOA serial number.
     * @throws IllegalStateException Gets thrown when this zonefile has no SOA record.
     */
    public int getSOA() throws IllegalStateException {
        Matcher matcher = Pattern.compile(SOA_REGEX).matcher(zoneBase);
        if (matcher.find()) return Integer.parseInt(matcher.group(1));
        else throw new IllegalStateException("SOA not found in zonefile");
    }

    /**
     * Increment the current SOA serial number by one.
     */
    public void incrementSOA() {
        soaLock.lock();
        try {
            int soa = getSOA();
            zoneBase = zoneBase.replace(Integer.toString(soa), Integer.toString(soa + 1));
        } catch (IllegalStateException ignored) {
        } finally {
            soaLock.unlock();
        }
    }

    /**
     * Generates a zone file with placeholders replaced by the appropriate IPv4 and IPv6
     * addresses. The method performs the following operations in sequence:
     * 1. Replace IPv4 placeholders in the format `IP4_{index}` within the zone file
     * with the corresponding IPv4 addresses.
     * 3. Replaces IPv6 placeholders in the format `IP6_{index}` within the zone file
     * with the corresponding IPv6 addresses.
     * 4. Identifies and processes IPv6 prefix placeholders in the zone file, adjusts the
     * prefixes accordingly, and replaces them in the zone file.
     *
     * @return The updated zone file string after performing all address and prefix replacements.
     */
    @Override
    public String toString() {
        ipv4WriteLock.lock();
        ipv6WriteLock.lock();
        ipv6PrefixWriteLock.lock();

        String result = this.zoneBase;

        for (int i = 0; i < ipv4Addresses.length; i++) {
            if (this.ipv4Addresses[i] == null) continue;
            result = result.replaceAll("IP4_" + i, ipv4Addresses[i].getHostAddress());
        }

        for (int i = 0; i < ipv6Addresses.length; i++) {
            if (this.ipv6Addresses[i] == null) continue;
            result = result.replaceAll("IP6_" + i, ipv6Addresses[i].getHostAddress());
        }

        HashMap<String, Inet6Address> ipv6Prefixes = new HashMap<>();
        Pattern ipv6PrefixPattern = Pattern.compile(IPv6_PREFIX_REGEX);
        Matcher matcher = ipv6PrefixPattern.matcher(result);
        while (matcher.find()) {
            if (ipv6Prefixes.containsKey(matcher.group())) continue;
            int prefix = Integer.parseInt(matcher.group(1));
            Inet6Address address = null;
            try {
                address = (Inet6Address) Inet6Address.getByName(matcher.group(2));
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
            if (this.prefixes[prefix] != null) {
                Inet6Address adjustedAddress = this.prefixes[prefix].adjustPrefix(address);
                ipv6Prefixes.put(matcher.group(), adjustedAddress);
            }
        }

        for (Map.Entry<String, Inet6Address> entry : ipv6Prefixes.entrySet()) {
            result = result.replaceAll(entry.getKey(), entry.getValue().getHostAddress());
        }

        ipv4WriteLock.unlock();
        ipv6WriteLock.unlock();
        ipv6PrefixWriteLock.unlock();
        return result;
    }
}
