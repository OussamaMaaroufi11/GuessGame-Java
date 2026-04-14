import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {
    private final Map<String, Room> rooms;

    public RoomManager() {
        this.rooms = new ConcurrentHashMap<>();
    }

    private String normalizeRoomKey(String roomName) {
        return roomName == null ? "" : roomName.trim().toLowerCase();
    }

    public boolean createRoom(String roomName, int maxPlayers, int maxAttempts, PlayerInfo admin) {
        if (roomName == null || roomName.trim().isEmpty() || admin == null) {
            return false;
        }

        if (maxPlayers < 2 || maxAttempts < 1) {
            return false;
        }

        String cleanRoomName = roomName.trim();
        String key = normalizeRoomKey(cleanRoomName);

        Room room = new Room(cleanRoomName, maxPlayers, maxAttempts, admin.getPlayerName());
        return rooms.putIfAbsent(key, room) == null;
    }

    public boolean createDefaultRoom(String roomName, int maxPlayers, int maxAttempts) {
        if (roomName == null || roomName.trim().isEmpty()) {
            return false;
        }

        if (maxPlayers < 2 || maxAttempts < 1) {
            return false;
        }

        String cleanRoomName = roomName.trim();
        String key = normalizeRoomKey(cleanRoomName);

        Room room = new Room(cleanRoomName, maxPlayers, maxAttempts, "SYSTEM");
        return rooms.putIfAbsent(key, room) == null;
    }

    public Room getRoom(String roomName) {
        return rooms.get(normalizeRoomKey(roomName));
    }

    public boolean roomExists(String roomName) {
        return getRoom(roomName) != null;
    }

    public boolean joinRoom(String roomName, PlayerInfo player) {
        Room room = getRoom(roomName);
        if (room == null || player == null) {
            return false;
        }
        return room.addPlayer(player);
    }

    public boolean leaveRoom(String roomName, String playerName) {
        Room room = getRoom(roomName);
        if (room == null) {
            return false;
        }

        return room.removePlayer(playerName);
    }

    public List<String> listRoomNames() {
        List<String> names = new ArrayList<>();

        for (Room room : rooms.values()) {
            names.add(room.getRoomName());
        }

        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public String getRoomsAsCsv() {
        return String.join(",", listRoomNames());
    }
}