import java.io.PrintWriter;
import java.net.Socket;

public class PlayerInfo {
    private final String playerName;
    private final Socket socket;
    private final PrintWriter out;
    private String currentRoom;

    public PlayerInfo(String playerName, Socket socket, PrintWriter out) {
        this.playerName = playerName == null ? "" : playerName.trim();
        this.socket = socket;
        this.out = out;
        this.currentRoom = null;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Socket getSocket() {
        return socket;
    }

    public PrintWriter getOut() {
        return out;
    }

    public synchronized String getCurrentRoom() {
        return currentRoom;
    }

    public synchronized void setCurrentRoom(String currentRoom) {
        if (currentRoom == null || currentRoom.isBlank()) {
            this.currentRoom = null;
        } else {
            this.currentRoom = currentRoom.trim();
        }
    }
}