# Features

- Implements a concurrent TCP-based architecture where multiple clients can connect and communicate simultaneously;
- All client–server interactions are encrypted using generated SSL certificates, ensuring message privacy and authentication;
- Supports user registration, login, and session management with credential verification for secure access;
- Users can create, join, and leave chat rooms, enabling organized group conversations;
- Includes special chat rooms connected to Ollama, allowing users to interact with a local LLM;
- Uses Java 21 virtual threads for lightweight, highly scalable concurrency;
- Clients interact through commands.

# Screenshots

### User _pedro_ registration
![user1register](https://github.com/phpc99/cpd-project2/blob/main/user1register.png)

### User login and room creation
![userloginandcreation](https://github.com/phpc99/cpd-project2/blob/main/loginAndRoomCreation.png)

### User _mariana_ enters the same room
![user2](https://github.com/phpc99/cpd-project2/blob/main/user2entersRoom.png)

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

# Authors
- Lara Cunha (up202108876)
- Pedro Camargo (up202102365)
