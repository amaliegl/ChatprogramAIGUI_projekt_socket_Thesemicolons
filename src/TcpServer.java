import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TcpServer {
    private static final int DEFAULT_PORT = 5001;

    public static void main(String[] args) {
        int port = readPort(args);
        ClientRegistry clientRegistry = new ClientRegistry();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Starter server på port " + port);
            System.out.println("Serveren venter på klienter.");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread clientThread = new Thread(() -> handleClient(clientSocket, clientRegistry));
                clientThread.start();
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

            while (true) {
                String clientMessage = reader.readLine();
                if (clientMessage == null) {
                    // Forbindelsen er brudt — fjern bruger fra registry hvis sat
                    if (currentUser != null && !currentUser.isBlank()) {
                        boolean removed = clientRegistry.unregisterUser(currentUser);
                        if (removed) {
                            System.out.println("Bruger fjernet pga. forbindelse lukket: " + currentUser);
                            System.out.println("Aktive brugere: " + clientRegistry.getUsers());
                        }
                    }
                    break;
                }

                boolean shouldExit = false;

                try {
                    Message message = Protocol.parse(clientMessage);
                    System.out.println("Modtaget fra klient: " + clientMessage + " -> " + message);

                    switch (message.getType().toUpperCase()) {
                        case "LOGIN":
                            String requestedUser = message.getTarget();
                            if (requestedUser == null || requestedUser.isBlank()) {
                                // Return target as what client attempted (empty string if none)
                                String targetForError = requestedUser == null ? "" : requestedUser;
                                sendServerReply(writer, "ERROR", "server", targetForError, "brugernavn optaget");
                                break;
                            }

                            // Brugernavnet skal være unikt, så vi afviser login, hvis navnet allerede er aktivt.
                            if (clientRegistry.containsUser(requestedUser)) {
                                System.out.println("Brugernavnet er allerede registreret: " + requestedUser);
                                currentUser = "";
                                sendServerReply(writer, "ERROR", "server", requestedUser, "brugernavn optaget");
                                break;
                            }

                            currentUser = requestedUser;
                            boolean registered = clientRegistry.registerUser(currentUser, writer);
                            if (registered) {
                                System.out.println("Bruger logget ind: " + currentUser);
                                System.out.println("Aktive brugere: " + clientRegistry.getUsers());
                                sendServerReply(writer, "ACK", currentUser, "", "Brugernavn godkendt");
                            } else {
                                System.out.println("Brugernavnet kunne ikke registreres: " + currentUser);
                                String targetForError = currentUser == null ? "" : currentUser;
                                currentUser = "";
                                sendServerReply(writer, "ERROR", "server", targetForError, "brugernavn optaget");
                            }
                            break;
                        case "TEXT":
                            System.out.println("Handling TEXT: target=" + message.getTarget() + ", payload=" + message.getPayload());
                            sendServerReply(writer, "ACK", currentUser, message.getTarget(), "Besked modtaget");
                            break;
                        case "PRIVAT":
                            System.out.println("Handling PRIVAT: target=" + message.getTarget() + ", payload=" + message.getPayload());
                            String targetUser = message.getTarget();
                            if (targetUser == null || targetUser.isBlank()) {
                                sendServerReply(writer, "ERROR", "server", currentUser == null ? "" : currentUser, "Den valgte modtager er ikke online");
                                break;
                            }

                            PrintWriter targetWriter = clientRegistry.getWriter(targetUser);
                            if (targetWriter == null) {
                                sendServerReply(writer, "ERROR", "server", currentUser == null ? "" : currentUser, "Den valgte modtager er ikke online");
                            } else {
                                // Forward the private message to the target client with sender set to currentUser
                                ServerMessage forward = new ServerMessage(null, "PRIVAT", currentUser, targetUser, message.getPayload());
                                targetWriter.println(Protocol.formatServerMessage(forward));
                                // Acknowledge to sender
                                sendServerReply(writer, "ACK", currentUser, targetUser, "Privat besked sendt");
                            }
                            break;
                        case "QUIT":
                            System.out.println("Handling QUIT");
                            // Fjern brugeren fra registry ved eksplicit logout
                            if (currentUser != null && !currentUser.isBlank()) {
                                boolean removed = clientRegistry.unregisterUser(currentUser);
                                if (removed) {
                                    System.out.println("Bruger fjernet ved logout: " + currentUser);
                                    System.out.println("Aktive brugere: " + clientRegistry.getUsers());
                                }
                            }
                            sendServerReply(writer, "ACK", currentUser, "", "Du er logget ud");
                            shouldExit = true;
                            break;
                        default:
                            System.out.println("Ukendt kommando: " + message.getType());
                            // Use message target as TARGET in ERROR
                            sendServerReply(writer, "ERROR", "server", message.getTarget(), "Ukendt kommando");
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("Ugyldig besked fra klient: " + e.getMessage());
                    // If client already has a name, use it as TARGET; otherwise leave blank
                    String targetForError = (currentUser != null && !currentUser.isBlank()) ? currentUser : "";
                    sendServerReply(writer, "ERROR", "server", targetForError, "Ugyldig besked");
                }

                if (shouldExit) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Fejl i klientforbindelse: " + e.getMessage());
        }
    }

    private static void sendServerReply(PrintWriter writer, String type, String sender, String target, String payload) {
        ServerMessage serverMessage = new ServerMessage(null, type, sender, target, payload);
        String formattedReply = Protocol.formatServerMessage(serverMessage);
        writer.println(formattedReply);
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
}
