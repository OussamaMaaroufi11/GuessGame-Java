import java.io.PrintWriter;
import java.net.Socket;

public class PeerConnection {

    private PeerConnection() {
        // classe utilitaire
    }

    public static void send(String host, int port, String rawMessage) {
        if (host == null || host.isBlank()) {
            System.out.println("[ERREUR] Hôte P2P invalide.");
            return;
        }

        if (port <= 0 || port > 65535) {
            System.out.println("[ERREUR] Port P2P invalide : " + port);
            return;
        }

        if (rawMessage == null || rawMessage.isBlank()) {
            System.out.println("[ERREUR] Message P2P vide.");
            return;
        }

        String cleanHost = host.trim();
        String cleanMessage = rawMessage.trim();

        try (
                Socket socket = new Socket(cleanHost, port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            out.println(cleanMessage);
            out.flush();

            System.out.println("\n=========== MESSAGE P2P ENVOYÉ ===========");
            System.out.println("Vers   : " + cleanHost + ":" + port);
            System.out.println("Brut   : " + cleanMessage);
            System.out.println("==========================================\n");

        } catch (Exception e) {
            System.out.println("[ERREUR] Envoi P2P : " + e.getMessage());
        }
    }
}