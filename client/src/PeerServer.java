import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class PeerServer extends Thread {
    private final int port;

    public PeerServer(int port) {
        this.port = port;
        setName("PeerServer-" + port);
        setDaemon(true);
    }

    @Override
    public void run() {
        if (port <= 0 || port > 65535) {
            System.out.println("[ERREUR] Port P2P invalide : " + port);
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[P2P] PeerServer actif sur le port " + port);

            while (!isInterrupted()) {
                try (
                        Socket peerSocket = serverSocket.accept();
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(peerSocket.getInputStream()))
                ) {
                    String remoteHost = peerSocket.getInetAddress().getHostAddress();
                    int remotePort = peerSocket.getPort();
                    String raw = in.readLine();

                    displayPeerMessage(remoteHost, remotePort, raw);

                } catch (IOException e) {
                    if (!isInterrupted()) {
                        System.out.println("[ERREUR] Connexion peer : " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("[ERREUR] PeerServer : " + e.getMessage());
        }
    }

    private void displayPeerMessage(String remoteHost, int remotePort, String raw) {
        System.out.println("\n=========== MESSAGE P2P REÇU ===========");
        System.out.println("Depuis : " + remoteHost + ":" + remotePort);
        System.out.println("Brut   : " + raw);

        if (raw == null || raw.isBlank()) {
            System.out.println("Type   : VIDE");
            System.out.println("========================================\n");
            return;
        }

        if (raw.startsWith("P2P|")) {
            displayCustomP2PMessage(raw);
            System.out.println("========================================\n");
            return;
        }

        if (raw.startsWith("GG|")) {
            displayGGMessage(raw);
            System.out.println("========================================\n");
            return;
        }

        System.out.println("Type   : TEXTE_LIBRE");
        System.out.println("Message: " + raw);
        System.out.println("========================================\n");
    }

    private void displayCustomP2PMessage(String raw) {
        String[] parts = raw.split("\\|", -1);

        if (parts.length < 2) {
            System.out.println("Type   : P2P_INVALIDE");
            return;
        }

        System.out.println("Type   : " + parts[1]);

        if (parts.length == 2) {
            System.out.println("Champs : aucun");
            return;
        }

        for (int i = 2; i < parts.length; i++) {
            System.out.println("Champ[" + (i - 2) + "] = " + parts[i]);
        }
    }

    private void displayGGMessage(String raw) {
        GGMessage message = MessageParser.parse(raw);

        if (message == null) {
            System.out.println("Type   : MESSAGE_GG_INVALIDE");
            return;
        }

        System.out.println("Type   : MESSAGE_GG_REÇU");
        System.out.println("Commande détectée : " + message.getType());
        System.out.println("Info   : les commandes GG|... sont réservées au serveur TCP.");

        if (message.getFieldCount() == 0) {
            System.out.println("Champs : aucun");
            return;
        }

        for (int i = 0; i < message.getFieldCount(); i++) {
            System.out.println("Champ[" + i + "] = " + message.getField(i));
        }
    }
}