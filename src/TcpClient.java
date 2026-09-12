import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TcpClient {
    /*private static final String HOST = "87.57.243.106";
    private static final int PORT = 42722;*/

    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    public static void main(String[] args) throws IOException {
        System.out.println("Forbinder til " + HOST + ":" + PORT);

        try (Socket socket = new Socket(HOST, PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

            System.out.println("Forbindelsen er oprettet");

            writer.println("CLIENT_HELLO");
            String response = reader.readLine();
            if (response != null) {
               System.out.println("Serverbesked: " + response);
            }

        } catch (ConnectException exception) {
            System.err.println("Kunne ikke forbinde");
        } catch (IOException exception) {
            System.err.println("Klientfejl: " + exception.getMessage());
        }
    }
}


