package server;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.*;

/**
 * UserManager is responsible for handling user authentication and registration.
 * It uses a file to persist user data and a map in memory for fast access.
 * Thread-safe via a ReentrantReadWriteLock.
 */
public class UserManager {
    private final Map<String, String> users = new HashMap<>(); // Stores usernames and passwords
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(); // Ensures thread-safe access
    private final String userFilePath; // Path to the file storing user credentials

    /**
     * Constructs a UserManager and loads existing users from file.
     * @param userFilePath path to the user credentials file
     */
    public UserManager(String userFilePath) {
        this.userFilePath = userFilePath;
        loadUsers(userFilePath);
    }

    /**
     * Loads users from the specified file into the internal map.
     * Assumes each line has the format "username:password".
     */
    private void loadUsers(String path) {
        lock.writeLock().lock(); // Exclusive access while loading
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.strip().split(":"); // Split username and password
                if (parts.length == 2) {
                    users.put(parts[0], parts[1]); // Add to map
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading users: " + e.getMessage());
        } finally {
            lock.writeLock().unlock(); // Release exclusive lock
        }
    }

    /**
     * Authenticates a user based on username and password.
     * @param username the username to authenticate
     * @param password the password to check
     * @return true if authentication is successful, false otherwise
     */
    public boolean authenticate(String username, String password) {
        lock.readLock().lock(); // Shared lock for reading
        try {
            return users.containsKey(username) && users.get(username).equals(password);
        } finally {
            lock.readLock().unlock(); // Release shared lock
        }
    }

    /**
     * Registers a new user if the username is not already taken.
     * Adds the user to the file and internal map.
     * @param username new username to register
     * @param password associated password
     * @return true if registration succeeds, false if user already exists or file write fails
     */
    public boolean register(String username, String password) {
        lock.writeLock().lock(); // Exclusive access for modifying the map
        try {
            if (users.containsKey(username)) {
                return false; // User already exists
            }
            users.put(username, password); // Add to map
            try (FileWriter fw = new FileWriter(userFilePath, true); // Append mode
                 BufferedWriter bw = new BufferedWriter(fw)) {
                bw.write(username + ":" + password); // Write to file
                bw.newLine();
            } catch (IOException e) {
                System.err.println("Error saving user: " + e.getMessage());
                return false;
            }
            return true;
        } finally {
            lock.writeLock().unlock(); // Release lock
        }
    }
}
