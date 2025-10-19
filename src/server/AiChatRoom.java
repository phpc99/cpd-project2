package server;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import java.lang.ProcessBuilder;
import java.lang.StringBuilder;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// AI-enhanced chat room that automatically generates bot responses using a language model (via Ollama)
public class AiChatRoom extends ChatRoom {
    private final String prompt; // Prompt used to guide the AI model's behavior

    // Constructor: initializes the room with a name and a prompt
    public AiChatRoom(String name, String prompt) {
        super(name);
        this.prompt = prompt; 
    }

    // Override broadcast to also generate a bot response if the message is from a user
    @Override
    public void broadcast(String message) {
        super.broadcast(message); // Broadcast to all participants as usual

        // Only generate a response if the message is not from the bot itself
        if (!message.startsWith("Bot:")) {
            generateBotResponse(); // Trigger AI response
        }
    }

    // Method to generate a bot response using the external "ollama" command
    private void generateBotResponse() {
        String context;
        List<String> messages = this.getMessages(); // Get full chat history

        // Build context with initial prompt and chat conversation
        StringBuilder fullContext = new StringBuilder(prompt + "\n\nConversation so far:\n");
        for (String msg : messages) {
            fullContext.append(msg).append("\n");
        }

        try {
            // Prepare to launch the AI model using the command: `ollama run llama2`
            ProcessBuilder pb = new ProcessBuilder("ollama", "run", "llama2");
            Process process = pb.start();

            // Write full context to AI process input
            PrintWriter writer = new PrintWriter(process.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            writer.println(fullContext.toString()); // Send conversation context to model
            writer.close(); // Close input stream to signal end of input

            // Read AI-generated response from the model's output
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append(" ");
            }

            // Broadcast AI response to all room participants
            super.broadcast("Bot: " + response.toString().trim());

        } catch (Exception e) {
            // Handle errors gracefully and notify users
            super.broadcast("Bot: [Error generating response]");
            System.err.println("AI Error: " + e.getMessage());
        }
    }
}
