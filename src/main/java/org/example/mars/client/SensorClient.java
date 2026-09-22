package org.example.mars.client;

import org.example.mars.model.SensorType;
import org.example.mars.protocol.SensorProtocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.Random;

/**
 * A simulated sensor robot somewhere in the Mars base.
 *
 * The sensor does not decide what it measures: when it connects, HQ assigns it
 * the first sensor type that is not covered yet, e.g.
 *
 *     HQ    -> ASSIGNED:O2;SENSOR-02
 *
 * Start the class once for each sensor you want. The first one becomes TEMP,
 * the next O2, then PRESSURE, then CO2. A fifth sensor is turned away with
 * REJECTED, because then every type is already covered.
 *
 * After the handshake the sensor sends one random measurement every 5 seconds:
 *
 *     SENSOR-02;O2:21.4
 */
public class SensorClient {

    private static final String HOST = "localhost";
    private static final int PORT = 5000;
    private static final int INTERVAL_MS = 5000;

    // HQ is expected to answer immediately; this is only a safety net
    private static final int HANDSHAKE_TIMEOUT_MS = 10_000;

    // java.util.Random is safe to share between threads
    private static final Random RANDOM = new Random();

    // Both are decided by HQ during the handshake
    private SensorType type;
    private String sensorId = "SENSOR-??";

    public static void main(String[] args) {
        new SensorClient().start();
    }

    /**
     * Connects to HQ, receives its sensor type, and then reports a measurement
     * every INTERVAL_MS until the program is stopped or the connection breaks.
     *
     * Socket, writer and reader are all closed by try-with-resources.
     */
    private void start() {
        report("Sensor booting up, contacting HQ at " + HOST + ":" + PORT + "...");

        try (Socket socket = new Socket(HOST, PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Only used while waiting for the assignment
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            if (!receiveAssignment(in)) {
                return; // HQ refused us, or answered something unexpected
            }
            socket.setSoTimeout(0); // no timeout while measuring

            startResponseListener(in);
            reportMeasurements(out);
        } catch (SocketTimeoutException e) {
            // Must be caught before IOException, which it inherits from
            report("[ERROR] HQ did not assign a sensor type within "
                    + HANDSHAKE_TIMEOUT_MS / 1000 + " seconds.");
        } catch (ConnectException e) {
            report("[ERROR] Could not reach HQ at " + HOST + ":" + PORT + " - is the server running?");
        } catch (IOException e) {
            report("[ERROR] Lost connection to HQ: " + e.getMessage());
        } catch (InterruptedException e) {
            // Restore the interrupt flag so the thread state stays correct
            Thread.currentThread().interrupt();
            report("Sensor stopped.");
        }
    }

    /**
     * Reads HQ's first line and stores the assigned type and id.
     * Returns false if HQ turned the sensor away or sent something unexpected.
     */
    private boolean receiveAssignment(BufferedReader in) throws IOException {
        String reply = in.readLine();

        if (reply == null) {
            report("[ERROR] HQ closed the connection before assigning a sensor type.");
            return false;
        }
        if (reply.startsWith(SensorProtocol.REJECTED_PREFIX)) {
            report("[ERROR] HQ turned the sensor away: "
                    + reply.substring(SensorProtocol.REJECTED_PREFIX.length()));
            return false;
        }
        if (!reply.startsWith(SensorProtocol.ASSIGNED_PREFIX)) {
            report("[ERROR] Unexpected reply from HQ: " + reply);
            return false;
        }

        // The payload looks like "O2;SENSOR-02"
        String[] parts = reply.substring(SensorProtocol.ASSIGNED_PREFIX.length())
                .split(SensorProtocol.ID_SEPARATOR);
        if (parts.length != 2) {
            report("[ERROR] Could not understand the assignment from HQ: " + reply);
            return false;
        }

        try {
            type = SensorType.valueOf(parts[0].trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            report("[ERROR] HQ assigned an unknown sensor type: " + parts[0]);
            return false;
        }
        sensorId = parts[1].trim();

        report("Assigned by HQ: " + type.getDescription()
                + ", reporting every " + INTERVAL_MS / 1000 + " seconds, safe range " + type.formatRange());
        return true;
    }

    /** Sends one measurement every INTERVAL_MS until the sensor is stopped. */
    private void reportMeasurements(PrintWriter out) throws InterruptedException {
        while (true) {
            String message = buildMessage();
            out.println(message);
            report("Sent: " + message);
            Thread.sleep(INTERVAL_MS);
        }
    }

    /**
     * Starts a background thread that prints whatever HQ sends back, for
     * example an ALARM. It runs as a daemon thread so it cannot keep the
     * program alive after the sensor has stopped.
     */
    private void startResponseListener(BufferedReader in) {
        Thread listener = new Thread(() -> {
            try {
                String response;
                while ((response = in.readLine()) != null) {
                    report("HQ says: " + response);
                }
            } catch (IOException e) {
                // Expected when the socket is closed while we are blocked in readLine
            }
        }, sensorId + "-listener");
        listener.setDaemon(true);
        listener.start();
    }

    /** Builds one protocol message, e.g. "SENSOR-02;O2:21.4". */
    private String buildMessage() {
        return SensorProtocol.buildMeasurement(sensorId, type, formatValue(generateValue()));
    }

    /**
     * Generates a random value for this sensor's type.
     *
     * The intervals are deliberately a bit wider than the safe range, so most
     * readings are normal but some fall outside and can trigger an alarm.
     */
    private double generateValue() {
        return switch (type) {
            case TEMP -> randomBetween(-20.0, 40.0);       // safe: -15 to 35
            case O2 -> randomBetween(18.0, 24.0);          // safe: 19 to 23
            case PRESSURE -> randomBetween(780.0, 1120.0); // safe: 800 to 1100
            case CO2 -> randomBetween(300.0, 2500.0);      // safe: below 2000
        };
    }

    /** Returns a random value in the interval [min, max]. */
    private static double randomBetween(double min, double max) {
        return min + RANDOM.nextDouble() * (max - min);
    }

    /**
     * Formats the value as text. Locale.US is used on purpose, so the decimal
     * separator is always a dot - a Danish locale would produce "27,4", which
     * the server could not parse as a number.
     */
    private String formatValue(double value) {
        return switch (type) {
            // Temperature and oxygen are reported with one decimal
            case TEMP, O2 -> String.format(Locale.US, "%.1f", value);
            // Pressure and CO2 are reported as whole numbers
            case PRESSURE, CO2 -> String.format(Locale.US, "%.0f", value);
        };
    }

    /** Prints a message prefixed with this sensor's id. */
    private void report(String message) {
        System.out.println("[" + sensorId + "] " + message);
    }
}
