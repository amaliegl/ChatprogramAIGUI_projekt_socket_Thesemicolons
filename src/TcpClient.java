import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TcpClient {
    private static final String HOST = "change to right ip";
    private static final int PORT = 9999; //change to right port

    public static void main(String[] args) throws IOException {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        System.out.println("Forbinder til " + HOST + ":" + PORT);

        try (Socket socket = new Socket(HOST, PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

            System.out.println("Forbindelsen er oprettet");

            boolean loggedIn = false;
            // Brugeren skal få en ny chance, så længe serveren afviser det valgte brugernavn.
            while (!loggedIn) {
               System.out.print("Indtast brugernavn: ");
               String username = consoleReader.readLine();

               if (username == null || username.isBlank()) {
                   System.out.println("Brugernavnet kan ikke være tomt.");
                   continue;
               }

               writer.println("LOGIN|" + username + "|");
               String response = reader.readLine();

               if (response == null) {
                   System.out.println("Serveren lukkede forbindelsen.");
                   return;
               }

               ServerMessage serverMessage = Protocol.parseServer(response);
               String payload = serverMessage.getPayload();
               String type = serverMessage.getType();

               System.out.println(response);

               if ("ERROR".equalsIgnoreCase(type) && "brugernavn optaget".equalsIgnoreCase(payload)) {
                   System.out.println("Brugernavnet er optaget. Prøv igen.");
                   continue;
               }

               if ("LOGIN".equalsIgnoreCase(type)) {
                   loggedIn = true;
               }
            }

            // Start a background listener thread to print any incoming server messages asynchronously
            Thread listener = new Thread(() -> {
                try {
                    String incoming;
                    while ((incoming = reader.readLine()) != null) {
                        System.out.println(incoming);
                    }
                } catch (IOException e) {
                    // Listener ends when connection is closed
                }
            });
            listener.setDaemon(true);
            listener.start();

            while (true) {
               printMenu();
               String input = consoleReader.readLine();
               int choice = parseMenuChoice(input);

               // Quit (now option 6)
               if (choice == 6) {
                   writer.println("QUIT||");
                   System.out.println("Du er logget ud.");
                   // Let listener handle server responses (ACK). Close socket by exiting main's try-with-resources.
                   break;
               }

               // Send private or room text
               if (choice == 1 || choice == 2) {
                   System.out.print("Indtast mål: ");
                   String target = consoleReader.readLine();
                   System.out.print("Skriv besked: ");
                   String payload = consoleReader.readLine();

                   String protocolMessage = buildProtocolMessage(choice, target, payload);
                   writer.println(protocolMessage);

                   // Responses (ACK or forwarded messages) will be printed by the listener thread.
                   continue;
               }

               if (choice == 3) {
                   // Create new chatroom
                   System.out.print("Indtast navn på nyt chatrum: ");
                   String roomName = consoleReader.readLine();
                   if (roomName == null || roomName.isBlank()) {
                       System.out.println("Chatrummets navn kan ikke være tomt.");
                       continue;
                   }
                   writer.println("CREATE_ROOM|" + roomName + "|");
                   // Response will be printed by listener thread
                   continue;
               }

               if (choice == 4) {
                   // Join existing chatroom
                   System.out.print("Indtast navn på chatrum at joine: ");
                   String roomName = consoleReader.readLine();
                   if (roomName == null || roomName.isBlank()) {
                       System.out.println("Chatrummets navn kan ikke være tomt.");
                       continue;
                   }
                   writer.println("JOIN|" + roomName + "|");
                   // Response will be printed by listener thread
                   continue;
               }

               if (choice == 5) {
                   // Leave existing chatroom
                   System.out.print("Indtast navn på chatrum at forlade: ");
                   String roomName = consoleReader.readLine();
                   if (roomName == null || roomName.isBlank()) {
                       System.out.println("Chatrummets navn kan ikke være tomt.");
                       continue;
                   }
                   writer.println("LEAVE|" + roomName + "|");
                   // Response will be printed by listener thread
                   continue;
               }

               System.out.println("Ugyldigt valg. Prøv igen.");
            }
        } catch (ConnectException exception) {
            System.err.println("Kunne ikke forbinde");
        } catch (IOException exception) {
            System.err.println("Klientfejl: " + exception.getMessage());
        }
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("Menu:");
        System.out.println("1. Send privat chatbesked");
        System.out.println("2. Send fællesbesked til chatrum");
        System.out.println("3. Opret nyt chatrum");
        System.out.println("4. Joine eksisterende chatrum");
        System.out.println("5. Forlad et chatrum");
        System.out.println("6. Log ud");
        System.out.print("Vælg en handling: ");
    }

    private static int parseMenuChoice(String input) {
        try {
            return Integer.parseInt(input.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static String buildProtocolMessage(int choice, String target, String payload) {
        String safeTarget = target == null ? "" : target.trim();
        String safePayload = payload == null ? "" : payload.trim();
        if (choice == 1) {
            return "PRIVAT|" + safeTarget + "|" + safePayload;
        }
        // choice 2 -> TEXT to chatroom
        return "TEXT|" + safeTarget + "|" + safePayload;
    }
}
