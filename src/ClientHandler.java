import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles a single client connection. Extracted from previous TcpServer.handleClient logic
 * so that an ExecutorService can run many ClientHandler instances concurrently.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ClientRegistry clientRegistry;
    private final ChatRoomManager chatRoomManager;

    public ClientHandler(Socket socket, ClientRegistry clientRegistry, ChatRoomManager chatRoomManager) {
        this.socket = socket;
        this.clientRegistry = clientRegistry;
        this.chatRoomManager = chatRoomManager;
    }

    @Override
    public void run() {
        String currentUser = "";
        // Track which chatrooms this user has joined (normalized lowercase names)
        Set<String> joinedChatrooms = ConcurrentHashMap.newKeySet();

        try (Socket s = this.socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8)) {

            System.out.println("Klient forbundet: " + s.getRemoteSocketAddress());

            boolean shouldExit = false;
            while (!shouldExit) {
                String clientMessage = reader.readLine();
                if (clientMessage == null) {
                    // Connection closed by client
                    cleanupOnDisconnect(currentUser, joinedChatrooms);
                    break;
                }

                try {
                    Message message = Protocol.parse(clientMessage);
                    System.out.println("Modtaget fra klient: " + clientMessage + " -> " + message);

                    switch (message.getType().toUpperCase()) {
                        case "LOGIN":
                            String requestedUser = message.getTarget();
                            if (requestedUser == null || requestedUser.isBlank()) {
                                String targetForError = requestedUser == null ? "" : requestedUser;
                                TcpServer.sendServerReply(writer, "ERROR", "server", targetForError, "brugernavn optaget");
                                break;
                            }

                            // Atomically register user: clientRegistry.registerUser already uses putIfAbsent
                            if (clientRegistry.containsUser(requestedUser)) {
                                System.out.println("Brugernavnet er allerede registreret: " + requestedUser);
                                currentUser = "";
                                TcpServer.sendServerReply(writer, "ERROR", "server", requestedUser, "brugernavn optaget");
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
                                        // Do NOT notify members about join/leave for 'alle' — it is a global
                                        // broadcast channel and notifications would be noisy. Other chatrooms
                                        // still receive join/leave notifications.
                                }
                                TcpServer.sendServerReply(writer, "LOGIN", currentUser, "", "Brugernavn godkendt");
                            } else {
                                System.out.println("Brugernavnet kunne ikke registreres: " + currentUser);
                                String targetForError = currentUser == null ? "" : currentUser;
                                currentUser = "";
                                TcpServer.sendServerReply(writer, "ERROR", "server", targetForError, "brugernavn optaget");
                            }
                            break;
                        case "CREATE_ROOM":
                            String newRoomName = message.getTarget();
                            if (newRoomName == null || newRoomName.isBlank()) {
                                TcpServer.sendServerReply(writer, "ERROR", "server", currentUser, "Chatrummets navn kan ikke være tomt");
                                break;
                            }
                            if (chatRoomManager.createChatroom(newRoomName)) {
                                System.out.println("Chatrum oprettet: " + newRoomName);
                                TcpServer.sendServerReply(writer, "NYT_CHATRUM", "server", currentUser, "Chatrum '" + newRoomName + "' oprettet");
                            } else {
                                TcpServer.sendServerReply(writer, "ERROR", "server", currentUser, "Chatrum eksisterer allerede");
                            }
                            break;
                        case "JOIN":
                            String joinRoomName = message.getTarget();
                            if (joinRoomName == null || joinRoomName.isBlank()) {
                                TcpServer.sendServerReply(writer, "ERROR", "server", currentUser, "Chatrummets navn kan ikke være tomt");
                                break;
                            }
                            if (!chatRoomManager.existsChatroom(joinRoomName)) {
                                TcpServer.sendServerReply(writer, "ERROR", "server", currentUser, "Chatrum eksisterer ikke");
                                break;
                            }
                            Chatroom joinRoom = chatRoomManager.getChatroom(joinRoomName);
                            if (joinRoom != null && joinRoom.addMember(currentUser, writer)) {
                                joinedChatrooms.add(joinRoomName.toLowerCase());
                                System.out.println(currentUser + " joiner chatrum: " + joinRoomName);
                                TcpServer.sendServerReply(writer, "JOIN_CHATRUM", "server", currentUser, "Du er nu medlem af '" + joinRoomName + "'");
                                // Notify existing members (excluding the new member), except for 'alle'
                                if (!joinRoom.getName().equalsIgnoreCase("alle")) {
                                    ServerMessage joinNotif = new ServerMessage(null, "INFO", currentUser, joinRoomName, currentUser + " har tilsluttet sig " + joinRoomName + ".");
                                    joinRoom.notifyMembersExcept(currentUser, joinNotif);
                                }
                            } else {
                                TcpServer.sendServerReply(writer, "ERROR", "server", currentUser, "Du er allerede medlem af dette chatrum");
                            }
                            break;
                        case "LEAVE":
                            String leaveRoomName = message.getTarget();
                            if (leaveRoomName == null || leaveRoomName.isBlank()) {
                                TcpServer.sendServerReply(writer, "ERROR", "server", currentUser, "Chatrummets navn kan ikke være tomt");
                                break;
                            }
                            if (!chatRoomManager.existsChatroom(leaveRoomName)) {
                                TcpServer.sendServerReply(writer, "ERROR", "server", currentUser, "Chatrum eksisterer ikke");
                                break;
                            }

                            Chatroom leaveRoom = chatRoomManager.getChatroom(leaveRoomName);
                            if (leaveRoom == null) {
                                TcpServer.sendServerReply(writer, "ERROR", "server", currentUser, "Chatrum eksisterer ikke");
                                break;
                            }

                            // Check membership
                            if (!leaveRoom.isMember(currentUser)) {
                                TcpServer.sendServerReply(writer, "ERROR", "server", currentUser, "Du kan ikke forlade " + leaveRoomName + ", da du ikke er medlem af det");
                                break;
                            }

                            // Remove and notify remaining members atomically via chatroom helper
                            boolean removed = leaveRoom.removeMemberWithNotify(currentUser);
                            if (removed) {
                                // Update client's joined set
                                joinedChatrooms.remove(leaveRoomName.toLowerCase());
                                TcpServer.sendServerReply(writer, "LEAVE_CHATRUM", "server", currentUser, "Du har forladt '" + leaveRoomName + "'");
                            } else {
                                TcpServer.sendServerReply(writer, "ERROR", "server", currentUser, "Kunne ikke forlade chatrum");
                            }

                            break;
                        case "TEXT":
                            String targetRoom = message.getTarget();
                            if (targetRoom == null || targetRoom.isBlank()) {
                                TcpServer.sendServerReply(writer, "ERROR", "server", currentUser, "Chatrummets navn kan ikke være tomt");
                                break;
                            }
                            if (!chatRoomManager.existsChatroom(targetRoom)) {
                                TcpServer.sendServerReply(writer, "ERROR", "server", currentUser, "Chatrum eksisterer ikke");
                                break;
                            }
                            Chatroom textRoom = chatRoomManager.getChatroom(targetRoom);
                            if (textRoom != null && !textRoom.isMember(currentUser)) {
                                TcpServer.sendServerReply(writer, "ERROR", "server", currentUser, "Du er ikke medlem af dette chatrum");
                                break;
                            }
                            if (textRoom != null) {
                                boolean broadcastSuccess = textRoom.broadcastMessage(currentUser, message.getPayload());
                                if (broadcastSuccess) {
                                    TcpServer.sendServerReply(writer, "TEXT", currentUser, targetRoom, "Besked sendt");
                                    System.out.println("Besked sendt i chatrum '" + targetRoom + "' fra " + currentUser);
                                } else {
                                    TcpServer.sendServerReply(writer, "ERROR", "server", currentUser, "Det valgte chatrum er tomt");
                                }
                            }
                            break;
                        case "PRIVAT":
                            String targetUser = message.getTarget();
                            if (targetUser == null || targetUser.isBlank()) {
                                TcpServer.sendServerReply(writer, "ERROR", "server", currentUser == null ? "" : currentUser, "Den valgte modtager er ikke online");
                                break;
                            }

                            PrintWriter targetWriter = clientRegistry.getWriter(targetUser);
                            if (targetWriter == null) {
                                TcpServer.sendServerReply(writer, "ERROR", "server", currentUser == null ? "" : currentUser, "Den valgte modtager er ikke online");
                            } else {
                                ServerMessage forward = new ServerMessage(null, "PRIVAT", currentUser, targetUser, message.getPayload());
                                synchronized (targetWriter) {
                                    targetWriter.println(Protocol.formatServerMessage(forward));
                                }
                                TcpServer.sendServerReply(writer, "PRIVAT", currentUser, targetUser, "Privat besked sendt");
                            }
                            break;
                        case "QUIT":
                            System.out.println("Handling QUIT");
                            cleanupOnDisconnect(currentUser, joinedChatrooms);
                            TcpServer.sendServerReply(writer, "QUIT", currentUser, "", "Du er logget ud");
                            shouldExit = true;
                            break;
                        default:
                            System.out.println("Ukendt kommando: " + message.getType());
                            TcpServer.sendServerReply(writer, "ERROR", "server", message.getTarget(), "Ukendt kommando");
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("Ugyldig besked fra klient: " + e.getMessage());
                    String targetForError = (currentUser != null && !currentUser.isBlank()) ? currentUser : "";
                    TcpServer.sendServerReply(writer, "ERROR", "server", targetForError, "Ugyldig besked");
                }
            }
        } catch (IOException e) {
            System.err.println("Fejl i klientforbindelse: " + e.getMessage());
            // Ensure cleanup on unexpected IO error
            cleanupOnDisconnect(currentUser, joinedChatrooms);
        }
    }

    private void cleanupOnDisconnect(String currentUser, Set<String> joinedChatrooms) {
        if (currentUser != null && !currentUser.isBlank()) {
            // First unregister user from global registry so no new private messages are routed to them
            boolean removed = clientRegistry.unregisterUser(currentUser);
            if (removed) {
                System.out.println("Bruger fjernet ved disconnect: " + currentUser);
                System.out.println("Aktive brugere: " + clientRegistry.getUsers());
            }

            // Remove user from all chatrooms they joined and notify remaining members.
            // Use Chatroom.removeMemberWithNotify which performs removal and then sends notifications
            // to remaining members in a thread-safe manner.
            for (String chatroomName : joinedChatrooms) {
                Chatroom room = chatRoomManager.getChatroom(chatroomName);
                if (room != null) {
                    boolean removedFromRoom = room.removeMemberWithNotify(currentUser);
                    if (removedFromRoom) {
                        System.out.println("Bruger " + currentUser + " fjernet fra chatrum ved disconnect: " + chatroomName);
                    }
                }
            }
        }
    }
}
