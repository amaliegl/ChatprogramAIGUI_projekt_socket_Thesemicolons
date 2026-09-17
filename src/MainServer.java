import java.io.IOException;

/**
 * MainServer - Starts HTTP and WebSocket servers for the GUI.
 * TCP server runs externally and is not started by this MainServer.
 * Thread-safe: Each server runs independently with proper shutdown hooks.
 */
public class MainServer {
    private static final int HTTP_PORT = 8080;
    private static final int WEBSOCKET_PORT = 8081;
    private static final String WEB_ROOT = "src/presentation/web";
    
    // External TCP server configuration
    private static final String EXTERNAL_SERVER_HOST = "localhost"; // Change to external server IP
    private static final int EXTERNAL_SERVER_PORT = 5003; // Change to external server port

    private StaticFileServer httpServer;
    private ChatWebSocketServer webSocketServer;

    public static void main(String[] args) {
        MainServer mainServer = new MainServer();
        mainServer.start();
    }

    /**
     * Start all servers
     */
    public void start() {
        try {
            // Start HTTP server (for serving GUI files)
            System.out.println("Starting HTTP server on port " + HTTP_PORT);
            startHttpServer();

            // Start WebSocket server (for GUI real-time communication)
            // Note: WebSocketServer will need to connect to external TCP server
            System.out.println("Starting WebSocket server on port " + WEBSOCKET_PORT);
            startWebSocketServer();

            System.out.println("\n=== Local servers started successfully ===");
            System.out.println("GUI: http://localhost:" + HTTP_PORT);
            System.out.println("WebSocket: ws://localhost:" + WEBSOCKET_PORT + "/chat");
            System.out.println("Note: TCP server runs externally");
            System.out.println("=========================================\n");

            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

            // Keep main thread alive
            Thread.currentThread().join();

        } catch (IOException e) {
            System.err.println("Failed to start servers: " + e.getMessage());
            stop();
        } catch (InterruptedException e) {
            System.err.println("Main thread interrupted");
            stop();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Start HTTP server for serving static GUI files
     */
    private void startHttpServer() throws IOException {
        httpServer = new StaticFileServer(HTTP_PORT, WEB_ROOT);
        httpServer.start();
    }

    /**
     * Start WebSocket server for GUI real-time communication
     * Note: WebSocketServer will act as a TCP client to the external server
     */
    private void startWebSocketServer() {
        // WebSocketServer will connect to external TCP server as a client
        webSocketServer = new ChatWebSocketServer(WEBSOCKET_PORT, EXTERNAL_SERVER_HOST, EXTERNAL_SERVER_PORT);
        webSocketServer.start();
    }

    /**
     * Stop all servers gracefully
     */
    public void stop() {
        System.out.println("\nShutting down servers...");

        if (httpServer != null) {
            httpServer.stop();
        }

        if (webSocketServer != null) {
            try {
                webSocketServer.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("All servers stopped");
    }
}
