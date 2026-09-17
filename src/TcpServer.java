import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TcpServer now uses an ExecutorService to handle client connections via ClientHandler tasks.
 * This keeps the accept-loop lightweight and allows controlled concurrency.
 */
public class TcpServer {
    private static final int DEFAULT_PORT = 5000;
    private static final int DEFAULT_POOL_SIZE = 20; // configurable via env THREAD_POOL_SIZE

    public static void main(String[] args) {
        int port = readPort(args);
        int poolSize = readPoolSize();

        ClientRegistry clientRegistry = new ClientRegistry();
        ChatRoomManager chatRoomManager = new ChatRoomManager();

        // Ensure default chatroom exists
        chatRoomManager.createChatroom("alle");

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            ServerSocket finalServerSocket = serverSocket; // for lambda

            // Shutdown hook: close server socket and shutdown executor
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutdown initiated: closing server socket and executor");
                try {
                    finalServerSocket.close();
                } catch (IOException e) {
                    // ignore
                }
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException ex) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }));

            System.out.println("Starter server på port " + port);
            System.out.println("Serveren venter på klienter.");

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    // Submit a ClientHandler to the executor instead of creating a new Thread
                    executor.submit(new ClientHandler(clientSocket, clientRegistry, chatRoomManager));
                } catch (IOException e) {
                    if (serverSocket.isClosed()) break;
                    System.err.println("Fejl ved accept: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Serverfejl: " + e.getMessage());
        } finally {
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException ex) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            if (serverSocket != null && !serverSocket.isClosed()) {
                try { serverSocket.close(); } catch (IOException ignored) {}
            }
        }
    }

    // Helper to send replies to a single client. Synchronize writes on writer to avoid interleaved output.
    public static void sendServerReply(PrintWriter writer, String type, String sender, String target, String payload) {
        ServerMessage serverMessage = new ServerMessage(null, type, sender, target, payload);
        String formattedReply = Protocol.formatServerMessage(serverMessage);
        synchronized (writer) {
            writer.println(formattedReply);
        }
        System.out.println("Svar sendt til klienten: " + formattedReply);
    }

    private static int readPort(String[] args) {
        if (args.length == 0) {
            return DEFAULT_PORT;
        }

        try {
            return Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            System.err.println("Ugyldig port. Bruger standardport " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    private static int readPoolSize() {
        String v = System.getenv("THREAD_POOL_SIZE");
        if (v == null) return DEFAULT_POOL_SIZE;
        try { return Integer.parseInt(v); } catch (NumberFormatException ex) { return DEFAULT_POOL_SIZE; }
    }
}
