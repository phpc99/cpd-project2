package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.net.ssl.*;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

public class ServerMain {
    private static final int PORT = 12345;
    // Stores all active chat rooms
    private static final Map<String, ChatRoom> chatRooms = new HashMap<>();
    private static final ReentrantReadWriteLock roomLock = new ReentrantReadWriteLock();
    // Stores all active sessions mapped by token
    private static final Map<String, Session> tokenSessions = new HashMap<>();
    private static final ReentrantReadWriteLock sessionLock = new ReentrantReadWriteLock();
    

    public static void main(String[] args) {
        // Configure SSL keystore for secure connections
        System.setProperty("javax.net.ssl.keyStore", "server_keystore.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");

        UserManager userManager = new UserManager("users.txt");
    
        // Load previously saved sessions on server startup
        loadSessionsFromFile("sessions.txt");
    
        // Save chat history when the server is shutting down
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Server shutting down... saving history.");
            roomLock.readLock().lock();
            try {
                for (ChatRoom room : chatRooms.values()) {
                    room.saveMessagesToFile();
                }
            } finally {
                roomLock.readLock().unlock();
            }
        }));
        
        // Start secure SSL server socket to accept clients
        try (SSLServerSocket serverSocket = (SSLServerSocket) SSLServerSocketFactory.getDefault().createServerSocket(PORT)) {
            System.out.println("Chat server started on port " + PORT);
            // Accept client connections continuously
            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread.startVirtualThread(() -> {
                    try {
                        new ClientHandler(clientSocket, userManager).handle();
                    } catch (IOException e) {
                        System.err.println("Error handling client: " + e.getMessage());
                    }
                });
            }
    
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
    
    // Gets an existing room or creates a new one (with AI support if prefixed "AI:")
    public static ChatRoom getOrCreateRoom(String roomName) {
        roomLock.writeLock().lock();
        try {
            return chatRooms.computeIfAbsent(roomName, name -> {
                ChatRoom room;
                if (name.startsWith("AI:")) {
                    String prompt = "You are a helpful bot assisting with this conversation.";
                    room = new AiChatRoom(name, prompt);
                } else {
                    room = new ChatRoom(name);
                }
                room.loadMessagesFromFile(); // Load saved messages from file
                return room;
            });
            
        } finally {
            roomLock.writeLock().unlock();
        }
    }
    
    // Returns the list of all available room names
    public static List<String> getRoomNames() {
        roomLock.readLock().lock();
        try {
            return new ArrayList<>(chatRooms.keySet());
        } finally {
            roomLock.readLock().unlock();
        }
    }

    // Creates a new session and returns the generated token
    public static String createSession(String username, ChatRoom room, PrintWriter writer) {
        String token = UUID.randomUUID().toString(); // Generate unique token
        sessionLock.writeLock().lock();
        try {
            long oneHour = 60 * 60 * 1000;
            tokenSessions.put(token, new Session(username, room, writer, oneHour));
            saveSessionsToFile("sessions.txt"); // Save to disk after creating
            return token;
        } finally {
            sessionLock.writeLock().unlock();
        }
    }
    
    // Retrieves a valid (non-expired) session using the token
    public static Session getSession(String token) {
        sessionLock.readLock().lock();
        try {
            Session session = tokenSessions.get(token);
            if (session != null && !session.isExpired()) {
                return session;
            }
            return null;
        } finally {
            sessionLock.readLock().unlock();
        }
    }

    // Saves all sessions to a file for persistence
    public static void saveSessionsToFile(String path) {
        sessionLock.readLock().lock();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            for (Map.Entry<String, Session> entry : tokenSessions.entrySet()) {
                String token = entry.getKey();
                Session s = entry.getValue();
                writer.write(token + ":" + s.getUsername() + ":" +
                            (s.getRoom() != null ? s.getRoom().getName() : "") + ":" +
                            (s.getExpiryTimeMillis() - System.currentTimeMillis()) + ":" +
                            System.currentTimeMillis());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error saving sessions: " + e.getMessage());
        } finally {
            sessionLock.readLock().unlock();
        }
    }

    // Loads valid sessions from file and discards expired ones
    public static void loadSessionsFromFile(String path) {
        sessionLock.writeLock().lock();
        Map<String, Session> validSessions = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length != 2) continue;
    
                String token = parts[0];
                String sessionLine = parts[1];
                PrintWriter dummyWriter = new PrintWriter(OutputStream.nullOutputStream(), true);
                Session s = Session.fromFileString(token, sessionLine, dummyWriter);
    
                if (s != null && !s.isExpired()) {
                    validSessions.put(token, s);
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading sessions: " + e.getMessage());
        }
    
        // Replace current sessions with the valid ones
        tokenSessions.clear();
        tokenSessions.putAll(validSessions);
    
        // Rewrite file to remove expired sessions
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            for (Map.Entry<String, Session> entry : validSessions.entrySet()) {
                String token = entry.getKey();
                Session s = entry.getValue();
                writer.write(token + ":" + s.getUsername() + ":" +
                             (s.getRoom() != null ? s.getRoom().getName() : "") + ":" +
                             (s.getExpiryTimeMillis() - System.currentTimeMillis()) + ":" +
                             System.currentTimeMillis());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error updating sessions.txt: " + e.getMessage());
        } finally {
            sessionLock.writeLock().unlock();
        }
    }
    
    



}
