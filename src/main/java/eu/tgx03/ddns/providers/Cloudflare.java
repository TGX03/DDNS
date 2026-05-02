package eu.tgx03.ddns.providers;

import eu.tgx03.ddns.DNSProvider;
import eu.tgx03.ddns.Zonefile;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.util.stream.Collectors;

/**
 * The Cloudflare class provides an implementation of the DNSProvider interface
 * for managing DNS records using the Cloudflare API.
 * It works by deleting all existing DNS records and uploading a new zonefile.
 */
public class Cloudflare implements DNSProvider {

    // This is just the stuff required for correctly formatting a multipart/form-data request.
    private static final String HYPHENS = "--";
    private static final String BOUNDARY = "*****";
    private static final String CRLF = "\r\n";
    private static final String FILE_NAME = "zonefile.txt";

    /**
     * The api key with permissions to modify the respective zone.
     */
    private final String apiKey;
    /**
     * The ID of the zone to modify.
     */
    private final String zoneID;

    /**
     * Create a new Cloudflare instance with the given API key and zone ID.
     *
     * @param apiKey The API key with permissions to modify the respective zone.
     * @param zoneID The ID of the zone to modify.
     */
    public Cloudflare(String apiKey, String zoneID) {
        this.apiKey = apiKey;
        this.zoneID = zoneID;
    }

    @Override
    public synchronized void sendZonefile(Zonefile zonefile) throws IOException {
        deleteRecords();
        uploadZonefile(zonefile);
    }

    /**
     * Deletes all DNS records in the zone on Cloudflare.
     *
     * @throws IOException Indicates Cloudflare replied with a response code other than 200.
     */
    private void deleteRecords() throws IOException {
        URL url = URI.create("https://api.cloudflare.com/client/v4/zones/" + zoneID + "/dns_records?per_page=50000").toURL();
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.connect();

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) throw new IOException("Failed to get DNS records: " + responseCode);

        String response = new BufferedReader(new InputStreamReader(connection.getInputStream())).lines().collect(Collectors.joining());
        connection.disconnect();

        JSONArray records = new JSONObject(response).getJSONArray("result");
        for (Object current : records) {
            JSONObject record = (JSONObject) current;
            if ("A".equals(record.getString("type")) || "AAAA".equals(record.getString("type"))) deleteRecord(record.getString("id"));
        }
    }

    /**
     * Deletes the specified DNS record from Cloudflare.
     *
     * @param id The ID of the record to delete.
     * @throws IOException Indicates Cloudflare replied with a response code other than 200.
     */
    private void deleteRecord(String id) throws IOException {
        URL url = URI.create("https://api.cloudflare.com/client/v4/zones/" + zoneID + "/dns_records/" + id).toURL();
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("DELETE");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.connect();
        if (connection.getResponseCode() != 200)
            throw new IOException("Failed to delete DNS record: " + connection.getResponseCode());
        connection.disconnect();
    }

    /**
     * Uploads the provided zonefile to Cloudflare's DNS records using the zone's API endpoint.
     *
     * @param zonefile The Zonefile object containing the DNS records to be imported into the Cloudflare zone.
     * @throws IOException Indicates Cloudflare replied with a response code other than 200.
     */
    private void uploadZonefile(Zonefile zonefile) throws IOException {
        URL url = URI.create("https://api.cloudflare.com/client/v4/zones/" + zoneID + "/dns_records/import").toURL();
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + BOUNDARY);
        connection.setRequestProperty("Connection", "Keep-Alive");
        connection.setDoOutput(true);
        connection.connect();

        PrintWriter writer = new PrintWriter(connection.getOutputStream());
        writer.append(HYPHENS + BOUNDARY + CRLF);
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + FILE_NAME + "\"" + CRLF);
        writer.append("Content-Type: text/plain").append(CRLF).append(CRLF).flush();
        writer.append(zonefile.toString()).flush();
        writer.append(CRLF).flush();
        writer.append(HYPHENS + BOUNDARY + HYPHENS + CRLF).flush();

        int responseCode = connection.getResponseCode();
        connection.disconnect();
        if (responseCode != 200) throw new IOException("Failed to upload zonefile: " + responseCode);
    }
}
