package org.example.mars.server;

import org.example.mars.service.MarsLogger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mars HQ server.
 *
 * Listens on a fixed port and hands every accepted sensor connection to a
 * thread pool, so the accept loop is free again immediately and several
 * sensors can be monitored at the same time.
 *
 * The number of sensors is limited by SensorRegistry, which allows one sensor
 * per sensor type, not by the size of the pool.
 */
public class MarsServer {

    private static final int PORT = 5000;
    private static final int POOL_SIZE = 5;

    public static void main(String[] args) {
        start();
    }

    /**
     * Opens the server socket and accepts sensor connections until the program
     * is stopped. Each connection is handed to the pool as a SensorHandler
     * task - the server itself never creates threads.
     */
    private static void start() {
        System.out.println("[HQ] Mars HQ starting up...");

        // One shared logger for all sensors. If the log file cannot be opened,
        // HQ refuses to start: monitoring without a log is not acceptable.
        MarsLogger logger;
        try {
            logger = new MarsLogger();
        } catch (IOException e) {
            System.out.println("[ERROR] HQ could not open the log file: " + e.getMessage());
            return;
        }

        // One shared registry, so two sensors can never be given the same type
        SensorRegistry registry = new SensorRegistry();

        ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[HQ] Listening for sensors on port " + PORT
                    + " with a pool of " + POOL_SIZE + " threads");
            logger.logEvent("Mars HQ started on port " + PORT);

            while (true) {
                // accept() blocks until a sensor connects
                Socket sensorSocket = serverSocket.accept();
                pool.submit(new SensorHandler(sensorSocket, logger, registry));
            }
        } catch (IOException e) {
            System.out.println("[ERROR] HQ could not listen on port " + PORT + ": " + e.getMessage());
        } finally {
            // Pool threads are not daemon threads: without this the program
            // would stay alive after the accept loop has failed.
            pool.shutdownNow();
            logger.close();
        }
    }
}
