package eu.tgx03.ddns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * This class converts a Zonefile initially into a file already containing initial addresses,
 * so all addresses are present when it's uploaded for the first time.
 * This is not necessary depending on how quick the initial address update is,
 * but in case someone wants to be sure.
 */
public final class ZonefileConverter {

    /**
     * The main method executes the Zonefile conversion process. It reads an input Zonefile,
     * processes specific address and prefix mappings provided as command-line arguments,
     * and writes the modified Zonefile to the specified output file.
     *
     * @param args the command-line arguments where:
     *             args[0] is the path to the input Zonefile,
     *             args[1] is the path to the output file,
     *             args[2..n] are mappings of the form "id;address" or "id;prefix",
     *             where "id" is an integer identifier and "address" can be an IPv4 or IPv6 address,
     *             or "prefix" is an IPv6 prefix in "address/prefixLength" format
     * @throws IOException              if an error occurs while reading the input file or writing the output file
     * @throws IllegalArgumentException if the provided address or prefix is invalid
     */
    public static void main(String[] args) throws IOException {
        File input = new File(args[0]);
        File output = new File(args[1]);
        Zonefile zonefile = new Zonefile(input);

        for (int i = 2; i < args.length; i++) {
            String[] split = args[i].split(";");
            int id = Integer.parseInt(split[0]);
            if (split[1].contains("/")) {
                zonefile.setIpv6Prefix(id, parsePrefix(split[1]));
            } else {
                InetAddress address = InetAddress.getByName(split[1]);
                switch (address) {
                    case Inet4Address ipv4 -> zonefile.setIpv4Address(id, ipv4);
                    case Inet6Address ipv6 -> zonefile.setIpv6Address(id, ipv6);
                    default -> throw new IllegalArgumentException("Invalid address: " + address);
                }
            }
        }

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(output))) {
            out.writeObject(zonefile);
        }
    }

    /**
     * Parses a string representation of an IPv6 prefix into an {@code IPv6Prefix} object.
     * The input string must be in the form "address/prefixLength", where "address" is
     * an IPv6 address and "prefixLength" is the numerical prefix length.
     *
     * @param prefix the string representation of the IPv6 prefix to parse, in the format "address/prefixLength"
     * @return an {@code IPv6Prefix} object constructed from the specified address and prefix length
     * @throws UnknownHostException if the IPv6 address in the input string cannot be resolved
     */
    private static IPv6Prefix parsePrefix(String prefix) throws UnknownHostException {
        String[] split = prefix.split("/");
        Inet6Address address = (Inet6Address) Inet6Address.getByName(split[0]);
        return new IPv6Prefix(Integer.parseInt(split[1]), address);
    }
}
