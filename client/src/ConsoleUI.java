import java.util.List;
import java.util.Scanner;

public class ConsoleUI {
    private final Scanner scanner;

    public ConsoleUI() {
        this.scanner = new Scanner(System.in);
    }

    public String ask(String label) {
        System.out.print(label + " : ");
        return scanner.nextLine().trim();
    }

    public String askChoiceString(String label) {
        return ask(label);
    }

    public int askInt(String label) {
        while (true) {
            try {
                System.out.print(label + " : ");
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (Exception e) {
                System.out.println("Veuillez entrer un nombre valide.");
            }
        }
    }

    public String askPlayerName() {
        return ask("Entrez votre nom joueur");
    }

    public int askPeerPort() {
        return askInt("Entrez votre port P2P (ex: 6001)");
    }

    public String askRoomName() {
        return ask("Nom salle");
    }

    public int askMaxPlayers() {
        while (true) {
            int value = askInt("Max joueurs");
            if (value >= 2) {
                return value;
            }
            System.out.println("Entrée invalide. Le nombre de joueurs doit être au moins 2.");
        }
    }

    public int askMaxAttempts() {
        while (true) {
            int value = askInt("Max tentatives");
            if (value >= 1) {
                return value;
            }
            System.out.println("Entrée invalide. Le nombre de tentatives doit être au moins 1.");
        }
    }

    public String askTcpRawMessage() {
        return ask("Entrez le message TCP complet");
    }

    public String askPeerHost() {
        return ask("IP du peer");
    }

    public int askPeerTargetPort() {
        return askInt("Port du peer");
    }

    public String askP2PRawMessage() {
        return ask("Entrez le message P2P complet");
    }

    public String askPlayerToKick() {
        return ask("Nom du joueur à expulser");
    }

    public String askColor(String label) {
        return ask(label);
    }

    public String[] askCombination(String title) {
        System.out.println("\n" + title);
        String[] combo = new String[4];
        combo[0] = askColor("Couleur 1");
        combo[1] = askColor("Couleur 2");
        combo[2] = askColor("Couleur 3");
        combo[3] = askColor("Couleur 4");
        return combo;
    }

    public void showHeader(String playerName, String host, int serverPort, int peerPort) {
        System.out.println("\n================================");
        System.out.println("        GUESS GAME CLIENT");
        System.out.println("================================");
        System.out.println("Joueur        : " + playerName);
        System.out.println("Serveur TCP   : " + host + ":" + serverPort);
        System.out.println("Port P2P local: " + peerPort);
        System.out.println("================================");
    }

    public void showTcpRawHelp() {
        System.out.println("TCP brut : envoie une commande au serveur.");
        System.out.println("Format attendu : GG|TYPE|... par exemple : GG|LIST_ROOMS");
    }

    public void showP2PRawHelp() {
        System.out.println("P2P : envoie un message direct à un autre client.");
        System.out.println("Utilisez soit du texte libre, soit un format simple comme : P2P|MSG|Bonjour");
    }

    public int showMainMenu() {
        System.out.println("\nMENU PRINCIPAL");
        System.out.println("1. Créer une salle");
        System.out.println("2. Lister les salles");
        System.out.println("3. Jouer contre le serveur");
        System.out.println("4. Quitter");
        System.out.println("5. Envoyer un message TCP brut");
        System.out.println("6. Envoyer un message P2P brut");
        return askInt("Choix");
    }

    public int showRoomMenu(String roomName, List<String> players) {
        System.out.println("\n================================");
        System.out.println("         SALLE : " + roomName);
        System.out.println("================================");
        System.out.println("Joueurs présents :");
        printPlayers(players);
        System.out.println("================================");
        System.out.println("1. Démarrer la partie");
        System.out.println("2. Quitter la salle et voir la liste des salles");
        System.out.println("3. Retour menu principal");
        System.out.println("4. Envoyer un message TCP brut");
        System.out.println("5. Envoyer un message P2P brut");
        return askInt("Choix");
    }

    public int showPreparationMenu(String roomName, List<String> players, String secretOwner) {
        System.out.println("\n================================");
        System.out.println(" PRÉPARATION DE PARTIE : " + roomName);
        System.out.println("================================");
        System.out.println("Joueurs dans la partie :");
        printPlayers(players);
        System.out.println("================================");

        if (secretOwner == null || secretOwner.isBlank()) {
            System.out.println("Aucun secret n'a encore été défini.");
        } else {
            System.out.println("Information : secret associé au joueur " + secretOwner + ".");
        }

        System.out.println("================================");
        System.out.println("1. Je définis le secret");
        System.out.println("2. Attendre qu'un joueur définisse le secret");
        System.out.println("3. Quitter la salle et voir la liste des salles");
        System.out.println("4. Retour menu principal");
        System.out.println("5. Envoyer un message TCP brut");
        System.out.println("6. Envoyer un message P2P brut");
        return askInt("Choix");
    }

    public int showSecretMenuAfter(String roomName) {
        System.out.println("\n================================");
        System.out.println(" MODE SECRET : " + roomName);
        System.out.println("================================");
        System.out.println("Vous êtes le joueur qui a défini la combinaison secrète.");
        System.out.println("1. Voir l'historique des propositions");
        System.out.println("2. Expulser un joueur");
        System.out.println("3. Quitter la salle et voir la liste des salles");
        System.out.println("4. Retour menu principal");
        System.out.println("5. Envoyer un message TCP brut");
        System.out.println("6. Envoyer un message P2P brut");
        return askInt("Choix");
    }

    public int showGuessMenu(String roomName, String secretOwner) {
        System.out.println("\n================================");
        System.out.println(" MODE GUESS : " + roomName);
        System.out.println("================================");
        System.out.println("Le joueur " + secretOwner + " a défini la combinaison secrète.");
        System.out.println("1. Envoyer une proposition");
        System.out.println("2. Voir l'historique des propositions");
        System.out.println("3. Quitter la salle et voir la liste des salles");
        System.out.println("4. Retour menu principal");
        System.out.println("5. Envoyer un message TCP brut");
        System.out.println("6. Envoyer un message P2P brut");
        return askInt("Choix");
    }

    public void showGuessesHistory(String history) {
        System.out.println("\n=========== HISTORIQUE ===========");
        printHistory(history);
        System.out.println("==================================");
    }

    public int showServerPlayMenu() {
        System.out.println("\n================================");
        System.out.println(" MODE JOUER CONTRE LE SERVEUR");
        System.out.println("================================");
        System.out.println("1. Envoyer une proposition");
        System.out.println("2. Voir l'historique");
        System.out.println("3. Quitter la partie serveur");
        System.out.println("4. Envoyer un message TCP brut");
        System.out.println("5. Envoyer un message P2P brut");
        return askInt("Choix");
    }

    public void showServerHistory(String history) {
        System.out.println("\n======= HISTORIQUE SERVEUR =======");
        printHistory(history);
        System.out.println("==================================");
    }

    public int showAfterCreateRoomMenu(String roomName) {
        System.out.println("\nSalle \"" + roomName + "\" créée avec succès.");
        System.out.println("1. Rejoindre cette salle");
        System.out.println("2. Retour au menu principal");
        System.out.println("3. Envoyer un message TCP brut");
        System.out.println("4. Envoyer un message P2P brut");
        return askInt("Choix");
    }

    public int showEndGameMenu(String winnerName) {
        System.out.println("\n================================");
        System.out.println("         FIN DE PARTIE");
        System.out.println("================================");
        System.out.println("Le gagnant est : " + winnerName);
        System.out.println("1. Rejouer dans la même salle");
        System.out.println("2. Quitter la salle et voir la liste des salles");
        System.out.println("3. Retour menu principal");
        System.out.println("4. Envoyer un message TCP brut");
        System.out.println("5. Envoyer un message P2P brut");
        return askInt("Choix");
    }

    public int showServerEndGameMenu(boolean won) {
        System.out.println("\n================================");
        System.out.println(" FIN DE PARTIE CONTRE LE SERVEUR");
        System.out.println("================================");

        if (won) {
            System.out.println("Résultat : Victoire");
        } else {
            System.out.println("Résultat : Défaite");
        }

        System.out.println("1. Rejouer contre le serveur");
        System.out.println("2. Retour menu principal");
        System.out.println("3. Envoyer un message TCP brut");
        System.out.println("4. Envoyer un message P2P brut");
        return askInt("Choix");
    }

    private void printPlayers(List<String> players) {
        if (players == null || players.isEmpty()) {
            System.out.println("- Aucun joueur");
            return;
        }

        for (String player : players) {
            System.out.println("- " + player);
        }
    }

    private void printHistory(String history) {
        if (history == null || history.isBlank()) {
            System.out.println("Aucune proposition pour le moment.");
            return;
        }

        String[] items = history.split(";");
        boolean found = false;

        for (String item : items) {
            if (!item.trim().isEmpty()) {
                System.out.println("- " + item.trim());
                found = true;
            }
        }

        if (!found) {
            System.out.println("Aucune proposition pour le moment.");
        }
    }
}