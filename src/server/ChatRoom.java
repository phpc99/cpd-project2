package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// This class represents a chat room that supports multiple participants
// and maintains a history of messages. It is thread-safe.
public class ChatRoom {
    private final String name; // Name of the chat room
    private final List<String> messages = new ArrayList<>(); // Message history
    private final Set<PrintWriter> participants = new HashSet<>(); // Active participants (output streams)
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(); // Lock to handle concurrent access

    // Constructor: creates a chat room with a given name
    public ChatRoom(String name) {
        this.name = name;
    }

    // Adds a user to the room and logs their entry in the message history
    public void join(PrintWriter out, String username) {
        lock.writeLock().lock();
        try {
            participants.add(out); // Add user's output stream
            messages.add("[" + username + " enters the room]"); // Record entry in message history
            
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Removes a user from the room and broadcasts that they left
    public void leave(PrintWriter out, String username) {
        lock.writeLock().lock();
        try {
            participants.remove(out); // Remove user's output stream
            broadcast("[" + username + " leaves the room]"); // Notify others
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Sends a message to all current participants and stores it in the history
    public void broadcast(String message) {
        lock.readLock().lock();
        try {
            messages.add(message); // Save the message
            for (PrintWriter out : participants) {
                out.println(message); // Send to each participant
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    // Returns a copy of the message history
    public List<String> getMessages() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(messages); // Defensive copy
        } finally {
            lock.readLock().unlock();
        }
    }

    // Returns the name of the chat room
    public String getName() {
        return name;
    }

    // Re-adds a participant's new connection (used during reconnection)
    public void rejoin(PrintWriter out, String username) {
        lock.writeLock().lock();
        try {
            participants.add(out); // Re-add output stream
            // No broadcast message, since this is a silent reconnection
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // Saves the chat history to a file named based on the room name
    public void saveMessagesToFile() {
        lock.readLock().lock();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("chat_" + name + ".txt"))) {
            for (String msg : messages) {
                writer.write(msg);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error saving room messages '" + name + "': " + e.getMessage());
        } finally {
            lock.readLock().unlock();
        }
    }

    // Loads chat history from a file if it exists
    public void loadMessagesFromFile() {
        lock.writeLock().lock();
        try (BufferedReader reader = new BufferedReader(new FileReader("chat_" + name + ".txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                messages.add(line); // Restore past messages
            }
        } catch (IOException e) {
            // Do nothing if the file doesn't exist — this is expected for a new room
        } finally {
            lock.writeLock().unlock();
        }
    }

}
