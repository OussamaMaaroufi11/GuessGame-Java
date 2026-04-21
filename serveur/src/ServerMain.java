import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerMain {
    private static final int DEFAULT_PORT = 5050;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        if (args.length >= 1 && args[0] != null && !args[0].isBlank()) {
            try {
                port = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException e) {
                System.out.println("[AVERTISSEMENT] Port invalide. Utilisation du port par défaut : " + DEFAULT_PORT);
                port = DEFAULT_PORT;
            }
        }

        if (port <= 0 || port > 65535) {
            System.out.println("[AVERTISSEMENT] Port hors intervalle. Utilisation du port par défaut : " + DEFAULT_PORT);
            port = DEFAULT_PORT;
        }

        RoomManager roomManager = new RoomManager();
        Map<String, PlayerInfo> connectedPlayers = new ConcurrentHashMap<>();

        roomManager.createDefaultRoom("Summer", 5, 10);
        roomManager.createDefaultRoom("Winter", 5, 5);

        System.out.println("====================================");
        System.out.println(" Guess Game Server démarré");
        System.out.println(" Port : " + port);
        System.out.println("====================================");

        printPreferredServerAddress(port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[INFO] Serveur en écoute sur le port " + port);
            System.out.println("====================================");

            while (true) {
                Socket clientSocket = serverSocket.accept();

                System.out.println("[INFO] Nouveau client connecté : "
                        + clientSocket.getInetAddress().getHostAddress()
                        + ":" + clientSocket.getPort());

                ClientHandler handler = new ClientHandler(clientSocket, roomManager, connectedPlayers);
                handler.start();
            }
        } catch (IOException e) {
            System.out.println("[ERREUR] Impossible de démarrer le serveur : " + e.getMessage());
        }
    }

    private static void printPreferredServerAddress(int port) {
        String bestIp = findPreferredIPv4();

        if (bestIp != null) {
            System.out.println("Adresse à utiliser pour les clients distants :");
            System.out.println("java ClientMain " + bestIp + " " + port);
        } else {
            System.out.println("[AVERTISSEMENT] Impossible de détecter une adresse IPv4 réseau utile.");
            System.out.println("Vérifie ton adresse avec ipconfig.");
        }

        System.out.println("====================================");
    }

    private static String findPreferredIPv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            String fallbackIp = null;

            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();

                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }

                String name = ni.getDisplayName().toLowerCase();

                if (name.contains("vmware")
                        || name.contains("virtualbox")
                        || name.contains("hyper-v")
                        || name.contains("vbox")
                        || name.contains("loopback")
                        || name.contains("pseudo")
                        || name.contains("bluetooth")
                        || name.contains("tunnel")) {
                    continue;
                }

                boolean preferred =
                        name.contains("wi-fi")
                                || name.contains("wireless")
                                || name.contains("wlan")
                                || name.contains("ethernet")
                                || name.contains("intel")
                                || name.contains("realtek");

                Enumeration<InetAddress> addresses = ni.getInetAddresses();

                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();

                        if (ip.startsWith("169.254.")) {
                            continue;
                        }

                        if (fallbackIp == null) {
                            fallbackIp = ip;
                        }

                        if (preferred) {
                            return ip;
                        }
                    }
                }
            }

            return fallbackIp;

        } catch (Exception e) {
            return null;
        }
    }
}