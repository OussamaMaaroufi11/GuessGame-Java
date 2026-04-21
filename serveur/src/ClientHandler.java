import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ClientHandler extends Thread {
    private static final List<String> ALLOWED_COLORS = List.of(
            "ROUGE", "BLEU", "VERT", "JAUNE", "ORANGE"
    );

    private final Socket socket;
    private final RoomManager roomManager;
    private final Map<String, PlayerInfo> connectedPlayers;

    private BufferedReader in;
    private PrintWriter out;
    private PlayerInfo currentPlayer;

    private boolean serverGameActive = false;
    private String[] serverSecret = null;
    private int serverMaxAttempts = 0;
    private int serverUsedAttempts = 0;
    private final List<String> serverGuessHistory = new ArrayList<>();

    public ClientHandler(Socket socket, RoomManager roomManager, Map<String, PlayerInfo> connectedPlayers) {
        this.socket = socket;
        this.roomManager = roomManager;
        this.connectedPlayers = connectedPlayers;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String line;
            while ((line = in.readLine()) != null) {
                displayReceivedMessage(line);

                GGMessage message = MessageParser.parse(line);

                if (message == null) {
                    sendError("INVALID_MESSAGE");
                    continue;
                }

                if (!"GG".equalsIgnoreCase(message.getPrefix())) {
                    sendError("INVALID_PREFIX");
                    continue;
                }

                handleMessage(message);
            }

        } catch (IOException e) {
            System.out.println("[INFO] Client déconnecté brutalement : " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleMessage(GGMessage message) {
        String type = message.getType();

        switch (type) {
            case "CONNECT" -> handleConnect(message);
            case "LIST_ROOMS" -> handleListRooms();
            case "CREATE_ROOM" -> handleCreateRoom(message);
            case "JOIN_ROOM" -> handleJoinRoom(message);
            case "LEAVE_ROOM" -> handleLeaveRoom(message);
            case "START_GAME" -> handleStartGame(message);
            case "GET_SECRET_OWNER" -> handleGetSecretOwner(message);
            case "SECRET_SET" -> handleSecretSet(message);
            case "GUESS" -> handleGuess(message);
            case "GET_GUESSES" -> handleGetGuesses(message);
            case "GET_GAME_STATUS" -> handleGetGameStatus(message);
            case "GET_ATTEMPTS_LEFT" -> handleGetAttemptsLeft(message);
            case "GET_ROOM_PLAYERS" -> handleGetRoomPlayers(message);
            case "NEW_GAME" -> handleNewGame(message);
            case "KICK_PLAYER" -> handleKickPlayer(message);

            case "PLAY_SERVER" -> handlePlayServer(message);
            case "PLAY_SERVER_GUESS" -> handlePlayServerGuess(message);
            case "PLAY_SERVER_HISTORY" -> handlePlayServerHistory();
            case "PLAY_SERVER_QUIT" -> handlePlayServerQuit();

            default -> sendError("UNKNOWN_COMMAND");
        }
    }

    private String normalizePlayerKey(String playerName) {
        return playerName == null ? "" : playerName.trim().toLowerCase();
    }

    private void handleConnect(GGMessage message) {
        String playerName = message.getField(0);

        if (playerName == null || playerName.isBlank()) {
            sendError("INVALID_PLAYER_NAME");
            return;
        }

        String trimmedName = playerName.trim();
        String playerKey = normalizePlayerKey(trimmedName);

        PlayerInfo newPlayer = new PlayerInfo(trimmedName, socket, out);
        PlayerInfo existing = connectedPlayers.putIfAbsent(playerKey, newPlayer);

        if (existing != null) {
            sendError("PLAYER_ALREADY_CONNECTED");
            return;
        }

        currentPlayer = newPlayer;
        send("GG|CONNECTED|" + trimmedName);
    }

    private void handleListRooms() {
        if (!ensureConnected()) {
            return;
        }
        send("GG|ROOM_LIST|" + roomManager.getRoomsAsCsv());
    }

    private void handleCreateRoom(GGMessage message) {
        if (!ensureConnected()) {
            return;
        }

        String roomName = message.getField(0);
        String maxPlayersStr = message.getField(1);
        String maxAttemptsStr = message.getField(2);

        if (roomName == null || maxPlayersStr == null || maxAttemptsStr == null) {
            sendError("INVALID_CREATE_ROOM_FORMAT");
            return;
        }

        int maxPlayers;
        int maxAttempts;

        try {
            maxPlayers = Integer.parseInt(maxPlayersStr);
            maxAttempts = Integer.parseInt(maxAttemptsStr);
        } catch (NumberFormatException e) {
            sendError("INVALID_NUMBERS");
            return;
        }

        if (maxPlayers < 2 || maxAttempts < 1) {
            sendError("INVALID_ROOM_PARAMETERS");
            return;
        }

        if (currentPlayer.getCurrentRoom() != null) {
            sendError("PLAYER_ALREADY_IN_ROOM");
            return;
        }

        boolean created = roomManager.createRoom(roomName, maxPlayers, maxAttempts, currentPlayer);
        if (!created) {
            sendError("ROOM_ALREADY_EXISTS");
            return;
        }

        send("GG|ROOM_CREATED|" + roomName.trim());
    }

    private void handleJoinRoom(GGMessage message) {
        if (!ensureConnected()) {
            return;
        }

        String roomName = message.getField(0);
        if (roomName == null || roomName.isBlank()) {
            sendError("INVALID_ROOM_NAME");
            return;
        }

        if (currentPlayer.getCurrentRoom() != null) {
            sendError("PLAYER_ALREADY_IN_ROOM");
            return;
        }

        Room room = roomManager.getRoom(roomName);
        if (room == null) {
            sendError("ROOM_NOT_FOUND");
            return;
        }

        if (room.isFull()) {
            sendError("ROOM_FULL");
            return;
        }

        boolean joined = roomManager.joinRoom(roomName, currentPlayer);
        if (!joined) {
            sendError("JOIN_FAILED");
            return;
        }

        send("GG|JOINED_ROOM|" + room.getRoomName() + "|" + room.getPlayersAsCsv());
    }

    private void handleLeaveRoom(GGMessage message) {
        if (!ensureConnected()) {
            return;
        }

        String roomName = message.getField(0);
        if (roomName == null || roomName.isBlank()) {
            sendError("INVALID_ROOM_NAME");
            return;
        }

        if (currentPlayer.getCurrentRoom() == null) {
            sendError("PLAYER_NOT_IN_ROOM");
            return;
        }

        if (!roomName.equalsIgnoreCase(currentPlayer.getCurrentRoom())) {
            sendError("ROOM_MISMATCH");
            return;
        }

        boolean left = roomManager.leaveRoom(currentPlayer.getCurrentRoom(), currentPlayer.getPlayerName());
        if (!left) {
            sendError("LEAVE_FAILED");
            return;
        }

        currentPlayer.setCurrentRoom(null);
        send("GG|LEFT_ROOM|" + roomName);
    }

    private void handleStartGame(GGMessage message) {
        if (!ensureConnected()) {
            return;
        }

        String roomName = message.getField(0);
        if (roomName == null || roomName.isBlank()) {
            sendError("INVALID_ROOM_NAME");
            return;
        }

        Room room = roomManager.getRoom(roomName);
        if (room == null) {
            sendError("ROOM_NOT_FOUND");
            return;
        }

        if (!room.containsPlayer(currentPlayer.getPlayerName())) {
            sendError("PLAYER_NOT_IN_ROOM");
            return;
        }

        if (room.getPlayerCount() < 2) {
            sendError("NOT_ENOUGH_PLAYERS");
            return;
        }

        if (room.isGameStarted()) {
            sendError("GAME_ALREADY_STARTED");
            return;
        }

        room.setGameStarted(true);
        broadcastToRoom(room, "GG|GAME_STARTED|" + room.getRoomName() + "|" + room.getPlayersAsCsv());
    }

    private void handleGetSecretOwner(GGMessage message) {
        if (!ensureConnected()) {
            return;
        }

        String roomName = message.getField(0);
        if (roomName == null || roomName.isBlank()) {
            sendError("INVALID_ROOM_NAME");
            return;
        }

        Room room = roomManager.getRoom(roomName);
        if (room == null) {
            sendError("ROOM_NOT_FOUND");
            return;
        }

        String owner = room.getSecretOwner();
        if (owner == null) {
            owner = "";
        }

        send("GG|SECRET_OWNER|" + room.getRoomName() + "|" + owner);
    }

    private void handleSecretSet(GGMessage message) {
        if (!ensureConnected()) {
            return;
        }

        String roomName = message.getField(0);
        if (roomName == null || roomName.isBlank() || message.getFieldCount() < 6) {
            sendError("INVALID_SECRET_SET_FORMAT");
            return;
        }

        Room room = roomManager.getRoom(roomName);
        if (room == null) {
            sendError("ROOM_NOT_FOUND");
            return;
        }

        if (!room.containsPlayer(currentPlayer.getPlayerName())) {
            sendError("PLAYER_NOT_IN_ROOM");
            return;
        }

        if (!room.isGameStarted()) {
            sendError("GAME_NOT_STARTED");
            return;
        }

        if (room.hasSecret()) {
            send("GG|SECRET_ALREADY_SET|" + room.getRoomName() + "|" + room.getSecretOwner());
            return;
        }

        String[] combo = new String[4];
        combo[0] = normalizeColor(message.getField(2));
        combo[1] = normalizeColor(message.getField(3));
        combo[2] = normalizeColor(message.getField(4));
        combo[3] = normalizeColor(message.getField(5));

        for (String c : combo) {
            if (!ALLOWED_COLORS.contains(c)) {
                sendError("INVALID_SECRET_COLOR");
                return;
            }
        }

        String who = currentPlayer.getPlayerName();
        boolean ok = room.setSecret(who, combo);

        if (!ok) {
            send("GG|SECRET_ALREADY_SET|" + room.getRoomName() + "|" + room.getSecretOwner());
            return;
        }

        send("GG|SECRET_ACCEPTED|" + room.getRoomName() + "|" + who);
    }

    private void handleGuess(GGMessage message) {
        if (!ensureConnected()) {
            return;
        }

        String roomName = message.getField(0);
        if (roomName == null || roomName.isBlank() || message.getFieldCount() < 6) {
            sendError("INVALID_GUESS_FORMAT");
            return;
        }

        Room room = roomManager.getRoom(roomName);
        if (room == null) {
            sendError("ROOM_NOT_FOUND");
            return;
        }

        if (!room.containsPlayer(currentPlayer.getPlayerName())) {
            sendError("PLAYER_NOT_IN_ROOM");
            return;
        }

        if (room.isGameFinished()) {
            send("GG|GAME_FINISHED|" + room.getRoomName() + "|" + room.getWinnerName());
            return;
        }

        if (!room.isGameStarted()) {
            sendError("GAME_NOT_STARTED");
            return;
        }

        if (!room.hasSecret()) {
            sendError("NO_SECRET_DEFINED");
            return;
        }

        String who = currentPlayer.getPlayerName();

        if (room.getSecretOwner().equalsIgnoreCase(who)) {
            sendError("SECRET_OWNER_CANNOT_GUESS");
            return;
        }

        if (room.hasReachedMaxAttempts(who)) {
            sendError("MAX_ATTEMPTS_REACHED");
            return;
        }

        String[] guess = new String[4];
        guess[0] = normalizeColor(message.getField(2));
        guess[1] = normalizeColor(message.getField(3));
        guess[2] = normalizeColor(message.getField(4));
        guess[3] = normalizeColor(message.getField(5));

        for (String c : guess) {
            if (!ALLOWED_COLORS.contains(c)) {
                sendError("INVALID_GUESS_COLOR");
                return;
            }
        }

        int usedAttempts = room.incrementAttempts(who);
        room.addGuessHistory(who + ":" + String.join(",", guess));

        int[] feedback = computeFeedback(room.getSecretCombination(), guess);

        send("GG|FEEDBACK|" + feedback[0] + "|" + feedback[1]);

        if (feedback[1] == 4) {
            room.finishGame(who);
            broadcastToRoom(room, "GG|WINNER|" + room.getRoomName() + "|" + who);
            return;
        }

        if (usedAttempts >= room.getMaxAttempts()) {
            System.out.println("[INFO] " + who + " a atteint la limite de tentatives dans la salle " + room.getRoomName());
        }
    }

    private void handleGetGuesses(GGMessage message) {
        if (!ensureConnected()) {
            return;
        }

        String roomName = message.getField(0);
        if (roomName == null || roomName.isBlank()) {
            sendError("INVALID_ROOM_NAME");
            return;
        }

        Room room = roomManager.getRoom(roomName);
        if (room == null) {
            sendError("ROOM_NOT_FOUND");
            return;
        }

        send("GG|GUESSES|" + room.getRoomName() + "|" + room.getGuessesHistoryAsCsv());
    }

    private void handleGetGameStatus(GGMessage message) {
        if (!ensureConnected()) {
            return;
        }

        String roomName = message.getField(0);
        if (roomName == null || roomName.isBlank()) {
            sendError("INVALID_ROOM_NAME");
            return;
        }

        Room room = roomManager.getRoom(roomName);
        if (room == null) {
            sendError("ROOM_NOT_FOUND");
            return;
        }

        String winner = room.getWinnerName() == null ? "" : room.getWinnerName();

        send("GG|GAME_STATUS|"
                + room.getRoomName() + "|"
                + room.isGameStarted() + "|"
                + room.isGameFinished() + "|"
                + winner);
    }

    private void handleGetAttemptsLeft(GGMessage message) {
        if (!ensureConnected()) {
            return;
        }

        String roomName = message.getField(0);
        if (roomName == null || roomName.isBlank()) {
            sendError("INVALID_ROOM_NAME");
            return;
        }

        Room room = roomManager.getRoom(roomName);
        if (room == null) {
            sendError("ROOM_NOT_FOUND");
            return;
        }

        if (!room.containsPlayer(currentPlayer.getPlayerName())) {
            sendError("PLAYER_NOT_IN_ROOM");
            return;
        }

        int left = room.getAttemptsLeft(currentPlayer.getPlayerName());

        send("GG|ATTEMPTS_LEFT|"
                + room.getRoomName() + "|"
                + currentPlayer.getPlayerName() + "|"
                + left + "|"
                + room.getMaxAttempts());
    }

    private void handleGetRoomPlayers(GGMessage message) {
        if (!ensureConnected()) {
            return;
        }

        String roomName = message.getField(0);
        if (roomName == null || roomName.isBlank()) {
            sendError("INVALID_ROOM_NAME");
            return;
        }

        Room room = roomManager.getRoom(roomName);
        if (room == null) {
            sendError("ROOM_NOT_FOUND");
            return;
        }

        send("GG|ROOM_PLAYERS|" + room.getRoomName() + "|" + room.getPlayersAsCsv());
    }

    private void handleNewGame(GGMessage message) {
        if (!ensureConnected()) {
            return;
        }

        String roomName = message.getField(0);
        if (roomName == null || roomName.isBlank()) {
            sendError("INVALID_ROOM_NAME");
            return;
        }

        Room room = roomManager.getRoom(roomName);
        if (room == null) {
            sendError("ROOM_NOT_FOUND");
            return;
        }

        if (!room.containsPlayer(currentPlayer.getPlayerName())) {
            sendError("PLAYER_NOT_IN_ROOM");
            return;
        }

        room.resetGame();
        broadcastToRoom(room, "GG|NEW_GAME|" + room.getRoomName() + "|" + room.getPlayersAsCsv());
    }

    private void handleKickPlayer(GGMessage message) {
        if (!ensureConnected()) {
            return;
        }

        String roomName = message.getField(0);
        String targetName = message.getField(1);

        if (roomName == null || roomName.isBlank() || targetName == null || targetName.isBlank()) {
            sendError("INVALID_KICK_FORMAT");
            return;
        }

        Room room = roomManager.getRoom(roomName);
        if (room == null) {
            sendError("ROOM_NOT_FOUND");
            return;
        }

        if (!room.containsPlayer(currentPlayer.getPlayerName())) {
            sendError("PLAYER_NOT_IN_ROOM");
            return;
        }

        if (room.getSecretOwner() == null
                || !room.getSecretOwner().equalsIgnoreCase(currentPlayer.getPlayerName())) {
            sendError("ONLY_SECRET_OWNER_CAN_KICK");
            return;
        }

        if (!room.containsPlayer(targetName)) {
            sendError("TARGET_PLAYER_NOT_IN_ROOM");
            return;
        }

        if (targetName.equalsIgnoreCase(currentPlayer.getPlayerName())) {
            sendError("CANNOT_KICK_SELF");
            return;
        }

        PlayerInfo targetPlayer = connectedPlayers.get(normalizePlayerKey(targetName));
        if (targetPlayer == null) {
            sendError("TARGET_PLAYER_NOT_FOUND");
            return;
        }

        boolean removed = roomManager.leaveRoom(roomName, targetName);
        if (!removed) {
            sendError("KICK_FAILED");
            return;
        }

        targetPlayer.setCurrentRoom(null);

        PrintWriter targetOut = targetPlayer.getOut();
        if (targetOut != null) {
            targetOut.println("GG|PLAYER_KICKED|" + targetName);
            targetOut.flush();
        }

        send("GG|KICK_SUCCESS|" + targetName);
    }

    private void handlePlayServer(GGMessage message) {
        if (!ensureConnected()) {
            return;
        }

        String maxAttemptsStr = message.getField(0);
        if (maxAttemptsStr == null || maxAttemptsStr.isBlank()) {
            sendError("INVALID_PLAY_SERVER_FORMAT");
            return;
        }

        int maxAttempts;
        try {
            maxAttempts = Integer.parseInt(maxAttemptsStr);
        } catch (NumberFormatException e) {
            sendError("INVALID_NUMBERS");
            return;
        }

        if (maxAttempts < 1) {
            sendError("INVALID_PLAY_SERVER_ATTEMPTS");
            return;
        }

        serverGameActive = true;
        serverMaxAttempts = maxAttempts;
        serverUsedAttempts = 0;
        serverGuessHistory.clear();
        serverSecret = generateRandomSecret();

        System.out.println("[MODE SERVEUR] Secret généré pour "
                + currentPlayer.getPlayerName() + " : "
                + String.join(",", serverSecret));

        send("GG|SERVER_GAME_STARTED|" + serverMaxAttempts);
    }

    private void handlePlayServerGuess(GGMessage message) {
        if (!ensureConnected()) {
            return;
        }

        if (!serverGameActive || serverSecret == null) {
            sendError("NO_SERVER_GAME_ACTIVE");
            return;
        }

        if (message.getFieldCount() < 4) {
            sendError("INVALID_PLAY_SERVER_GUESS_FORMAT");
            return;
        }

        String[] guess = new String[4];
        guess[0] = normalizeColor(message.getField(0));
        guess[1] = normalizeColor(message.getField(1));
        guess[2] = normalizeColor(message.getField(2));
        guess[3] = normalizeColor(message.getField(3));

        for (String c : guess) {
            if (!ALLOWED_COLORS.contains(c)) {
                sendError("INVALID_GUESS_COLOR");
                return;
            }
        }

        serverUsedAttempts++;
        int[] feedback = computeFeedback(serverSecret, guess);
        serverGuessHistory.add(String.join(",", guess));

        send("GG|FEEDBACK|" + feedback[0] + "|" + feedback[1]);

        if (feedback[1] == 4) {
            send("GG|SERVER_WINNER|" + serverUsedAttempts + "|" + serverMaxAttempts);
            clearServerGame();
            return;
        }

        if (serverUsedAttempts >= serverMaxAttempts) {
            send("GG|SERVER_GAME_OVER|" + serverUsedAttempts + "|" + serverMaxAttempts + "|" + String.join(",", serverSecret));
            clearServerGame();
        }
    }

    private void handlePlayServerHistory() {
        if (!ensureConnected()) {
            return;
        }

        String history = String.join(";", serverGuessHistory);
        send("GG|GUESSES|SERVER|" + history);
    }

    private void handlePlayServerQuit() {
        if (!ensureConnected()) {
            return;
        }

        clearServerGame();
        send("GG|LEFT_ROOM|SERVER");
    }

    private String[] generateRandomSecret() {
        String[] secret = new String[4];
        for (int i = 0; i < 4; i++) {
            secret[i] = ALLOWED_COLORS.get(ThreadLocalRandom.current().nextInt(ALLOWED_COLORS.size()));
        }
        return secret;
    }

    private void clearServerGame() {
        serverGameActive = false;
        serverSecret = null;
        serverMaxAttempts = 0;
        serverUsedAttempts = 0;
        serverGuessHistory.clear();
    }

    private String normalizeColor(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private int[] computeFeedback(String[] secret, String[] guess) {
        int couleursCorrectes = 0;
        int positionsCorrectes = 0;

        int[] secretCount = new int[ALLOWED_COLORS.size()];
        int[] guessCount = new int[ALLOWED_COLORS.size()];

        for (int i = 0; i < 4; i++) {
            if (secret[i].equals(guess[i])) {
                positionsCorrectes++;
            }

            int secretIndex = ALLOWED_COLORS.indexOf(secret[i]);
            int guessIndex = ALLOWED_COLORS.indexOf(guess[i]);

            if (secretIndex >= 0) {
                secretCount[secretIndex]++;
            }
            if (guessIndex >= 0) {
                guessCount[guessIndex]++;
            }
        }

        for (int i = 0; i < ALLOWED_COLORS.size(); i++) {
            couleursCorrectes += Math.min(secretCount[i], guessCount[i]);
        }

        return new int[]{couleursCorrectes, positionsCorrectes};
    }

    private boolean ensureConnected() {
        if (currentPlayer == null) {
            sendError("PLAYER_NOT_CONNECTED");
            return false;
        }
        return true;
    }

    private void broadcastToRoom(Room room, String message) {
        if (room == null || message == null || message.isBlank()) {
            return;
        }

        GGMessage msg = MessageParser.parse(message);

        System.out.println("=== MESSAGE ENVOYÉ (BROADCAST) ===");
        if (msg == null) {
            System.out.println("Brut : " + message);
        } else {
            System.out.println("Type : " + msg.getType());
            if ("GAME_STARTED".equals(msg.getType())) {
                System.out.println("Partie démarrée dans : " + msg.getField(0));
                System.out.println("Joueurs : " + msg.getField(1));
            } else if ("WINNER".equals(msg.getType())) {
                System.out.println("Gagnant annoncé : " + msg.getField(1));
            } else if ("NEW_GAME".equals(msg.getType())) {
                System.out.println("Salle prête pour une nouvelle partie : " + msg.getField(0));
                System.out.println("Joueurs : " + msg.getField(1));
            } else {
                System.out.println("Brut : " + message);
            }
        }
        System.out.println("=================================");

        for (PlayerInfo player : room.getPlayers()) {
            PrintWriter playerOut = player.getOut();
            if (playerOut != null) {
                playerOut.println(message);
                playerOut.flush();
            }
        }
    }

    private void send(String message) {
        GGMessage msg = MessageParser.parse(message);

        System.out.println("=== MESSAGE ENVOYÉ ===");

        if (msg == null) {
            System.out.println("Brut : " + message);
            System.out.println("======================");
            out.println(message);
            out.flush();
            return;
        }

        String type = msg.getType();
        System.out.println("Type : " + type);

        switch (type) {
            case "CONNECTED" -> System.out.println("Connexion acceptée pour : " + msg.getField(0));
            case "ROOM_CREATED" -> System.out.println("Salle créée : " + msg.getField(0));
            case "ROOM_LIST" -> {
                String rooms = msg.getField(0);
                System.out.println("Liste des salles : " + (rooms == null || rooms.isBlank() ? "Aucune" : rooms));
            }
            case "JOINED_ROOM" -> {
                System.out.println("Salle rejointe : " + msg.getField(0));
                System.out.println("Joueurs : " + msg.getField(1));
            }
            case "LEFT_ROOM" -> {
                System.out.println("Sortie de : " + msg.getField(0));
            }
            case "ROOM_PLAYERS" -> {
                System.out.println("Joueurs actuels : " + msg.getField(1));
            }
            case "GAME_STARTED" -> {
                System.out.println("Partie démarrée dans : " + msg.getField(0));
                System.out.println("Joueurs : " + msg.getField(1));
            }
            case "SECRET_OWNER" -> {
                String owner = msg.getField(1);
                if (owner == null || owner.isBlank()) {
                    System.out.println("Aucun secret défini pour la salle : " + msg.getField(0));
                } else {
                    System.out.println("Secret défini par : " + owner);
                }
            }
            case "SECRET_ACCEPTED" -> {
                System.out.println("Secret accepté pour : " + msg.getField(0));
                System.out.println("Défini par : " + msg.getField(1));
            }
            case "SECRET_ALREADY_SET" -> {
                System.out.println("Secret déjà défini par : " + msg.getField(1));
            }
            case "GAME_STATUS" -> {
                String started = msg.getField(1);
                String finished = msg.getField(2);
                String winner = msg.getField(3);

                if (!"true".equalsIgnoreCase(started)) {
                    System.out.println("Statut : partie non démarrée");
                } else if ("true".equalsIgnoreCase(finished)) {
                    if (winner == null || winner.isBlank()) {
                        System.out.println("Statut : partie terminée");
                    } else {
                        System.out.println("Statut : partie terminée, gagnant = " + winner);
                    }
                } else {
                    System.out.println("Statut : partie démarrée");
                }
            }
            case "ATTEMPTS_LEFT" -> {
                System.out.println("Tentatives restantes pour " + msg.getField(1) + " : " + msg.getField(2) + "/" + msg.getField(3));
            }
            case "GUESSES" -> {
                String history = msg.getField(1);
                System.out.println("Historique : " + (history == null || history.isBlank() ? "vide" : history));
            }
            case "FEEDBACK" -> {
                System.out.println("Feedback envoyé : couleurs=" + msg.getField(0) + ", positions=" + msg.getField(1));
            }
            case "WINNER" -> System.out.println("Gagnant annoncé : " + msg.getField(1));
            case "SERVER_GAME_STARTED" -> System.out.println("Partie serveur démarrée, tentatives max = " + msg.getField(0));
            case "SERVER_WINNER" -> System.out.println("Victoire contre le serveur.");
            case "SERVER_GAME_OVER" -> System.out.println("Partie serveur perdue.");
            case "PLAYER_KICKED" -> System.out.println("Joueur expulsé : " + msg.getField(0));
            case "KICK_SUCCESS" -> System.out.println("Joueur expulsé : " + msg.getField(0));
            case "NEW_GAME" -> System.out.println("Salle prête pour une nouvelle partie : " + msg.getField(0));
            case "ERROR" -> System.out.println("Erreur envoyée : " + msg.getField(0));
            default -> System.out.println("Brut : " + message);
        }

        System.out.println("======================");
        out.println(message);
        out.flush();
    }

    private void sendError(String errorCode) {
        send("GG|ERROR|" + errorCode);
    }

    private void displayReceivedMessage(String raw) {
        GGMessage msg = MessageParser.parse(raw);

        System.out.println("\n=== MESSAGE REÇU ===");
        System.out.println("Brut : " + raw);

        if (msg == null) {
            System.out.println("Type : MESSAGE_INVALIDE");
            System.out.println("======================");
            return;
        }

        System.out.println("Type : " + msg.getType());
        List<String> fields = msg.getFields();
        for (int i = 0; i < fields.size(); i++) {
            System.out.println("Champ " + (i + 1) + " : " + fields.get(i));
        }
        System.out.println("=====================");
    }

    private void cleanup() {
        clearServerGame();

        try {
            if (currentPlayer != null) {
                String currentRoomName = currentPlayer.getCurrentRoom();
                if (currentRoomName != null) {
                    roomManager.leaveRoom(currentRoomName, currentPlayer.getPlayerName());
                }

                connectedPlayers.remove(normalizePlayerKey(currentPlayer.getPlayerName()));
                System.out.println("[INFO] Joueur déconnecté : " + currentPlayer.getPlayerName());
            }

            if (in != null) {
                in.close();
            }

            if (out != null) {
                out.close();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

        } catch (IOException e) {
            System.out.println("[ERREUR] Nettoyage client : " + e.getMessage());
        }
    }
}