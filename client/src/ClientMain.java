public class ClientMain {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 5050;

        if (args.length >= 1 && args[0] != null && !args[0].isBlank()) {
            host = args[0].trim();
        }

        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException e) {
                System.out.println("[AVERTISSEMENT] Port invalide. Utilisation du port par défaut : 5050");
                port = 5050;
            }
        }

        if (port <= 0 || port > 65535) {
            System.out.println("[AVERTISSEMENT] Port hors intervalle. Utilisation du port par défaut : 5050");
            port = 5050;
        }

        ClientApp app = new ClientApp(host, port);
        app.start();
    }
}