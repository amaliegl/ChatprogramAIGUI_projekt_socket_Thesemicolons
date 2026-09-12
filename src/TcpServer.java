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
        System.out.println("Starter server på port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Serveren venter på en klient.");

            try (Socket clientSocket = serverSocket.accept();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8)) {

                System.out.println("Klient forbundet: " + clientSocket.getRemoteSocketAddress());

                String clientMessage = reader.readLine();
                if (clientMessage != null) {
                    System.out.println("Modtaget fra klient: " + clientMessage);
                }

                writer.println("Connected to chat server");
                System.out.println("Bekræftelse sendt til klienten");

            }
        } catch (IOException e) {
            System.err.println("Serverfejl: " + e.getMessage());
        }
        System.out.println("Serveren er stoppet.");
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
