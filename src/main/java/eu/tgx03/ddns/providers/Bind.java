package eu.tgx03.ddns.providers;

import eu.tgx03.ddns.DNSProvider;
import eu.tgx03.ddns.Zonefile;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A DNS provider implementation for managing zone files with a local Bind DNS server.
 * This class handles updating the zone file and restarting the Bind instance
 * to apply the changes.
 */
public class Bind implements DNSProvider {

    /**
     * The command to restart the Bind instance running on the server.
     *
     */
    private final String[] restartCommand;
    /**
     * The location of the zonefile used by Bind.
     */
    private final Path zonefileLocation;

    /**
     * Prevent multiple updates at the same time.
     */
    private final Lock accessLock = new ReentrantLock();

    /**
     * Constructs a new Bind instance for managing a zonefile with a local Bind DNS server.
     *
     * @param restartCommand   The command to restart the Bind instance running on the server.
     * @param zonefileLocation The file system path to the zonefile used by the Bind DNS server.
     */
    public Bind(String restartCommand, Path zonefileLocation) {
        this.restartCommand = restartCommand.split(" ");
        this.zonefileLocation = zonefileLocation;
    }

    @Override
    public void sendZonefile(Zonefile zonefile) throws IOException {
        try {
            accessLock.lock();
            zonefile.incrementSOA();
            PrintWriter writer = new PrintWriter(zonefileLocation.toFile());
            writer.print(zonefile);
            writer.close();
            restartBind();
        } finally {
            accessLock.unlock();
        }
    }

    /**
     * Executes the restart command to restart the Bind instance.
     *
     * @throws IOException When the restart command fails.
     */
    private void restartBind() throws IOException {
        ProcessBuilder builder = new ProcessBuilder(restartCommand);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);

        Process process = builder.start();
        try {
            if (process.waitFor() != 0)
                throw new IOException("Restart Bind failed with exit code " + process.exitValue());
        } catch (InterruptedException ignored) {
        }
    }
}
