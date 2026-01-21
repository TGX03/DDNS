package eu.tgx03.ddns;

/**
 * Represents a provider for handling DNS-related operations.
 * This interface defines the contract for sending zone files
 * to a DNS system or service.
 */
public interface DNSProvider {

    /**
     * Sends a DNS zone file to the DNS system or service.
     * This method allows transferring of zone file data for configuration or updates.
     *
     * @param zonefile The DNS zone file to be sent. It contains records and configurations relevant to a domain's DNS setup.
     */
    void sendZonefile(Zonefile zonefile);

}
