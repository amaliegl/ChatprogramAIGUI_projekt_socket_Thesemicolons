import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TcpClient {
    private static final String HOST = "localhost";
    private static final int PORT = 5001;

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

               System.out.println("Serverbesked: " + response);

               if ("ERROR".equalsIgnoreCase(type) && "brugernavn optaget".equalsIgnoreCase(payload)) {
                   System.out.println("Brugernavnet er optaget. Prøv igen.");
                   continue;
               }

               if ("ACK".equalsIgnoreCase(type)) {
                   loggedIn = true;
               }
            }

            // Start a background listener thread to print any incoming server messages asynchronously
            Thread listener = new Thread(() -> {
                try {
                    String incoming;
                    while ((incoming = reader.readLine()) != null) {
                        System.out.println("Serverbesked: " + incoming);
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

               if (choice == 3) {
                   writer.println("QUIT||");
                   System.out.println("Du er logget ud.");
                   // Let listener handle server responses (ACK). Close socket by exiting main's try-with-resources.
                   break;
               }

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
        System.out.println("3. Log ud");
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
        return "TEXT|" + safeTarget + "|" + safePayload;
    }
}
