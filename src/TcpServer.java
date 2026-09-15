import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TcpServer {
    private static final int DEFAULT_PORT = 5001;

    public static void main(String[] args) {
        int port = readPort(args);
        ClientRegistry clientRegistry = new ClientRegistry();
        ChatRoomManager chatRoomManager = new ChatRoomManager();

        // Create the default "alle" chatroom
        chatRoomManager.createChatroom("alle");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Starter server på port " + port);
            System.out.println("Serveren venter på klienter.");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread clientThread = new Thread(() -> handleClient(clientSocket, clientRegistry, chatRoomManager));
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("Serverfejl: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket, ClientRegistry clientRegistry, ChatRoomManager chatRoomManager) {
        try (Socket socket = clientSocket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

            System.out.println("Klient forbundet: " + socket.getRemoteSocketAddress());
            String currentUser = "";
            // Track which chatrooms this user has joined
            Set<String> joinedChatrooms = ConcurrentHashMap.newKeySet();

            while (true) {
                String clientMessage = reader.readLine();
                if (clientMessage == null) {
                    // Forbindelsen er brudt — fjern bruger fra registry og chatrooms hvis sat
                    if (currentUser != null && !currentUser.isBlank()) {
                        boolean removed = clientRegistry.unregisterUser(currentUser);
                        if (removed) {
                            System.out.println("Bruger fjernet pga. forbindelse lukket: " + currentUser);
                            System.out.println("Aktive brugere: " + clientRegistry.getUsers());
                        }
                        // Remove user from all chatrooms
                        for (String chatroomName : joinedChatrooms) {
                            Chatroom room = chatRoomManager.getChatroom(chatroomName);
                            if (room != null) {
                                room.removeMember(currentUser);
                            }
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
                                String targetForError = requestedUser == null ? "" : requestedUser;
                                sendServerReply(writer, "ERROR", "server", targetForError, "brugernavn optaget");
                                break;
                            }

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
                                // Auto-join the "alle" chatroom
                                Chatroom alleChatroom = chatRoomManager.getChatroom("alle");
                                if (alleChatroom != null) {
                                    alleChatroom.addMember(currentUser, writer);
                                    joinedChatrooms.add("alle");
                                }
                                sendServerReply(writer, "ACK", currentUser, "", "Brugernavn godkendt");
                            } else {
                                System.out.println("Brugernavnet kunne ikke registreres: " + currentUser);
                                String targetForError = currentUser == null ? "" : currentUser;
                                currentUser = "";
                                sendServerReply(writer, "ERROR", "server", targetForError, "brugernavn optaget");
                            }
                            break;
                        case "CREATE_ROOM":
                            // Format: CREATE_ROOM|roomname|
                            String newRoomName = message.getTarget();
                            if (newRoomName == null || newRoomName.isBlank()) {
                                sendServerReply(writer, "ERROR", "server", currentUser, "Chatrummets navn kan ikke være tomt");
                                break;
                            }
                            if (chatRoomManager.createChatroom(newRoomName)) {
                                System.out.println("Chatrum oprettet: " + newRoomName);
                                sendServerReply(writer, "ACK", "server", currentUser, "Chatrum '" + newRoomName + "' oprettet");
                            } else {
                                sendServerReply(writer, "ERROR", "server", currentUser, "Chatrum eksisterer allerede");
                            }
                            break;
                        case "JOIN":
                            // Format: JOIN|roomname|
                            String joinRoomName = message.getTarget();
                            if (joinRoomName == null || joinRoomName.isBlank()) {
                                sendServerReply(writer, "ERROR", "server", currentUser, "Chatrummets navn kan ikke være tomt");
                                break;
                            }
                            if (!chatRoomManager.existsChatroom(joinRoomName)) {
                                sendServerReply(writer, "ERROR", "server", currentUser, "Chatrum eksisterer ikke");
                                break;
                            }
                            Chatroom joinRoom = chatRoomManager.getChatroom(joinRoomName);
                            if (joinRoom != null && joinRoom.addMember(currentUser, writer)) {
                                joinedChatrooms.add(joinRoomName.toLowerCase());
                                System.out.println(currentUser + " joiner chatrum: " + joinRoomName);
                                sendServerReply(writer, "ACK", "server", currentUser, "Du er nu medlem af '" + joinRoomName + "'");
                            } else {
                                sendServerReply(writer, "ERROR", "server", currentUser, "Du er allerede medlem af dette chatrum");
                            }
                            break;
                        case "TEXT":
                            // Format: TEXT|roomname|payload
                            String targetRoom = message.getTarget();
                            if (targetRoom == null || targetRoom.isBlank()) {
                                sendServerReply(writer, "ERROR", "server", currentUser, "Chatrummets navn kan ikke være tomt");
                                break;
                            }
                            if (!chatRoomManager.existsChatroom(targetRoom)) {
                                sendServerReply(writer, "ERROR", "server", currentUser, "Chatrum eksisterer ikke");
                                break;
                            }
                            Chatroom textRoom = chatRoomManager.getChatroom(targetRoom);
                            if (textRoom != null && !textRoom.isMember(currentUser)) {
                                sendServerReply(writer, "ERROR", "server", currentUser, "Du er ikke medlem af dette chatrum");
                                break;
                            }
                            if (textRoom != null) {
                                boolean broadcastSuccess = textRoom.broadcastMessage(currentUser, message.getPayload());
                                if (broadcastSuccess) {
                                    sendServerReply(writer, "ACK", currentUser, targetRoom, "Besked sendt");
                                    System.out.println("Besked sendt i chatrum '" + targetRoom + "' fra " + currentUser);
                                } else {
                                    sendServerReply(writer, "ERROR", "server", currentUser, "Det valgte chatrum er tomt");
                                }
                            }
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
                                ServerMessage forward = new ServerMessage(null, "PRIVAT", currentUser, targetUser, message.getPayload());
                                targetWriter.println(Protocol.formatServerMessage(forward));
                                sendServerReply(writer, "ACK", currentUser, targetUser, "Privat besked sendt");
                            }
                            break;
                        case "QUIT":
                            System.out.println("Handling QUIT");
                            if (currentUser != null && !currentUser.isBlank()) {
                                boolean removed = clientRegistry.unregisterUser(currentUser);
                                if (removed) {
                                    System.out.println("Bruger fjernet ved logout: " + currentUser);
                                    System.out.println("Aktive brugere: " + clientRegistry.getUsers());
                                }
                                // Remove user from all chatrooms
                                for (String chatroomName : joinedChatrooms) {
                                    Chatroom room = chatRoomManager.getChatroom(chatroomName);
                                    if (room != null) {
                                        room.removeMember(currentUser);
                                    }
                                }
                            }
                            sendServerReply(writer, "ACK", currentUser, "", "Du er logget ud");
                            shouldExit = true;
                            break;
                        default:
                            System.out.println("Ukendt kommando: " + message.getType());
                            sendServerReply(writer, "ERROR", "server", message.getTarget(), "Ukendt kommando");
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("Ugyldig besked fra klient: " + e.getMessage());
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
