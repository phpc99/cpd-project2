package server;

import java.io.*;
import java.net.Socket;

// This class handles communication with a single connected client.
public class ClientHandler {
    private final Socket socket;
    private final UserManager userManager;

    // Constructor receives the socket and a reference to the user manager
    public ClientHandler(Socket socket, UserManager userManager) {
        this.socket = socket;
        this.userManager = userManager;
    }

    // Validates if the room name starts with a letter (A-Z or a-z)
    private boolean isValidRoomName(String name) {
        return name != null && name.matches("^[a-zA-Z].*");
    }

    // Main method to handle client interaction
    public void handle() throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("Welcome to ChatApp!");

            // --- Support for reconnection using saved token ---
            String mode = in.readLine();
            if (mode != null && mode.startsWith("RECONNECT")) {
                String token = mode.split(" ")[1];
                Session session = ServerMain.getSession(token);

                // Update session with the new output stream
                if (session != null) {
                    session.setWriter(out); // substituir writer antigo
                    ServerMain.saveSessionsToFile("sessions.txt");
                    
                    ChatRoom room = session.getRoom();
                    String username = session.getUsername();

                    out.println("RECONNECT_SUCCESS");
                    out.println("Welcome back, " + username + "!");

                    // If the session has a room, rejoin and start chat loop
                    if (room != null) {
                        room.rejoin(out, username);  // apenas se estiver numa sala
                
                        for (String msg : room.getMessages()) {
                            out.println(msg);
                        }
                
                        String msg;
                        while ((msg = in.readLine()) != null) {
                            if (msg.equalsIgnoreCase("/exit")) {
                                room.leave(out, username);
                                break;
                            } else {
                                room.broadcast(username + ": " + msg);
                            }
                        }
                    } else {
                        out.println("[INFO] Reconnected, but you are not in a room.");
                        
                    }
                    return; // End reconnection handling
                } else {
                    out.println("RECONNECT_FAILED");
                    return;
                }
            }

            // --- User registration ---
            if ("REGISTER".equalsIgnoreCase(mode)) {
                String newUser = in.readLine();
                String newPass = in.readLine();
                boolean success = userManager.register(newUser, newPass);
                out.println(success ? "REG_SUCCESS" : "Username already exists");
                return;
            }

            // If not LOGIN command, send error
            if (!"LOGIN".equalsIgnoreCase(mode)) {
                out.println("Unknown command");
                return;
            }

            // --- User login ---
            String username = in.readLine();
            String password = in.readLine();
            ChatRoom room = null;
            String token = null;

            if (userManager.authenticate(username, password)) {
                out.println("AUTH_SUCCESS");

                // Create a new session and send token to client
                token = ServerMain.createSession(username, null, out);
                out.println("TOKEN " + token);
                System.out.println("DEBUG: Token generated for " + username + " -> " + token);

                while (true) {
                    // List available rooms to the user
                    out.println("Rooms available:");
                    for (String name : ServerMain.getRoomNames()) {
                        out.println("- " + name);
                    }

                    // Ask user to choose a valid room
                    String roomName;
                    while (true) {
                        out.println("Enter room name (must start with a letter):");
                        roomName = in.readLine();
                        if (!isValidRoomName(roomName)) {
                            out.println("Invalid room name!");
                        } else {
                            break;
                        }
                    }
                    
                    // Join or create the requested room
                    room = ServerMain.getOrCreateRoom(roomName);
                    room.join(out, username);
                    out.println("Room: " + room.getName());

                    // Update the session with the joined room
                    Session session = ServerMain.getSession(token);
                    if (session != null) {
                        session.setRoom(room);
                        // You may persist sessions here if needed
                        // ServerMain.saveSessionsToFile("sessions.txt");
                    }

                    // Inform user if in AI-powered chat room
                    if (room instanceof AiChatRoom) {
                        out.println("[You are in an AI-powered room. The bot will respond to your messages.]");
                    }

                    // Send chat history
                    for (String msg : room.getMessages()) {
                        out.println(msg);
                    }

                    // Chat loop to read user messages and broadcast
                    String line;
                    boolean leavingRoom = false;

                    while ((line = in.readLine()) != null) {
                        if (line.equalsIgnoreCase("/exit")) {
                            room.leave(out, username);
                            leavingRoom = true;
                            return; // Exit the application
                        } else if (line.equalsIgnoreCase("/leave")) {
                            room.leave(out, username);
                            leavingRoom = true;
                            break; // Go back to room selection
                        } else if (line.equalsIgnoreCase("/rooms")) {
                            out.println("Rooms available:");
                            for (String name : ServerMain.getRoomNames()) {
                                out.println("- " + name);
                            }
                        } else {
                            room.broadcast(username + ": " + line);
                        }
                    }
                    // If user didn't type /leave, break outer loop
                    if (!leavingRoom) break;
                }

            } else {
                out.println("AUTH_FAILED"); // Login failed
            }

        } finally {
            socket.close(); // Always close socket at the end
        }
    }
}
