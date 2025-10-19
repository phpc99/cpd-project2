package server;

import java.io.PrintWriter;

/**
 * Represents a user session with a unique token.
 * Stores the username, associated room (if any), output writer, and expiration time.
 */
public class Session {
    private final String username;
    private ChatRoom room;
    private PrintWriter writer;
    private final long expiryTimeMillis; // Absolute expiration timestamp

    // Constructor initializes session data and calculates expiration time
    public Session(String username, ChatRoom room, PrintWriter writer, long validityMillis) {
        this.username = username;
        this.room = room;
        this.writer = writer;
        this.expiryTimeMillis = System.currentTimeMillis() + validityMillis;
    }
    // Getter for username
    public String getUsername() { return username; }
    // Getters and setters for the chat room
    public ChatRoom getRoom() { return room; }
    public void setRoom(ChatRoom room) { this.room = room; }
    // Getters and setters for the writer (used to send messages to the client)
    public PrintWriter getWriter() { return writer; }
    public void setWriter(PrintWriter writer) { this.writer = writer; }
    // Checks whether the session has expired
    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTimeMillis;
    }

    /**
     * Converts this session to a string format for saving to a file.
     * Format: username:roomName:remainingTime:creationTimestamp
     */
    public String toFileString() {
        String roomName = (room != null) ? room.getName() : "";
        return String.format("%s:%s:%s:%d", username, roomName, expiryTimeMillis, System.currentTimeMillis());
    }

    /**
     * Recreates a Session object from a string line in the sessions file.
     * @param token Token string (not used here but may be relevant externally)
     * @param line Encoded session info from file
     * @param dummyWriter Placeholder PrintWriter since real client writer is unavailable
     * @return Valid Session or null if invalid/expired
     */
    public static Session fromFileString(String token, String line, PrintWriter dummyWriter) {
        try {
            String[] parts = line.split(":");
            if (parts.length < 4) return null;
    
            String username = parts[0];
            String roomName = parts[1].isEmpty() ? null : parts[1];
            long delta = Long.parseLong(parts[2]);           // Remaining time when saved
            long creationTime = Long.parseLong(parts[3]);     // Timestamp when saved
    
            long expiryTimeMillis = creationTime + delta;
            long remaining = expiryTimeMillis - System.currentTimeMillis();
            if (remaining <= 0) return null; // Session already expired
    
            ChatRoom room = (roomName != null) ? ServerMain.getOrCreateRoom(roomName) : null;
            return new Session(username, room, dummyWriter, remaining);
        } catch (Exception e) {
            return null; // Fail-safe for malformed lines
        }
    }
    
    // Getter for the absolute expiration time
    public long getExpiryTimeMillis() {
        return expiryTimeMillis;
    }
    
}
