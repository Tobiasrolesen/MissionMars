package org.example.mars.server;

import org.example.mars.model.SensorReading;
import org.example.mars.model.SensorType;
import org.example.mars.protocol.ReadingParser;
import org.example.mars.protocol.SensorProtocol;
import org.example.mars.service.AlarmService;
import org.example.mars.service.MarsLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Handles the communication with ONE sensor connection.
 *
 * One instance is created per accepted socket and handed to the server's
 * thread pool, so the server can keep accepting new sensors while this one
 * keeps sending measurements.
 *
 * The handler first assigns a sensor type to the new sensor, then reads its
 * measurements: each line is parsed, checked against the thresholds, printed,
 * and written to mars.log. A critical value is also sent back to the sensor.
 */
public class SensorHandler implements Runnable {

    /**
     * A sensor is expected to report every 5 seconds. After three missed
     * reports HQ gives up on it, so a silent or crashed sensor cannot keep a
     * pool thread occupied forever.
     */
    private static final int READ_TIMEOUT_MS = 15_000;

    private final Socket socket;
    private final String sensorAddress;
    private final AlarmService alarmService = new AlarmService();

    // Both are shared with every other handler
    private final MarsLogger logger;
    private final SensorRegistry registry;

    // Assigned during the handshake, released again when the sensor is gone
    private SensorType assignedType;
    private String sensorId;

    public SensorHandler(Socket socket, MarsLogger logger, SensorRegistry registry) {
        this.socket = socket;
        this.sensorAddress = socket.getRemoteSocketAddress().toString();
        this.logger = logger;
        this.registry = registry;
    }

    /**
     * Runs on a thread from the server's pool: assign a type, then read
     * measurements until the sensor disconnects. When the method returns, the
     * thread goes back to the pool and the sensor type is free again.
     */
    @Override
    public void run() {
        log("Sensor connected from " + sensorAddress);
        logger.logEvent("Sensor connected from " + sensorAddress);

        // Socket, reader and writer are all closed by try-with-resources
        try (Socket openSocket = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(openSocket.getInputStream()));
             PrintWriter out = new PrintWriter(openSocket.getOutputStream(), true)) {

            // Makes readLine() give up instead of blocking forever if the
            // sensor goes silent without closing the connection
            openSocket.setSoTimeout(READ_TIMEOUT_MS);

            if (!assignSensorType(out)) {
                return; // no free type, the sensor was turned away
            }
            readMeasurements(in, out);

            log("Sensor " + describeSensor() + " disconnected");
            logger.logEvent("Sensor " + describeSensor() + " disconnected");
        } catch (SocketTimeoutException e) {
            // Must be caught before IOException, which it inherits from
            reportError("Sensor " + describeSensor() + " lost connection (silent for "
                    + READ_TIMEOUT_MS / 1000 + " seconds).");
        } catch (IOException e) {
            reportError("Sensor " + describeSensor() + " lost connection: " + e.getMessage());
        } finally {
            // Always give the type back, also after an error
            registry.release(assignedType);
        }
    }

    /**
     * Claims a free sensor type for this sensor and tells it which type and id
     * it has been given, e.g. "ASSIGNED:O2;SENSOR-02".
     *
     * Returns false if every type is already covered; the sensor is then told
     * "REJECTED:..." and the connection is closed.
     */
    private boolean assignSensorType(PrintWriter out) {
        assignedType = registry.claimFreeType();

        if (assignedType == null) {
            out.println(SensorProtocol.buildRejection("all sensor types are already in use"));
            reportError("Sensor at " + sensorAddress + " was turned away: no free sensor type.");
            return false;
        }

        sensorId = SensorRegistry.idFor(assignedType);
        out.println(SensorProtocol.buildAssignment(assignedType, sensorId));

        String message = "Assigned " + assignedType.name() + " to " + sensorId
                + " (" + registry.countInUse() + " of " + SensorType.values().length + " types covered)";
        log(message);
        logger.logEvent(message);
        return true;
    }

    /**
     * Reads measurements until the sensor closes the connection.
     * readLine() returns null when that happens.
     */
    private void readMeasurements(BufferedReader in, PrintWriter out) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            handleLine(line, out);
        }
    }

    /**
     * Handles one received line: parse it, then either report an alarm or
     * confirm that the reading is fine. An unparsable line is reported as an
     * error and then ignored.
     */
    private void handleLine(String line, PrintWriter out) {
        SensorReading reading = ReadingParser.parse(line);

        if (reading == null) {
            reportError("Sensor " + describeSensor() + " sent invalid data: " + line);
            return;
        }

        if (alarmService.isCritical(reading)) {
            raiseAlarm(reading, out);
        } else {
            log(reading + " (ok)");
            logger.logReading(reading);
        }
    }

    /**
     * Prints the alarm at HQ, writes it to the log, and sends the same message
     * back to the sensor.
     */
    private void raiseAlarm(SensorReading reading, PrintWriter out) {
        String alarmMessage = alarmService.buildAlarmMessage(reading);
        log(reading.getSensorId() + " " + alarmMessage);
        logger.logAlarm(reading);
        out.println(alarmMessage);
    }

    /**
     * Returns the assigned sensor id, or the network address if the sensor has
     * not been assigned a type yet. Used so error messages name the sensor,
     * not just a port number.
     */
    private String describeSensor() {
        return sensorId != null ? sensorId : sensorAddress;
    }

    /** Reports a problem both in the console and in mars.log. */
    private void reportError(String message) {
        System.out.println("[ERROR] " + message);
        logger.logEvent("[ERROR] " + message);
    }

    /**
     * Prints a message prefixed with the name of the pool thread, so it is
     * visible that several sensors are handled by different threads.
     */
    private void log(String message) {
        System.out.println("[HQ][" + Thread.currentThread().getName() + "] " + message);
    }
}
