// Default package - same as existing chat classes

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * Simple HTTP server to serve static files for the GUI.
 * Serves HTML, CSS, and JavaScript files from the presentation/web directory.
 */
public class StaticFileServer {
    private final int port;
    private final String webRoot;
    private com.sun.net.httpserver.HttpServer server;

    public StaticFileServer(int port, String webRoot) {
        this.port = port;
        this.webRoot = webRoot;
    }

    /**
     * Start the HTTP server
     */
    public void start() throws IOException {
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
        
        // Serve static files
        server.createContext("/", new StaticFileHandler(webRoot));
        
        // Use a thread pool for handling requests
        server.setExecutor(Executors.newCachedThreadPool());
        
        server.start();
        System.out.println("HTTP server started on port " + port);
        System.out.println("GUI available at http://localhost:" + port);
    }

    /**
     * Stop the HTTP server
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("HTTP server stopped");
        }
    }

    /**
     * Handler for serving static files
     */
    private static class StaticFileHandler implements HttpHandler {
        private final String webRoot;

        public StaticFileHandler(String webRoot) {
            this.webRoot = webRoot;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            // Default to index.html for root path
            if (path.equals("/")) {
                path = "/index.html";
            }

            // Resolve file path
            Path filePath = Paths.get(webRoot, path).normalize();
            
            // Security check: ensure file is within web root
            if (!filePath.startsWith(Paths.get(webRoot).normalize())) {
                sendResponse(exchange, 403, "Forbidden");
                return;
            }

            // Check if file exists
            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendResponse(exchange, 404, "Not Found");
                return;
            }

            // Determine content type
            String contentType = getContentType(filePath.toString());
            
            // Send file content
            byte[] fileContent = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, fileContent.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileContent);
            }
        }

        /**
         * Determine content type based on file extension
         */
        private String getContentType(String filePath) {
            if (filePath.endsWith(".html")) return "text/html";
            if (filePath.endsWith(".css")) return "text/css";
            if (filePath.endsWith(".js")) return "application/javascript";
            return "text/plain";
        }

        /**
         * Send error response
         */
        private void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
            String response = message;
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(statusCode, response.length());
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
}
