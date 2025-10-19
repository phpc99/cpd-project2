#### i) estabelecimento duma conexão -- é criado algum "thread"? Em caso afirmativo, qual o seu tipo ("virtual" vs "platform"), o que é que esse "thread" faz e quando termina (se é que termina)?

Onde o thread é criado:
```
Thread.startVirtualThread(() -> {
    try {
        new ClientHandler(clientSocket, userManager).handle();
    } catch (IOException e) {
        System.err.println("Error handling client: " + e.getMessage());
    }
});
```
1. É criado algum thread?
Sim, um thread é criado para cada cliente que se conecta.

2. Tipo do thread?
É um virtual thread (Java 21+), criado com `Thread.startVirtualThread(...)`.

3. O que é que esse thread faz?
- Executa o método `handle()` da classe `ClientHandler`:
    - Lê o modo (`REGISTER` ou `LOGIN`).
    - Realiza a autenticação.
    - Lista e entra em salas.
    - Lê comandos do utilizador (`/exit`, `/leave`, mensagens, etc.).
    - Envia e recebe mensagens para a sala.
    - Pode interagir com uma sala com bot (`AiChatRoom`).

4. Quando é que esse thread termina?
- O thread termina quando `handle()` termina, o que pode acontecer quando:
    - O utilizador envia o comando `/exit`.
    - A conexão com o socket é fechada (por erro ou desconexão).
    - O cliente fecha manualmente.

#### ii) receção dum pedido de "join". Que "thread" processa este pedido? Qual o seu tipo? A que estruturas de dados partilhadas acede esse "thread" no processamento deste pedido? Como garantem que não há "race conditions"? Que "thread" envia a resposta? Se diferente do que processa o pedido, qual o seu tipo?

O pedido de `join` ocorre no ficheiro `ClientHandler`:
```
room = ServerMain.getOrCreateRoom(roomName);
room.join(out, username);
```
1. Que thread processa o pedido de `join`?
O mesmo virtual thread criado em `ServerMain` via:
```
Thread.startVirtualThread(() -> { ... });
```
Esse thread executa todo o método `handle()` do `ClientHandler`, incluindo o `join()`.

2. Qual o tipo desse thread?
É um virtual thread.

3. A que estruturas de dados partilhadas acede esse thread?
Durante o `join`, o método `ChatRoom.join(...)` acede a:

| Estrutura                           | Tipo       | Função                         |
| ----------------------------------- | ---------- | ------------------------------ |
| `participants` (`Set<PrintWriter>`) | Partilhada | Adiciona o cliente à sala      |
| `messages` (`List<String>`)         | Partilhada | Regista a entrada no histórico |

Estas estruturas são acessadas por múltiplos threads (todos os clientes daquela sala).

4. Como garantem que não há race conditions?
A classe `ChatRoom` usa um `ReentrantReadWriteLock`, garantindo:
- Lock de escrita no `join()`:
```
lock.writeLock().lock();
try {
    participants.add(out);
    messages.add("[" + username + " enters the room]");
    broadcast("[" + username + " enters the room]");
} finally {
    lock.writeLock().unlock();
}
```
- No método `broadcast(...)`, também se usa `readLock()` para ler `participants`, e `writeLock()` para adicionar a `messages`.

Isto garante que:
- Nenhum outro thread modifica a sala enquanto alguém está a fazer `join`.
- Leitura e escrita nas coleções partilhadas são sincronizadas corretamente.

5. Que thread envia a resposta?
O mesmo virtual thread que processou o `join` envia as mensagens ao cliente.
- O `join()` invoca `broadcast(...)`, que envia a mensagem `"[X enters the room]"`.
- Depois, ainda dentro do mesmo thread, o histórico da sala é enviado:
```
for (String msg : room.getMessages()) {
    out.println(msg);
}
```

#### iii) recepção duma mensagem. As mesmas questões que no caso de "join", mas em vez do envio da resposta, considerem o reenvio da mensagem recebida. Além disso, como garantem que não há "race conditions" no acesso aos diferentes "output streams" das conexões do servidor com os diferentes utilizadores?

A receção de uma mensagem ocorre no `ClientHandler`:
```
while ((line = in.readLine()) != null) {
    ...
    room.broadcast(username + ": " + line);
}
```

1. Que thread processa a mensagem?
O virtual thread do cliente (o mesmo que foi criado no `ServerMain`).
Esta thread lê `line = in.readLine()` e chama `room.broadcast(...)`.

2. Qual o tipo desse thread?
É um virtual thread, criado com `Thread.startVirtualThread(...)`.

3. A que estruturas de dados partilhadas acede?
O método ChatRoom.broadcast(...) acessa:

| Estrutura                           | Tipo       | Função                                     |
| ----------------------------------- | ---------- | ------------------------------------------ |
| `messages` (`List<String>`)         | Partilhada | Adiciona a nova mensagem                   |
| `participants` (`Set<PrintWriter>`) | Partilhada | Itera sobre os `PrintWriter` para reenviar |

4. Como garantem que não há race conditions?
A classe `ChatRoom` usa:
```
lock.readLock().lock();
try {
    messages.add(message);      // OK porque só este thread escreve
    for (PrintWriter out : participants) {
        out.println(message);   // envia para todos
    }
} finally {
    lock.readLock().unlock();
}
```
Proteções:
- `messages.add(...)` é protegido.
- Iteração sobre `participants` é protegida por `readLock` (a escrita em `participants`, como no `join` ou `leave`, requer `writeLock`).

5. Como garantem que não há race condition nos `PrintWriter`?
Cada PrintWriter representa a conexão com um utilizador, mas há o risco de dois threads escreverem no mesmo `PrintWriter` simultaneamente. Como o nosso código evita isso? Cada `PrintWriter` só é usado por um único thread: o virtual thread correspondente ao cliente:
- Os clientes não partilham `PrintWriter`s entre si.
- O método `broadcast(...)` apenas escreve em `out.println()` de cada participante, e esse objeto não é usado por outros threads em paralelo.
Portanto: não há race conditions porque não há concorrência no uso de cada `PrintWriter`.

#### iv) processamento de envio de mensagem ao LLM. As mesmas questões que para a receção duma mensagem. E ainda, como é que o servidor processa mensagens "simultâneas" destinadas ao LLM, i.e. quando recebe uma mensagem para o LLM e ainda não recebeu a resposta à mensagem anterior.

Onde ocorre o envio da mensagem para o LLM?
```
public void broadcast(String message) {
    super.broadcast(message);

    if (!message.startsWith("Bot:")) {
        generateBotResponse();
    }
}
```

1. Que thread processa a mensagem que vai para o LLM?
O virtual thread do cliente que escreveu a mensagem.
- Esse thread chama `room.broadcast(...)`, que chama `generateBotResponse()` (em `AIChatRoom`).

2. Tipo desse thread?
É um virtual thread, tal como os outros clientes.

3. Que estruturas de dados partilhadas são usadas?
Dentro de `generateBotResponse()`:
- Acessa `getMessages()`:
```
List<String> messages = this.getMessages();
```
- Depois, interage com: 
    - `ProcessBuilder`: cria subprocesso externo (OLLAMA).
    - `PrintWriter`/`BufferedReader` para comunicar com o processo.

4. Como evitam race conditions?
A leitura do histórico (`getMessages()`) é protegida por `readLock`.