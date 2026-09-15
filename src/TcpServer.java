import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TcpServer {
    private static final int DEFAULT_PORT = 5000;

    public static void main(String[] args) {
        int port = readPort(args);
        ClientRegistry clientRegistry = new ClientRegistry();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Starter server på port " + port);
            System.out.println("Serveren venter på klienter.");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread handler = new Thread(() -> handleClient(clientSocket, clientRegistry));
                handler.start();
            }
        } catch (IOException e) {
            System.err.println("Serverfejl: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket, ClientRegistry clientRegistry) {
        try (Socket socket = clientSocket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

            System.out.println("Klient forbundet: " + socket.getRemoteSocketAddress());
            String currentUser = "";
            String clientMessage = reader.readLine();

            if (clientMessage == null) {
                return;
            }

            try {
                Message message = Protocol.parse(clientMessage);
                System.out.println("Modtaget fra klient: " + clientMessage + " -> " + message);

                switch (message.getType().toUpperCase()) {
                    case "LOGIN":
                        currentUser = message.getTarget();
                        boolean registered = clientRegistry.registerUser(currentUser);
                        if (registered) {
                            System.out.println("Bruger logget ind: " + currentUser);
                            System.out.println("Aktive brugere: " + clientRegistry.getUsers());
                        } else {
                            System.out.println("Brugernavnet er allerede registreret: " + currentUser);
                        }
                        break;
                    case "TEXT":
                        System.out.println("Handling TEXT: payload=" + message.getPayload());
                        break;
                    case "QUIT":
                        System.out.println("Handling QUIT");
                        break;
                    default:
                        System.out.println("Ukendt kommando: " + message.getType());
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Ugyldig besked fra klient: " + e.getMessage());
            }

            String ackSender = currentUser == null || currentUser.isBlank() ? "server" : currentUser;
            ServerMessage serverMessage = new ServerMessage(null, "ACK", ackSender, "", "Connected to chat server");
            String formattedReply = Protocol.formatServerMessage(serverMessage);
            writer.println(formattedReply);
            System.out.println("Bekræftelse sendt til klienten: " + formattedReply);
        } catch (IOException e) {
            System.err.println("Fejl i klientforbindelse: " + e.getMessage());
        }
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
}
