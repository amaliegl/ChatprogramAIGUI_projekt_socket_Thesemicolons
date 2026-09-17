// Default package - same as existing chat classes

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket server for real-time communication with the GUI.
 * Acts as a TCP client to an external chat server.
 * Thread-safe implementation using ConcurrentHashMap for WebSocket connections.
 */
public class ChatWebSocketServer extends WebSocketServer {
    private final String externalServerHost;
    private final int externalServerPort;
    
    // Map WebSocket connection -> TCP socket for tracking connected GUI clients
    private final Set<WebSocket> connections = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<WebSocket, Socket> connectionToTcpSocket = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocket, PrintWriter> connectionToTcpWriter = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocket, BufferedReader> connectionToTcpReader = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocket, Thread> connectionToListenerThread = new ConcurrentHashMap<>();

    public ChatWebSocketServer(int port, String externalServerHost, int externalServerPort) {
        super(new InetSocketAddress(port));
        this.externalServerHost = externalServerHost;
        this.externalServerPort = externalServerPort;
    }

    /**
     * Constructor for backward compatibility - uses default external server settings
     */
    public ChatWebSocketServer(int port, ClientRegistry unused1, ChatRoomManager unused2) {
        this(port, "localhost", 5000); // Default to localhost:5000 for external server
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connections.add(conn);
        System.out.println("WebSocket connection opened: " + conn.getRemoteSocketAddress());
        
        // Establish TCP connection to external server for this GUI client
        try {
            Socket tcpSocket = new Socket(externalServerHost, externalServerPort);
            PrintWriter tcpWriter = new PrintWriter(tcpSocket.getOutputStream(), true, StandardCharsets.UTF_8);
            BufferedReader tcpReader = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream(), StandardCharsets.UTF_8));
            
            connectionToTcpSocket.put(conn, tcpSocket);
            connectionToTcpWriter.put(conn, tcpWriter);
            connectionToTcpReader.put(conn, tcpReader);
            
            // Start listener thread for messages from external server
            Thread listenerThread = new Thread(() -> listenToTcpServer(conn, tcpReader));
            listenerThread.setDaemon(true);
            listenerThread.start();
            connectionToListenerThread.put(conn, listenerThread);
            
            System.out.println("TCP connection established to external server for GUI client");
        } catch (IOException e) {
            System.err.println("Failed to connect to external server: " + e.getMessage());
            sendError(conn, "Kunne ikke forbinde til serveren");
            conn.close();
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connections.remove(conn);
        
        // Cleanup TCP connection
        Socket tcpSocket = connectionToTcpSocket.remove(conn);
        PrintWriter tcpWriter = connectionToTcpWriter.remove(conn);
        BufferedReader tcpReader = connectionToTcpReader.remove(conn);
        Thread listenerThread = connectionToListenerThread.remove(conn);
        
        if (tcpSocket != null) {
            try {
                // Send QUIT before closing
                if (tcpWriter != null) {
                    tcpWriter.println("QUIT||");
                }
                tcpSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing TCP socket: " + e.getMessage());
            }
        }
        
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        
        System.out.println("WebSocket connection closed: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("WebSocket message received: " + message);
        
        // Forward message to external TCP server
        PrintWriter tcpWriter = connectionToTcpWriter.get(conn);
        if (tcpWriter != null) {
            tcpWriter.println(message);
        } else {
            sendError(conn, "Ikke forbundet til serveren");
        }
    }

    /**
     * Listen for messages from external TCP server and forward to GUI client
     * Thread-safe: Runs in separate thread per WebSocket connection
     */
    private void listenToTcpServer(WebSocket conn, BufferedReader tcpReader) {
        try {
            String line;
            while ((line = tcpReader.readLine()) != null) {
                System.out.println("Received from external server: " + line);
                // Forward to GUI client via WebSocket
                if (conn.isOpen()) {
                    conn.send(line);
                } else {
                    break;
                }
            }
        } catch (IOException e){
            System.err.println("Error reading from TCP server: " + e.getMessage());
        } finally {
            if (conn.isOpen()) {
                conn.close();
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WebSocket error: " + ex.getMessage());
        if (conn != null) {
            onClose(conn, -1, ex.getMessage(), false);
        }
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port " + getPort());
        System.out.println("Will connect to external TCP server at " + externalServerHost + ":" + externalServerPort);
    }

    /**
     * Send an error message to a WebSocket connection
     */
    private void sendError(WebSocket conn, String error) {
        // Send error in server message format
        String errorMessage = "null|ERROR|server||" + error;
        conn.send(errorMessage);
    }
}
