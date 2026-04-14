import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClientApp {
    private static final List<String> ALLOWED_COLORS = List.of(
            "ROUGE", "BLEU", "VERT", "JAUNE", "ORANGE"
    );

    private final String host;
    private final int port;
    private final ConsoleUI ui;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private String playerName;
    private int peerPort;
    private PeerServer peerServer;

    private String currentRoom;
    private String secretOwner;
    private boolean secretDefined;
    private boolean roomGameStarted;
    private final List<String> currentPlayers;

    private boolean serverGameActive;
    private int serverGameMaxAttempts;

    private boolean roomGameFinished;
    private String roomWinnerName;
    private int serverGameUsedAttempts;
    private boolean serverGameWon;

    public ClientApp(String host, int port) {
        this.host = host;
        this.port = port;
        this.ui = new ConsoleUI();
        this.currentPlayers = new ArrayList<>();
        this.serverGameUsedAttempts = 0;
        this.serverGameWon = false;
    }

    public void start() {
        try {
            playerName = ui.askPlayerName();
            if (playerName == null || playerName.isBlank()) {
                System.out.println("Nom joueur invalide.");
                return;
            }

            peerPort = ui.askPeerPort();
            if (peerPort <= 0 || peerPort > 65535) {
                System.out.println("Port P2P invalide.");
                return;
            }

            peerServer = new PeerServer(peerPort);
            peerServer.start();

            connectToServer();

            sendMessage("GG|CONNECT|" + playerName);
            GGMessage connectResponse = readParsedResponse();

            if (connectResponse == null) {
                System.out.println("Connexion refusée : aucune réponse du serveur.");
                return;
            }

            if (!"CONNECTED".equals(connectResponse.getType())) {
                System.out.println("Connexion joueur refusée.");
                if ("ERROR".equals(connectResponse.getType())) {
                    System.out.println("Erreur : " + connectResponse.getField(0));
                }
                return;
            }

            System.out.println("Connexion réussie pour le joueur : " + playerName);
            runMainLoop();

        } catch (IOException e) {
            System.out.println("[ERREUR] Impossible de se connecter au serveur : " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void connectToServer() throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
        System.out.println("Connecté au serveur " + host + ":" + port);
    }

    private void runMainLoop() throws IOException {
        boolean running = true;

        while (running) {
            ui.showHeader(playerName, host, port, peerPort);

            if (serverGameActive) {
                handleServerGameMode();
                continue;
            }

            if (currentRoom == null) {
                int choice = ui.showMainMenu();

                switch (choice) {
                    case 1 -> handleCreateRoom();
                    case 2 -> handleListRooms();
                    case 3 -> handlePlayServer();
                    case 4 -> {
                        running = false;
                        System.out.println("Déconnexion du client.");
                    }
                    case 5 -> handleTcpRaw();
                    case 6 -> handleP2PRaw();
                    default -> System.out.println("Choix invalide.");
                }
            } else {
                if (roomGameFinished) {
                    running = handleEndGameMenu();
                } else if (!roomGameStarted) {
                    running = handleRoomAndPreparationFlow();
                } else if (!secretDefined) {
                    running = handlePreparationMode();
                } else if (playerName.equals(secretOwner)) {
                    running = handleSecretMode();
                } else {
                    running = handleGuessMode();
                }
            }
        }
    }

    private boolean handleRoomAndPreparationFlow() throws IOException {
        refreshCurrentPlayers();

        int roomChoice = ui.showRoomMenu(currentRoom, currentPlayers);

        switch (roomChoice) {
            case 1 -> {
                sendMessage("GG|START_GAME|" + currentRoom);
                GGMessage response = readParsedResponse();

                if (response != null && "GAME_STARTED".equals(response.getType())) {
                    updatePlayersFromCsv(response.getField(1));
                    roomGameStarted = true;
                    return handlePreparationMode();

                } else if (response != null && "ERROR".equals(response.getType())) {
                    String error = response.getField(0);

                    if ("NOT_ENOUGH_PLAYERS".equals(error)) {
                        System.out.println("Tu es seul dans la salle. Attends qu'un autre joueur rejoigne, ou quitte la salle pour jouer contre le serveur.");
                        return true;
                    }

                    if ("GAME_ALREADY_STARTED".equals(error)) {
                        refreshRoomState();
                        refreshCurrentPlayers();

                        if (roomGameFinished) {
                            return handleEndGameMenu();
                        } else if (!roomGameStarted) {
                            return true;
                        } else if (!secretDefined) {
                            return handlePreparationMode();
                        } else if (playerName.equals(secretOwner)) {
                            return handleSecretMode();
                        } else {
                            return handleGuessMode();
                        }
                    }

                    System.out.println("Erreur : " + error);
                    return true;
                }

                return true;
            }

            case 2 -> {
                leaveCurrentRoom();
                handleListRooms();
                return true;
            }

            case 3 -> {
                leaveCurrentRoom();
                return true;
            }

            case 4 -> {
                handleTcpRaw();
                return true;
            }

            case 5 -> {
                handleP2PRaw();
                return true;
            }

            default -> {
                System.out.println("Choix invalide.");
                return true;
            }
        }
    }

    private boolean handlePreparationMode() throws IOException {
        while (currentRoom != null && roomGameStarted && !secretDefined) {
            GGMessage immediate = tryReadImmediateMessage();
            if (handleRoomTerminalMessage(immediate)) {
                return handleEndGameMenu();
            }

            if (currentRoom == null) {
                return true;
            }

            if (roomGameFinished) {
                return handleEndGameMenu();
            }

            if (!roomGameStarted) {
                return true;
            }

            if (secretDefined) {
                return true;
            }

            int choice = ui.showPreparationMenu(currentRoom, currentPlayers, secretOwner);

            switch (choice) {
                case 1 -> {
                    refreshCurrentPlayers();

                    if (currentPlayers.size() <= 1) {
                        System.out.println("Tu es maintenant seul dans la salle.");
                        System.out.println("La partie a été arrêtée.");
                        roomGameStarted = false;
                        secretDefined = false;
                        secretOwner = null;
                        return true;
                    }

                    if (roomGameFinished) {
                        return handleEndGameMenu();
                    }

                    if (!roomGameStarted) {
                        return true;
                    }

                    if (secretDefined) {
                        return true;
                    }

                    String[] secret = askValidatedCombination("Entrez votre combinaison secrète");
                    String msg = "GG|SECRET_SET|" + currentRoom + "|" + playerName + "|" +
                            secret[0] + "|" + secret[1] + "|" + secret[2] + "|" + secret[3];

                    sendMessage(msg);
                    GGMessage response = readParsedResponse();

                    if (response == null) {
                        System.out.println("Aucune réponse du serveur.");
                    } else if ("SECRET_ACCEPTED".equals(response.getType())) {
                        secretOwner = playerName;
                        secretDefined = true;
                        System.out.println("Secret enregistré avec succès : " + Arrays.toString(secret));
                        return true;
                    } else if ("SECRET_ALREADY_SET".equals(response.getType())) {
                        secretOwner = response.getField(1);
                        secretDefined = true;
                        return true;
                    } else if ("ERROR".equals(response.getType())) {
                        System.out.println("Erreur : " + response.getField(0));
                    }
                }

                case 2 -> {
                    System.out.println("En attente qu'un joueur définisse le secret...");

                    GGMessage waitImmediate = tryReadImmediateMessage();
                    if (handleRoomTerminalMessage(waitImmediate)) {
                        return handleEndGameMenu();
                    }

                    if (currentRoom == null) {
                        return true;
                    }

                    refreshCurrentPlayers();

                    if (currentPlayers.size() <= 1) {
                        System.out.println("Tu es maintenant seul dans la salle.");
                        System.out.println("La partie a été arrêtée.");
                        roomGameStarted = false;
                        secretDefined = false;
                        secretOwner = null;
                        return true;
                    }

                    if (roomGameFinished) {
                        return handleEndGameMenu();
                    }

                    if (!roomGameStarted) {
                        return true;
                    }

                    if (secretDefined) {
                        return true;
                    }
                }

                case 3 -> {
                    leaveCurrentRoom();
                    handleListRooms();
                    return true;
                }

                case 4 -> {
                    leaveCurrentRoom();
                    return true;
                }

                case 5 -> handleTcpRaw();
                case 6 -> handleP2PRaw();
                default -> System.out.println("Choix invalide.");
            }
        }

        return true;
    }

    private boolean handleSecretMode() throws IOException {
        while (currentRoom != null && secretDefined && playerName.equals(secretOwner)) {
            GGMessage immediate = tryReadImmediateMessage();
            if (handleRoomTerminalMessage(immediate)) {
                return handleEndGameMenu();
            }

            if (currentRoom == null) {
                return true;
            }

            if (!roomGameStarted || !secretDefined) {
                return true;
            }

            if (roomGameFinished) {
                return handleEndGameMenu();
            }

            int choice = ui.showSecretMenuAfter(currentRoom);

            switch (choice) {
                case 1 -> {
                    showRoomGuessHistory();

                    GGMessage afterHistory = tryReadImmediateMessage();
                    if (handleRoomTerminalMessage(afterHistory)) {
                        return handleEndGameMenu();
                    }
                }

                case 2 -> {
                    refreshCurrentPlayers();

                    if (currentPlayers.size() <= 1) {
                        System.out.println("Tu es maintenant seul dans la salle.");
                        System.out.println("La partie ne peut pas continuer avec un seul joueur.");
                        return true;
                    }

                    handleKickPlayer();

                    GGMessage afterKick = tryReadImmediateMessage();
                    if (handleRoomTerminalMessage(afterKick)) {
                        return handleEndGameMenu();
                    }
                }

                case 3 -> {
                    leaveCurrentRoom();
                    handleListRooms();
                    return true;
                }

                case 4 -> {
                    leaveCurrentRoom();
                    return true;
                }

                case 5 -> handleTcpRaw();
                case 6 -> handleP2PRaw();
                default -> System.out.println("Choix invalide.");
            }
        }

        return true;
    }

    private boolean handleGuessMode() throws IOException {
        while (currentRoom != null && secretDefined && !playerName.equals(secretOwner)) {
            if (!roomGameStarted || !secretDefined) {
                return true;
            }

            if (roomGameFinished) {
                return handleEndGameMenu();
            }

            int choice = ui.showGuessMenu(currentRoom, secretOwner);

            switch (choice) {
                case 1 -> {
                    refreshCurrentPlayers();

                    if (currentPlayers.size() <= 1) {
                        System.out.println("Tu es maintenant seul dans la salle.");
                        System.out.println("La partie a été arrêtée.");
                        roomGameStarted = false;
                        secretDefined = false;
                        secretOwner = null;
                        return true;
                    }

                    if (!roomGameStarted || !secretDefined) {
                        return true;
                    }

                    int[] attemptsInfo = getAttemptsInfoForCurrentPlayer();
                    int attemptsLeft = attemptsInfo[0];
                    int maxAttempts = attemptsInfo[1];

                    if (attemptsLeft == 0) {
                        System.out.println("Tu as déjà utilisé toutes tes tentatives.");
                        break;
                    } else if (attemptsLeft == maxAttempts && maxAttempts > 0) {
                        System.out.println("Tu as " + maxAttempts + " tentatives.");
                    } else if (attemptsLeft == 1) {
                        System.out.println("Attention : c'est ta dernière chance !");
                    } else if (attemptsLeft > 1) {
                        System.out.println("Il te reste " + attemptsLeft + " tentatives.");
                    }

                    String[] guess = askValidatedCombination("Entrez votre proposition");
                    String msg = "GG|GUESS|" + currentRoom + "|" + playerName + "|" +
                            guess[0] + "|" + guess[1] + "|" + guess[2] + "|" + guess[3];

                    sendMessage(msg);
                    GGMessage response = readParsedResponse();

                    if (response == null) {
                        System.out.println("Aucune réponse du serveur.");
                        break;
                    }

                    if ("FEEDBACK".equals(response.getType())) {
                        displayFeedback(response);

                        GGMessage followUp = tryReadImmediateMessage();
                        if (handleRoomTerminalMessage(followUp)) {
                            return handleEndGameMenu();
                        }

                    } else if (handleRoomTerminalMessage(response)) {
                        return handleEndGameMenu();

                    } else if ("ERROR".equals(response.getType())) {
                        String error = response.getField(0);

                        if ("GAME_NOT_STARTED".equals(error) || "NO_SECRET_DEFINED".equals(error)) {
                            refreshRoomState();
                            refreshCurrentPlayers();
                            return true;
                        }

                        System.out.println("Erreur : " + error);
                    }
                }

                case 2 -> {
                    showRoomGuessHistory();

                    GGMessage afterHistory = tryReadImmediateMessage();
                    if (handleRoomTerminalMessage(afterHistory)) {
                        return handleEndGameMenu();
                    }
                }

                case 3 -> {
                    leaveCurrentRoom();
                    handleListRooms();
                    return true;
                }

                case 4 -> {
                    leaveCurrentRoom();
                    return true;
                }

                case 5 -> handleTcpRaw();
                case 6 -> handleP2PRaw();
                default -> System.out.println("Choix invalide.");
            }
        }

        return true;
    }

    private boolean handleEndGameMenu() throws IOException {
        while (true) {
            GGMessage immediate = tryReadImmediateMessage();
            handleRoomTerminalMessage(immediate);

            if (currentRoom == null) {
                return true;
            }

            refreshCurrentPlayers();

            int choice = ui.showEndGameMenu(roomWinnerName == null ? "Inconnu" : roomWinnerName);

            switch (choice) {
                case 1 -> {
                    sendMessage("GG|REPLAY_ROOM|" + currentRoom);
                    GGMessage response = readParsedResponse();

                    if (response != null && "REPLAY_READY".equals(response.getType())) {
                        updatePlayersFromCsv(response.getField(1));
                        roomGameStarted = false;
                        resetRoomGameStateOnly();
                        return true;
                    } else if (response != null && "ERROR".equals(response.getType())) {
                        System.out.println("Erreur : " + response.getField(0));
                    }
                }

                case 2 -> {
                    leaveCurrentRoom();
                    handleListRooms();
                    return true;
                }

                case 3 -> {
                    leaveCurrentRoom();
                    return true;
                }

                case 4 -> handleTcpRaw();
                case 5 -> handleP2PRaw();
                default -> System.out.println("Choix invalide.");
            }
        }
    }

    private void handleCreateRoom() throws IOException {
        String roomName = ui.askRoomName();
        int maxPlayers = ui.askMaxPlayers();
        int maxAttempts = ui.askMaxAttempts();

        if (roomName == null || roomName.isBlank()) {
            System.out.println("Le nom de la salle ne peut pas être vide.");
            return;
        }

        sendMessage("GG|CREATE_ROOM|" + roomName + "|" + maxPlayers + "|" + maxAttempts);
        GGMessage response = readParsedResponse();

        if (response == null) {
            return;
        }

        if ("ROOM_CREATED".equals(response.getType())) {
            boolean afterCreateMenu = true;

            while (afterCreateMenu) {
                int choice = ui.showAfterCreateRoomMenu(roomName);

                switch (choice) {
                    case 1 -> {
                        joinRoomByName(roomName);
                        afterCreateMenu = false;
                    }
                    case 2 -> afterCreateMenu = false;
                    case 3 -> handleTcpRaw();
                    case 4 -> handleP2PRaw();
                    default -> System.out.println("Choix invalide.");
                }
            }

        } else if ("ERROR".equals(response.getType())) {
            System.out.println("Erreur : " + response.getField(0));
        }
    }

    private void handleListRooms() throws IOException {
        sendMessage("GG|LIST_ROOMS");
        GGMessage response = readParsedResponse();

        if (response == null) {
            return;
        }

        if (!"ROOM_LIST".equals(response.getType())) {
            if ("ERROR".equals(response.getType())) {
                System.out.println("Erreur : " + response.getField(0));
            }
            return;
        }

        List<String> rooms = parseCsvToList(response.getField(0));
        boolean inRoomListMenu = true;

        while (inRoomListMenu) {
            System.out.println("\n===== LISTE DES SALLES =====");
            if (rooms.isEmpty()) {
                System.out.println("Aucune salle disponible.");
            } else {
                for (int i = 0; i < rooms.size(); i++) {
                    System.out.println((i + 1) + ". " + rooms.get(i));
                }
            }

            System.out.println("M. Retour menu principal");
            System.out.println("T. Envoyer un message TCP brut");
            System.out.println("P. Envoyer un message P2P brut");

            String choice = ui.askChoiceString("Choix").toUpperCase();

            if (choice.equals("M")) {
                inRoomListMenu = false;
            } else if (choice.equals("T")) {
                handleTcpRaw();
            } else if (choice.equals("P")) {
                handleP2PRaw();
            } else {
                try {
                    int roomChoice = Integer.parseInt(choice);
                    if (roomChoice >= 1 && roomChoice <= rooms.size()) {
                        joinRoomByName(rooms.get(roomChoice - 1));
                        inRoomListMenu = false;
                    } else {
                        System.out.println("Choix invalide.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Choix invalide.");
                }
            }
        }
    }

    private void joinRoomByName(String roomName) throws IOException {
        sendMessage("GG|JOIN_ROOM|" + roomName);
        GGMessage response = readParsedResponse();

        if (response == null) {
            return;
        }

        if ("JOINED_ROOM".equals(response.getType())) {
            currentRoom = response.getField(0);
            updatePlayersFromCsv(response.getField(1));
            resetRoomGameStateOnly();
            roomGameStarted = false;

            System.out.println("Vous avez rejoint la salle : " + currentRoom);
            System.out.println("Joueurs présents :");
            for (String player : currentPlayers) {
                System.out.println("- " + player);
            }

        } else if ("ERROR".equals(response.getType())) {
            System.out.println("Erreur : " + response.getField(0));
        }
    }

    private void leaveCurrentRoom() throws IOException {
        if (currentRoom == null) {
            return;
        }

        String roomToLeave = currentRoom;
        sendMessage("GG|LEAVE_ROOM|" + roomToLeave);
        GGMessage response = readParsedResponse();

        if (response != null && "LEFT_ROOM".equals(response.getType())) {
            System.out.println("Vous avez quitté la salle : " + roomToLeave);
            resetRoomStateCompletely();
        } else if (response != null && "ERROR".equals(response.getType())) {
            System.out.println("Erreur : " + response.getField(0));
        }
    }

    private void handlePlayServer() throws IOException {
        int maxAttempts = ui.askMaxAttempts();
        sendMessage("GG|PLAY_SERVER|" + maxAttempts);
        GGMessage response = readParsedResponse();

        if (response == null) {
            return;
        }

        if ("SERVER_GAME_STARTED".equals(response.getType())) {
            serverGameActive = true;
            serverGameUsedAttempts = 0;
            serverGameWon = false;

            try {
                serverGameMaxAttempts = Integer.parseInt(response.getField(0));
            } catch (Exception e) {
                serverGameMaxAttempts = 0;
            }

            System.out.println("Le serveur a choisi une combinaison secrète.");
            System.out.println("Tu as " + serverGameMaxAttempts + " tentatives pour la deviner.");

        } else if ("ERROR".equals(response.getType())) {
            System.out.println("Erreur : " + response.getField(0));
        }
    }

    private void handleServerGameMode() throws IOException {
        while (serverGameActive) {
            int choice = ui.showServerPlayMenu();

            switch (choice) {
                case 1 -> {
                    int attemptsLeft = serverGameMaxAttempts - serverGameUsedAttempts;

                    if (serverGameUsedAttempts == 0) {
                        System.out.println("Tu as " + serverGameMaxAttempts + " tentatives.");
                    } else if (attemptsLeft > 1) {
                        System.out.println("Il te reste " + attemptsLeft + " tentatives.");
                    } else if (attemptsLeft == 1) {
                        System.out.println("Attention : il te reste 1 tentative !");
                    }

                    String[] guess = askValidatedCombination("Entrez votre proposition");
                    String msg = "GG|PLAY_SERVER_GUESS|" +
                            guess[0] + "|" + guess[1] + "|" + guess[2] + "|" + guess[3];

                    sendMessage(msg);
                    GGMessage response = readParsedResponse();

                    if (response == null) {
                        System.out.println("Aucune réponse du serveur.");
                        break;
                    }

                    if ("FEEDBACK".equals(response.getType())) {
                        serverGameUsedAttempts++;
                        displayFeedback(response);

                        GGMessage followUp = tryReadImmediateMessage();
                        if (handleServerTerminalMessage(followUp)) {
                            serverGameActive = false;
                            handleServerEndGameMenu();
                            return;
                        }

                    } else if (handleServerTerminalMessage(response)) {
                        serverGameActive = false;
                        handleServerEndGameMenu();
                        return;

                    } else if ("ERROR".equals(response.getType())) {
                        System.out.println("Erreur : " + response.getField(0));
                    }
                }

                case 2 -> {
                    sendMessage("GG|PLAY_SERVER_HISTORY");
                    GGMessage response = readParsedResponse();

                    if (response == null) {
                        System.out.println("Aucune réponse du serveur.");
                        break;
                    }

                    if ("GUESSES".equals(response.getType())) {
                        ui.showServerHistory(response.getField(1));
                    } else if (handleServerTerminalMessage(response)) {
                        serverGameActive = false;
                        handleServerEndGameMenu();
                        return;
                    } else if ("ERROR".equals(response.getType())) {
                        System.out.println("Erreur : " + response.getField(0));
                    }
                }

                case 3 -> {
                    sendMessage("GG|PLAY_SERVER_QUIT");
                    GGMessage response = readParsedResponse();

                    if (response != null && "LEFT_ROOM".equals(response.getType())) {
                        System.out.println("Partie contre le serveur abandonnée.");
                    } else if (response != null && "ERROR".equals(response.getType())) {
                        System.out.println("Erreur : " + response.getField(0));
                    }
                    serverGameActive = false;
                    return;
                }

                case 4 -> handleTcpRaw();
                case 5 -> handleP2PRaw();
                default -> System.out.println("Choix invalide.");
            }
        }
    }

    private void drainImmediateMessages() {
        try {
            while (in != null && in.ready()) {
                String raw = in.readLine();
                if (raw == null) {
                    break;
                }
                displayReceivedMessage(raw);
            }
        } catch (IOException e) {
            System.out.println("Erreur vidage messages : " + e.getMessage());
        }
    }

    private void handleServerEndGameMenu() throws IOException {
        drainImmediateMessages();

        while (true) {
            int choice = ui.showServerEndGameMenu(serverGameWon);

            switch (choice) {
                case 1 -> {
                    serverGameActive = false;
                    serverGameUsedAttempts = 0;
                    serverGameWon = false;
                    handlePlayServer();
                    return;
                }
                case 2 -> {
                    serverGameActive = false;
                    return;
                }
                case 3 -> handleTcpRaw();
                case 4 -> handleP2PRaw();
                default -> System.out.println("Choix invalide.");
            }
        }
    }

    private void handleKickPlayer() throws IOException {
        if (currentRoom == null) {
            return;
        }

        String target = ui.askPlayerToKick();

        if (target == null || target.isBlank()) {
            System.out.println("Nom joueur invalide.");
            return;
        }

        if (target.equalsIgnoreCase(playerName)) {
            System.out.println("Tu ne peux pas t'expulser toi-même.");
            return;
        }

        sendMessage("GG|KICK_PLAYER|" + currentRoom + "|" + target);
        GGMessage response = readParsedResponse();

        if (response == null) {
            System.out.println("Aucune réponse du serveur.");
            return;
        }

        if ("KICK_SUCCESS".equals(response.getType())) {
            System.out.println("Joueur expulsé : " + target);
            refreshCurrentPlayers();
        } else if ("ERROR".equals(response.getType())) {
            System.out.println("Erreur : " + response.getField(0));
        }
    }

    private void handleTcpRaw() {
        try {
            ui.showTcpRawHelp();

            while (true) {
                String raw = ui.askTcpRawMessage();

                if (raw == null || raw.isBlank()) {
                    System.out.println("Message TCP vide.");
                    continue;
                }

                if (!raw.startsWith("GG|")) {
                    System.out.println("Message TCP invalide. Utilisez le format du protocole, par exemple : GG|LIST_ROOMS");
                    continue;
                }

                sendMessage(raw);

                GGMessage response = readParsedResponse();
                if (response == null) {
                    System.out.println("Aucune réponse du serveur.");
                }

                return;
            }

        } catch (IOException e) {
            System.out.println("Erreur TCP brut : " + e.getMessage());
        }
    }

    private void handleP2PRaw() {
        try {
            ui.showP2PRawHelp();

            while (true) {
                String peerHost = ui.askPeerHost();
                int peerTargetPort = ui.askPeerTargetPort();
                String message = ui.askP2PRawMessage();

                if (peerHost == null || peerHost.isBlank()) {
                    System.out.println("IP du peer invalide.");
                    continue;
                }

                if (peerTargetPort <= 0 || peerTargetPort > 65535) {
                    System.out.println("Port peer invalide.");
                    continue;
                }

                if (message == null || message.isBlank()) {
                    System.out.println("Message P2P vide.");
                    continue;
                }

                PeerConnection.send(peerHost, peerTargetPort, message);
                return;
            }

        } catch (Exception e) {
            System.out.println("Erreur P2P : " + e.getMessage());
        }
    }

    private GGMessage readParsedResponse() throws IOException {
        String raw = in.readLine();

        if (raw == null) {
            System.out.println("Connexion au serveur perdue.");
            serverGameActive = false;
            resetRoomStateCompletely();
            closeConnection();
            return null;
        }

        displayReceivedMessage(raw);
        GGMessage msg = MessageParser.parse(raw);

        if (msg != null && "PLAYER_KICKED".equals(msg.getType())) {
            String kickedPlayer = msg.getField(0);

            if (kickedPlayer == null || kickedPlayer.isBlank() || kickedPlayer.equalsIgnoreCase(playerName)) {
                System.out.println("Vous avez été expulsé de la salle.");
                resetRoomStateCompletely();
                return null;
            }
        }

        return msg;
    }

    private GGMessage tryReadImmediateMessage() {
        try {
            if (in != null && in.ready()) {
                String raw = in.readLine();
                if (raw == null) {
                    return null;
                }

                displayReceivedMessage(raw);
                GGMessage msg = MessageParser.parse(raw);

                if (msg != null && "PLAYER_KICKED".equals(msg.getType())) {
                    String kickedPlayer = msg.getField(0);

                    if (kickedPlayer == null || kickedPlayer.isBlank() || kickedPlayer.equalsIgnoreCase(playerName)) {
                        System.out.println("Vous avez été expulsé de la salle.");
                        resetRoomStateCompletely();
                        return null;
                    }
                }

                return msg;
            }
        } catch (IOException e) {
            System.out.println("Erreur lecture message immédiat : " + e.getMessage());
        }

        return null;
    }

    private void refreshRoomState() throws IOException {
        if (currentRoom == null) {
            return;
        }

        refreshGameStatus();

        sendMessage("GG|GET_SECRET_OWNER|" + currentRoom);
        GGMessage response = readParsedResponse();

        if (response != null && "SECRET_OWNER".equals(response.getType())) {
            String owner = response.getField(1);
            secretOwner = (owner == null || owner.isBlank()) ? null : owner;
            secretDefined = secretOwner != null;
        } else {
            secretOwner = null;
            secretDefined = false;
        }
    }

    private void refreshGameStatus() throws IOException {
        if (currentRoom == null) {
            return;
        }

        sendMessage("GG|GET_GAME_STATUS|" + currentRoom);
        GGMessage response = readParsedResponse();

        if (response == null) {
            return;
        }

        if ("GAME_STATUS".equals(response.getType())) {
            String started = response.getField(1);
            String finished = response.getField(2);
            String winner = response.getField(3);

            roomGameStarted = "true".equalsIgnoreCase(started);
            roomGameFinished = "true".equalsIgnoreCase(finished);
            roomWinnerName = (winner == null || winner.isBlank()) ? null : winner;
            return;
        }

        if ("WINNER".equals(response.getType()) || "GAME_FINISHED".equals(response.getType())) {
            roomGameStarted = true;
            roomGameFinished = true;
            roomWinnerName = response.getField(1);
        }
    }

    private void refreshCurrentPlayers() throws IOException {
        if (currentRoom == null) {
            return;
        }

        sendMessage("GG|GET_ROOM_PLAYERS|" + currentRoom);
        GGMessage response = readParsedResponse();

        if (response != null && "ROOM_PLAYERS".equals(response.getType())) {
            updatePlayersFromCsv(response.getField(1));
        }
    }

    private void showRoomGuessHistory() throws IOException {
        sendMessage("GG|GET_GUESSES|" + currentRoom);
        GGMessage response = readParsedResponse();

        if (response != null && "GUESSES".equals(response.getType())) {
            ui.showGuessesHistory(response.getField(1));
        } else if (response != null && "ERROR".equals(response.getType())) {
            System.out.println("Erreur : " + response.getField(0));
        }
    }

    private void displayFeedback(GGMessage response) {
        System.out.println("Couleurs correctes : " + response.getField(0));
        System.out.println("Positions correctes : " + response.getField(1));
    }

    private boolean handleRoomTerminalMessage(GGMessage message) {
        if (message == null) {
            return false;
        }

        if ("WINNER".equals(message.getType()) || "GAME_FINISHED".equals(message.getType())) {
            roomGameStarted = true;
            roomGameFinished = true;
            roomWinnerName = message.getField(1);
            return true;
        }

        if ("REPLAY_READY".equals(message.getType())) {
            updatePlayersFromCsv(message.getField(1));
            roomGameStarted = false;
            resetRoomGameStateOnly();
            return true;
        }

        return false;
    }

    private boolean handleServerTerminalMessage(GGMessage message) {
        if (message == null) {
            return false;
        }

        if ("SERVER_WINNER".equals(message.getType())) {
            serverGameWon = true;
            System.out.println("Bravo ! Tu as trouvé la combinaison.");
            System.out.println("Tentatives utilisées : " + message.getField(0) + "/" + message.getField(1));
            return true;
        }

        if ("SERVER_GAME_OVER".equals(message.getType())) {
            serverGameWon = false;
            System.out.println("Partie terminée. Tu as perdu.");
            System.out.println("Tentatives utilisées : " + message.getField(0) + "/" + message.getField(1));
            System.out.println("Secret du serveur : " + message.getField(2));
            return true;
        }

        return false;
    }

    private String[] askValidatedCombination(String title) {
        while (true) {
            System.out.println("\nCouleurs autorisées : " + String.join(", ", ALLOWED_COLORS));
            String[] combo = ui.askCombination(title);

            if (combo == null || combo.length != 4) {
                System.out.println("La combinaison doit contenir exactement 4 couleurs.");
                continue;
            }

            boolean valid = true;
            for (int i = 0; i < combo.length; i++) {
                combo[i] = normalizeColor(combo[i]);

                if (!ALLOWED_COLORS.contains(combo[i])) {
                    System.out.println("Couleur invalide à la position " + (i + 1) + " : " + combo[i]);
                    valid = false;
                }
            }

            if (valid) {
                return combo;
            }

            System.out.println("Veuillez ressaisir une combinaison valide.");
        }
    }

    private String normalizeColor(String color) {
        return color == null ? "" : color.trim().toUpperCase();
    }

    private List<String> parseCsvToList(String csv) {
        List<String> result = new ArrayList<>();

        if (csv == null || csv.isBlank()) {
            return result;
        }

        String[] parts = csv.split(",");
        for (String part : parts) {
            String value = part.trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }

        return result;
    }

    private void updatePlayersFromCsv(String csv) {
        currentPlayers.clear();
        currentPlayers.addAll(parseCsvToList(csv));
    }

    private void sendMessage(String message) {
        if (out == null) {
            System.out.println("Impossible d'envoyer : sortie réseau indisponible.");
            return;
        }

        System.out.println("\nEnvoyé -> " + message);
        out.println(message);
        out.flush();
    }

    private void displayReceivedMessage(String raw) {
        GGMessage msg = MessageParser.parse(raw);

        System.out.println("\n===== MESSAGE REÇU =====");

        if (msg == null) {
            System.out.println("Message invalide.");
            System.out.println("Brut : " + raw);
            System.out.println("========================");
            return;
        }

        System.out.println("Brut : " + raw);
        System.out.println("Préfixe : " + msg.getPrefix());
        System.out.println("Type : " + msg.getType());

        if (msg.getFieldCount() == 0) {
            System.out.println("Champs : aucun");
        } else {
            for (int i = 0; i < msg.getFieldCount(); i++) {
                String fieldValue = msg.getField(i);
                System.out.println("Champ[" + i + "] = " + fieldValue);

                if (looksLikeCsvList(msg.getType(), i, fieldValue)) {
                    List<String> items = parseCsvToList(fieldValue);
                    if (items.isEmpty()) {
                        System.out.println("  -> liste vide");
                    } else {
                        for (String item : items) {
                            System.out.println("  - " + item);
                        }
                    }
                }
            }
        }

        switch (msg.getType()) {
            case "CONNECTED" -> System.out.println("Connexion acceptée pour : " + msg.getField(0));

            case "ROOM_CREATED" -> System.out.println("Salle créée : " + msg.getField(0));

            case "ROOM_LIST" -> {
                List<String> rooms = parseCsvToList(msg.getField(0));
                if (rooms.isEmpty()) {
                    System.out.println("Aucune salle disponible.");
                } else {
                    System.out.println("Salles disponibles :");
                    for (String room : rooms) {
                        System.out.println("- " + room);
                    }
                }
            }

            case "JOINED_ROOM" -> {
                System.out.println("Salle rejointe : " + msg.getField(0));
                List<String> players = parseCsvToList(msg.getField(1));
                System.out.println("Joueurs présents :");
                if (players.isEmpty()) {
                    System.out.println("- Aucun");
                } else {
                    for (String player : players) {
                        System.out.println("- " + player);
                    }
                }
            }

            case "LEFT_ROOM" -> System.out.println("Confirmation de sortie de salle.");

            case "ROOM_PLAYERS" -> {
                List<String> players = parseCsvToList(msg.getField(1));
                System.out.println("Liste actuelle des joueurs :");
                if (players.isEmpty()) {
                    System.out.println("- Aucun");
                } else {
                    for (String player : players) {
                        System.out.println("- " + player);
                    }
                }
            }

            case "GAME_STARTED" -> {
                System.out.println("La partie a commencé dans la salle : " + msg.getField(0));
                List<String> players = parseCsvToList(msg.getField(1));
                System.out.println("Joueurs de la partie :");
                if (players.isEmpty()) {
                    System.out.println("- Aucun");
                } else {
                    for (String player : players) {
                        System.out.println("- " + player);
                    }
                }
            }

            case "SECRET_OWNER" -> {
                String owner = msg.getField(1);
                if (owner == null || owner.isBlank()) {
                    System.out.println("Aucun joueur n'a encore défini le secret.");
                } else {
                    System.out.println("Le secret a été défini par : " + owner);
                }
            }

            case "SECRET_ACCEPTED" -> System.out.println("Le secret a été accepté.");
            case "SECRET_ALREADY_SET" -> System.out.println("Le secret a déjà été défini par : " + msg.getField(1));

            case "GAME_STATUS" -> {
                System.out.println("Statut partie démarrée : " + msg.getField(1));
                System.out.println("Statut partie terminée : " + msg.getField(2));
                System.out.println("Gagnant : " + msg.getField(3));
            }

            case "GUESSES" -> {
                String history = msg.getField(1);
                if (history == null || history.isBlank()) {
                    System.out.println("Aucune proposition pour le moment.");
                } else {
                    System.out.println("Historique reçu.");
                }
            }

            case "ATTEMPTS_LEFT" -> {
                System.out.println("Joueur : " + msg.getField(1));
                System.out.println("Tentatives restantes : " + msg.getField(2));
                System.out.println("Tentatives max : " + msg.getField(3));
            }

            case "FEEDBACK" -> {
                System.out.println("Couleurs correctes : " + msg.getField(0));
                System.out.println("Positions correctes : " + msg.getField(1));
            }

            case "WINNER" -> System.out.println("Le gagnant est : " + msg.getField(1));

            case "SERVER_GAME_STARTED" -> System.out.println("Partie contre le serveur démarrée avec " + msg.getField(0) + " tentatives.");

            case "SERVER_WINNER" -> {
                System.out.println("Bravo ! Tu as gagné contre le serveur.");
                System.out.println("Tentatives utilisées : " + msg.getField(0) + "/" + msg.getField(1));
            }

            case "SERVER_GAME_OVER" -> {
                System.out.println("Partie contre le serveur terminée.");
                System.out.println("Tentatives utilisées : " + msg.getField(0) + "/" + msg.getField(1));
                System.out.println("Secret : " + msg.getField(2));
            }

            case "PLAYER_KICKED" -> System.out.println("Joueur expulsé : " + msg.getField(0));
            case "KICK_SUCCESS" -> System.out.println("Joueur expulsé : " + msg.getField(0));
            case "REPLAY_READY" -> System.out.println("Salle prête pour une nouvelle partie.");
            case "ERROR" -> System.out.println("Erreur : " + msg.getField(0));

            default -> {
                // rien de plus
            }
        }

        System.out.println("========================");
    }

    private boolean looksLikeCsvList(String type, int fieldIndex, String value) {
        if (value == null || value.isBlank() || !value.contains(",")) {
            return false;
        }

        return ("ROOM_LIST".equals(type) && fieldIndex == 0)
                || ("JOINED_ROOM".equals(type) && fieldIndex == 1)
                || ("GAME_STARTED".equals(type) && fieldIndex == 1)
                || ("ROOM_PLAYERS".equals(type) && fieldIndex == 1)
                || ("REPLAY_READY".equals(type) && fieldIndex == 1);
    }

    private int[] getAttemptsInfoForCurrentPlayer() throws IOException {
        if (currentRoom == null) {
            return new int[]{-1, -1};
        }

        sendMessage("GG|GET_ATTEMPTS_LEFT|" + currentRoom);
        GGMessage response = readParsedResponse();

        if (response != null && "ATTEMPTS_LEFT".equals(response.getType())) {
            try {
                int left = Integer.parseInt(response.getField(2));
                int max = Integer.parseInt(response.getField(3));
                return new int[]{left, max};
            } catch (Exception e) {
                return new int[]{-1, -1};
            }
        }

        return new int[]{-1, -1};
    }

    private void resetRoomGameStateOnly() {
        secretOwner = null;
        secretDefined = false;
        roomGameFinished = false;
        roomWinnerName = null;
    }

    private void resetRoomStateCompletely() {
        currentRoom = null;
        currentPlayers.clear();
        roomGameStarted = false;
        resetRoomGameStateOnly();
    }

    private void closeConnection() {
        safeClose(in);
        safeClose(socket);

        if (out != null) {
            out.close();
        }

        System.out.println("Client fermé.");
    }

    private void safeClose(Closeable resource) {
        if (resource == null) {
            return;
        }

        try {
            resource.close();
        } catch (IOException e) {
            System.out.println("[ERREUR] Fermeture ressource : " + e.getMessage());
        }
    }
}