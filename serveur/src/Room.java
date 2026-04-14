import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Room {
    private final String roomName;
    private final int maxPlayers;
    private final int maxAttempts;

    private String adminName;
    private final List<PlayerInfo> players;

    private boolean gameStarted;
    private boolean gameFinished;
    private String winnerName;

    private String secretOwner;
    private String[] secretCombination;
    private final List<String> guessesHistory;

    private final Map<String, Integer> playerAttempts;

    public Room(String roomName, int maxPlayers, int maxAttempts, String adminName) {
        this.roomName = roomName == null ? "" : roomName.trim();
        this.maxPlayers = maxPlayers;
        this.maxAttempts = maxAttempts;
        this.adminName = adminName == null ? "SYSTEM" : adminName.trim();
        this.players = new ArrayList<>();

        this.gameStarted = false;
        this.gameFinished = false;
        this.winnerName = null;

        this.secretOwner = null;
        this.secretCombination = null;
        this.guessesHistory = new ArrayList<>();
        this.playerAttempts = new HashMap<>();
    }

    private String normalizePlayerKey(String playerName) {
        return playerName == null ? "" : playerName.trim().toLowerCase();
    }

    public synchronized String getRoomName() {
        return roomName;
    }

    public synchronized int getMaxPlayers() {
        return maxPlayers;
    }

    public synchronized int getMaxAttempts() {
        return maxAttempts;
    }

    public synchronized String getAdminName() {
        return adminName;
    }

    public synchronized boolean isAdmin(String playerName) {
        return playerName != null
                && adminName != null
                && adminName.equalsIgnoreCase(playerName.trim());
    }

    public synchronized boolean addPlayer(PlayerInfo player) {
        if (player == null) {
            return false;
        }

        if (players.size() >= maxPlayers) {
            return false;
        }

        if (containsPlayer(player.getPlayerName())) {
            return false;
        }

        players.add(player);
        player.setCurrentRoom(roomName);

        if (adminName == null || adminName.isBlank() || "SYSTEM".equalsIgnoreCase(adminName)) {
            adminName = player.getPlayerName();
        }

        return true;
    }

    public synchronized boolean removePlayer(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }

        PlayerInfo target = null;

        for (PlayerInfo p : players) {
            if (p.getPlayerName().equalsIgnoreCase(playerName.trim())) {
                target = p;
                break;
            }
        }

        if (target == null) {
            return false;
        }

        boolean wasSecretOwner = secretOwner != null && playerName.equalsIgnoreCase(secretOwner);

        players.remove(target);
        target.setCurrentRoom(null);
        playerAttempts.remove(normalizePlayerKey(playerName));

        if (wasSecretOwner) {
            resetGame();
        }

        if (adminName != null && adminName.equalsIgnoreCase(playerName)) {
            if (players.isEmpty()) {
                adminName = "SYSTEM";
            } else {
                adminName = players.get(0).getPlayerName();
            }
        }

        if (players.size() < 2) {
            resetGame();
        }

        return true;
    }

    public synchronized boolean containsPlayer(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }

        for (PlayerInfo p : players) {
            if (p.getPlayerName().equalsIgnoreCase(playerName.trim())) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public synchronized boolean isEmpty() {
        return players.isEmpty();
    }

    public synchronized int getPlayerCount() {
        return players.size();
    }

    public synchronized List<PlayerInfo> getPlayers() {
        return new ArrayList<>(players);
    }

    public synchronized String getPlayersAsCsv() {
        return players.stream()
                .map(PlayerInfo::getPlayerName)
                .collect(Collectors.joining(","));
    }

    public synchronized boolean isGameStarted() {
        return gameStarted;
    }

    public synchronized void setGameStarted(boolean gameStarted) {
        this.gameStarted = gameStarted;

        if (gameStarted) {
            this.gameFinished = false;
            this.winnerName = null;
        }
    }

    public synchronized boolean isGameFinished() {
        return gameFinished;
    }

    public synchronized String getWinnerName() {
        return winnerName;
    }

    public synchronized void finishGame(String winnerName) {
        this.gameStarted = true;
        this.gameFinished = true;
        this.winnerName = winnerName;
    }

    public synchronized String getSecretOwner() {
        return secretOwner;
    }

    public synchronized boolean hasSecret() {
        return secretOwner != null
                && !secretOwner.isBlank()
                && secretCombination != null
                && secretCombination.length == 4;
    }

    public synchronized boolean setSecret(String owner, String[] combination) {
        if (owner == null || owner.isBlank() || combination == null || combination.length != 4) {
            return false;
        }

        if (hasSecret()) {
            return false;
        }

        secretOwner = owner.trim();
        secretCombination = combination.clone();
        playerAttempts.clear();

        return true;
    }

    public synchronized String[] getSecretCombination() {
        return secretCombination == null ? null : secretCombination.clone();
    }

    public synchronized void addGuessHistory(String guessLine) {
        if (guessLine != null && !guessLine.isBlank()) {
            guessesHistory.add(guessLine.trim());
        }
    }

    public synchronized String getGuessesHistoryAsCsv() {
        return String.join(";", guessesHistory);
    }

    public synchronized int getAttemptsUsed(String playerName) {
        return playerAttempts.getOrDefault(normalizePlayerKey(playerName), 0);
    }

    public synchronized int getAttemptsLeft(String playerName) {
        int left = maxAttempts - getAttemptsUsed(playerName);
        return Math.max(left, 0);
    }

    public synchronized boolean hasReachedMaxAttempts(String playerName) {
        return getAttemptsUsed(playerName) >= maxAttempts;
    }

    public synchronized int incrementAttempts(String playerName) {
        String key = normalizePlayerKey(playerName);
        int newValue = playerAttempts.getOrDefault(key, 0) + 1;
        playerAttempts.put(key, newValue);
        return newValue;
    }

    public synchronized void resetGame() {
        gameStarted = false;
        gameFinished = false;
        winnerName = null;
        secretOwner = null;
        secretCombination = null;
        guessesHistory.clear();
        playerAttempts.clear();
    }
}