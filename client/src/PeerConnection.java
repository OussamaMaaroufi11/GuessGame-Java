import java.io.IOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class PeerConnection {

    private PeerConnection() {
        // classe utilitaire
    }

    public static void send(String host, int port, String rawMessage) {
        if (host == null || host.isBlank()) {
            System.out.println("[ERREUR] IP du peer invalide.");
            return;
        }

        if (port <= 0 || port > 65535) {
            System.out.println("[ERREUR] Port du peer invalide : " + port);
            return;
        }

        if (rawMessage == null || rawMessage.isBlank()) {
            System.out.println("[ERREUR] Message P2P vide.");
            return;
        }

        String cleanHost = host.trim();
        String cleanMessage = rawMessage.trim();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(cleanHost, port), 3000);

            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println(cleanMessage);
                out.flush();
            }

            System.out.println("\n=========== MESSAGE P2P ENVOYÉ ===========");
            System.out.println("Vers   : " + cleanHost + ":" + port);
            System.out.println("Brut   : " + cleanMessage);
            System.out.println("==========================================\n");

        } catch (UnknownHostException e) {
            System.out.println("[ERREUR] IP du peer incorrecte ou introuvable.");
        } catch (SocketTimeoutException e) {
            System.out.println("[ERREUR] Délai dépassé : peer inaccessible.");
        } catch (ConnectException e) {
            System.out.println("[ERREUR] Aucun client P2P n'écoute sur ce port.");
        } catch (IOException e) {
            String msg = e.getMessage();

            if (msg != null && (
                    msg.contains("No route to host")
                            || msg.contains("Network is unreachable")
            )) {
                System.out.println("[ERREUR] Impossible de joindre ce peer sur le réseau.");
            } else {
                System.out.println("[ERREUR] Envoi P2P impossible : " + msg);
            }
        }
    }
}