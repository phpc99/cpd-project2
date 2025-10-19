# How to run the project

### Step 1:
- Open the first terminal and run the command `javac server/*.java client/*.java`;
### Step 2:
- In the first terminal and run the command `java server.ServerMain`;
### Step 3:
- Open a second terminal and run the command `ollama serve`;
### Step 4:
- Open a third terminal and run the command `java client.ChatClient`;
- After running the third command, you will be able to start the project, and a welcome message will be displayed, followed by login and registration options.
### Step 5 (optional):
- You can open a new terminal and run the same command from *Step 3* to login (or register) as a new user. If you choose to enter the same room as the user from *Step 3*, you will be able to chat!
### Project Commands:
`/rooms` to list all rooms available;
`/leave` to leave a room and return to room selection;
`/exit` to exit the application.