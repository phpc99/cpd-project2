package client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import javax.net.ssl.*;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class ChatClient {
    // Server configuration constants
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        new ChatClient().start(); // Start the client
    }

    public void start() {
        // Set SSL truststore properties before any socket connection
        System.setProperty("javax.net.ssl.trustStore", "client_truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        // Try reconnecting using saved token (if exists)
        File tokenFile = new File("token.txt");
        if (tokenFile.exists()) {
            try (BufferedReader tokenReader = new BufferedReader(new FileReader(tokenFile))) {
                String savedToken = tokenReader.readLine();
                if (reconnectWithToken(savedToken)) return; // If reconnection successful, stop here
                System.out.println("Invalid token or expired session. Please log in again.");
            } catch (IOException ignored) { }
        }


        // Proceed to regular login or registration
        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(SERVER_HOST, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {
            
            // Print local socket port and server welcome message
            System.out.println("Client local port: " + socket.getLocalPort());
            // "Welcome to ChatApp!" message
            System.out.println(in.readLine());

            // Ask the user if they want to login or register
            System.out.print("Do you want to (1) Login or (2) Register? ");
            String choice = scanner.nextLine();

            if (choice.equals("2")) {
                // Registration process
                out.println("REGISTER");
                System.out.print("Choose username: ");
                String username = scanner.nextLine();
                out.println(username);

                System.out.print("Choose password: ");
                String password = scanner.nextLine();
                out.println(password);

                String regResponse = in.readLine();
                if ("REG_SUCCESS".equals(regResponse)) {
                    System.out.println("Registration successful! Please login now.");
                } else {
                    System.out.println("Registration failed: " + regResponse);
                }
                return; // Exit after registration attempt
            }

            // Login process
            out.println("LOGIN");
            System.out.print("Username: ");
            String username = scanner.nextLine();
            out.println(username);

            System.out.print("Password: ");
            String password = scanner.nextLine();
            out.println(password);

            String authResponse = in.readLine();
            if ("AUTH_SUCCESS".equals(authResponse)) {
                System.out.println("Login successful!");

                // Receive token from server and save it locally
                String tokenLine = in.readLine();
                System.out.println("DEBUG: Received from server -> " + tokenLine);
                if (tokenLine.startsWith("TOKEN")) {
                    String token = tokenLine.substring(6);
                    try {
                        FileWriter fw = new FileWriter("token.txt");
                        fw.write(token + "\n");
                        fw.close();
                        System.out.println("Token saved successfully.");
                    } catch (IOException e) {
                        System.err.println("Error saving token: " + e.getMessage());
                    }
                }

                // Start thread to receive messages from server
                Thread reader = new Thread(() -> {
                    try {
                        String serverMsg;
                        while (true) {
                            serverMsg = in.readLine();
                            if (serverMsg == null) {
                                throw new IOException("Server closed connection.");
                            }
                            System.out.println(serverMsg);
                        }
                    } catch (IOException e) {
                        System.err.println("Disconnected from server.");
                        reconnectLoop(readTokenFromFile()); // Try automatic reconnection
                    }
                });
                
                reader.start();

                // Main loop to send messages to server
                while (true) {
                    String input = scanner.nextLine();
                    out.println(input);
                    if (input.equalsIgnoreCase("/exit")) {
                        break;
                    }
                }

            } else {
                System.out.println("Login failed. Please check your credentials.");
            }

        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }

    // Attempt to reconnect using stored token
    private boolean reconnectWithToken(String token) {
        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(SERVER_HOST, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
    
            out.println("RECONNECT " + token);
            String response = in.readLine();
    
            if ("RECONNECT_SUCCESS".equals(response)) {
                System.out.println("Reconnected successfully.");
                System.out.println(in.readLine()); // Welcome back
                
                runClientLoop(in, out, new Scanner(System.in), token);
    
                return true;
            }
        } catch (IOException e) {
            System.err.println("Failed to reconnect with token.");
        }
        return false;
    }

    // Keep attempting reconnection until it succeeds
    private void reconnectLoop(String token) {
        int attempt = 1;
        while (true) {
            System.out.println(" Reconnection attempt #" + attempt);
            if (reconnectWithToken(token)) return;
    
            try {
                Thread.sleep(3000); // Wait before next attempt
            } catch (InterruptedException ignored) {}
            attempt++;
        }
    }
    
    // Read token from file if available
    private String readTokenFromFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader("token.txt"))) {
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    // Main client loop used after reconnect
    private void runClientLoop(BufferedReader in, PrintWriter out, Scanner scanner, String token) {
        Thread readerThread = new Thread(() -> {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {
                    System.out.println(msg);
                }
                throw new IOException("Server disconnected");
            } catch (IOException e) {
                System.err.println("Disconnected. Trying to reconnect...");
                reconnectLoop(token); // Reconnect automatically
            }
        });
    
        readerThread.start();
    
        while (true) {
            String input = scanner.nextLine();
            out.println(input);
            if (input.equalsIgnoreCase("/exit")) {
                System.exit(0);
            }
        }
    } 
    
}
