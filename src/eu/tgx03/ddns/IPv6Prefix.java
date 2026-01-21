package eu.tgx03.ddns;

import java.net.Inet6Address;
import java.net.UnknownHostException;

/**
 * Represents an IPv6 network prefix by storing its upper and lower 64 bits and
 * the corresponding suffix masks used to apply the prefix to IP addresses.
 * This class provides utilities for working with IPv6 prefixes and addresses,
 * including conversion between different networks.
 */
public class IPv6Prefix {

    /**
     * The upper 64 bits of this prefix.
     */
    private final long upper;
    /**
     * The lower 64 bits of this prefix.
     */
    private final long lower;

    /**
     * The upper 64 bits of the suffix mask used to adjust IP addresses to this prefix.
     */
    private final long upper_suffix_mask;
    /**
     * The lower 64 bits of the suffix mask used to adjust IP addresses to this prefix.
     */
    private final long lower_suffix_mask;

    /**
     * Constructs an IPv6Prefix object by converting the given IPv6 address into its
     * upper and lower 64-bit components and zeroing the remaining bits.
     *
     * @param prefixLength the prefix length, representing the number of bits in the address
     *                     that constitute the network portion. Must be in the range 0 to 128.
     * @param address      the IPv6 address represented as an {@link Inet6Address} object.
     *                     This address is used to determine the associated upper and lower
     *                     components for the prefix.
     * @throws IllegalArgumentException if the prefix length is not in the valid range
     *                                  [0, 128].
     */
    public IPv6Prefix(int prefixLength, Inet6Address address) {
        IPv6Long prefix = IPv6ToLong(address);
        IPv6Long mask = prefixMask(prefixLength);
        this.upper = prefix.upper & mask.upper;
        this.lower = prefix.lower & mask.lower;

        IPv6Long suffixMask = suffixMask(prefixLength);
        this.upper_suffix_mask = suffixMask.upper;
        this.lower_suffix_mask = suffixMask.lower;
    }

    /**
     * Converts an IPv6 address represented as an {@link Inet6Address} object into its 128-bit
     * representation, separated into two 64-bit parts, upper and lower.
     *
     * @param address the IPv6 address to be converted, provided as an {@link Inet6Address} object.
     *                This address is broken down into its high-order 64 bits and low-order 64 bits.
     * @return an {@link IPv6Long} object containing the upper and lower 64-bit components
     * of the given IPv6 address.
     */
    public static IPv6Long IPv6ToLong(Inet6Address address) {
        byte[] bytes = address.getAddress();
        long upper = Byte.toUnsignedLong(bytes[0]);
        upper <<= 8;
        upper |= Byte.toUnsignedLong(bytes[1]);
        upper <<= 8;
        upper |= Byte.toUnsignedLong(bytes[2]);
        upper <<= 8;
        upper |= Byte.toUnsignedLong(bytes[3]);
        upper <<= 8;
        upper |= Byte.toUnsignedLong(bytes[4]);
        upper <<= 8;
        upper |= Byte.toUnsignedLong(bytes[5]);
        upper <<= 8;
        upper |= Byte.toUnsignedLong(bytes[6]);
        upper <<= 8;
        upper |= Byte.toUnsignedLong(bytes[7]);
        long lower = Byte.toUnsignedLong(bytes[8]);
        lower <<= 8;
        lower |= Byte.toUnsignedLong(bytes[9]);
        lower <<= 8;
        lower |= Byte.toUnsignedLong(bytes[10]);
        lower <<= 8;
        lower |= Byte.toUnsignedLong(bytes[11]);
        lower <<= 8;
        lower |= Byte.toUnsignedLong(bytes[12]);
        lower <<= 8;
        lower |= Byte.toUnsignedLong(bytes[13]);
        lower <<= 8;
        lower |= Byte.toUnsignedLong(bytes[14]);
        lower <<= 8;
        lower |= Byte.toUnsignedLong(bytes[15]);
        return new IPv6Long(upper, lower);
    }

    /**
     * Converts a 128-bit IPv6 address, represented as an {@link IPv6Long} object,
     * into an {@link Inet6Address} object.
     *
     * @param address the input IPv6 address represented as an {@link IPv6Long} object,
     *                containing the high-order 64 bits (upper) and low-order 64 bits (lower)
     *                of the address.
     * @return the {@link Inet6Address} object corresponding to the provided IPv6 address.
     * @throws RuntimeException if the provided address cannot be converted into an {@link Inet6Address},
     *                          typically caused by an {@link UnknownHostException}.
     */
    public static Inet6Address longToInet6Address(IPv6Long address) {
        byte[] bytes = new byte[16];
        bytes[0] = (byte) (address.upper >> 56);
        bytes[1] = (byte) (address.upper >> 48);
        bytes[2] = (byte) (address.upper >> 40);
        bytes[3] = (byte) (address.upper >> 32);
        bytes[4] = (byte) (address.upper >> 24);
        bytes[5] = (byte) (address.upper >> 16);
        bytes[6] = (byte) (address.upper >> 8);
        bytes[7] = (byte) (address.upper);
        bytes[8] = (byte) (address.lower >> 56);
        bytes[9] = (byte) (address.lower >> 48);
        bytes[10] = (byte) (address.lower >> 40);
        bytes[11] = (byte) (address.lower >> 32);
        bytes[12] = (byte) (address.lower >> 24);
        bytes[13] = (byte) (address.lower >> 16);
        bytes[14] = (byte) (address.lower >> 8);
        bytes[15] = (byte) (address.lower);
        try {
            return (Inet6Address) Inet6Address.getByAddress(bytes);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generates a prefix mask for an IPv6 address based on the specified prefix length.
     * The mask is represented as an {@link IPv6Long} object with two 64-bit components
     * (upper and lower) defining the network bits of the IPv6 prefix.
     *
     * @param prefixLength the length of the network prefix, ranging from 0 to 128. A
     *                     value of 0 provides a mask of all zeroes, while a value of
     *                     128 provides a mask of all ones.
     * @return an {@link IPv6Long} object representing the prefix mask, where the
     * network bits are set to 1 up to the specified prefix length, and the
     * remaining bits are set to 0.
     * @throws IllegalArgumentException if the prefix length is outside the valid range [0, 128].
     */
    private static IPv6Long prefixMask(int prefixLength) {
        if (prefixLength == 0) return new IPv6Long(0L, 0L);
        if (prefixLength < 0 || prefixLength > 128)
            throw new IllegalArgumentException("Invalid prefix length: " + prefixLength);

        if (prefixLength == 64) {
            return new IPv6Long(-1L, 0L);
        } else if (prefixLength == 128) {
            return new IPv6Long(-1L, -1L);
        } else if (prefixLength < 64) {
            long upper = -1L << (64 - prefixLength);
            return new IPv6Long(upper, 0L);
        } else {
            long lower = -1L << (128 - prefixLength);
            return new IPv6Long(-1L, lower);
        }
    }

    /**
     * Generates a suffix mask for an IPv6 address based on the specified prefix length.
     * The suffix mask is represented as an {@link IPv6Long} object, with the non-network bits
     * set to 1 and the network bits set to 0, starting at the specified prefix length.
     *
     * @param prefixLength the length of the network prefix, ranging from 0 to 128. A value of 0
     *                     results in a mask with all bits set to 1, and a value of 128 results
     *                     in a mask with all bits set to 0.
     * @return an {@link IPv6Long} object representing the suffix mask, where bits outside the
     * network portion are set to 1 and network bits are set to 0.
     * @throws IllegalArgumentException if the prefix length is outside the valid range [0, 128].
     */
    private static IPv6Long suffixMask(int prefixLength) {
        if (prefixLength == 0) return new IPv6Long(-1L, -1L);
        if (prefixLength < 0 || prefixLength > 128)
            throw new IllegalArgumentException("Invalid prefix length: " + prefixLength);

        if (prefixLength == 64) {
            return new IPv6Long(0L, -1L);
        } else if (prefixLength == 128) {
            return new IPv6Long(0L, 0L);
        } else if (prefixLength <= 64) {
            long upper = -1L >>> prefixLength;
            return new IPv6Long(upper, -1L);
        } else {
            long lower = -1 >>> (128 - prefixLength);
            return new IPv6Long(0L, lower);
        }
    }

    /**
     * Changes the prefix of the given IPv6 address to this prefix.
     *
     * @param address The address to modify.
     * @return The address with the new prefix.
     */
    public Inet6Address adjustPrefix(Inet6Address address) {
        IPv6Long addressLong = IPv6ToLong(address);
        long upperSuffix = addressLong.upper & this.upper_suffix_mask;
        long lowerSuffix = addressLong.lower & this.lower_suffix_mask;

        long upperResult = this.upper | upperSuffix;
        long lowerResult = this.lower | lowerSuffix;

        return longToInet6Address(new IPv6Long(upperResult, lowerResult));
    }

    public record IPv6Long(long upper, long lower) {
    }
}
