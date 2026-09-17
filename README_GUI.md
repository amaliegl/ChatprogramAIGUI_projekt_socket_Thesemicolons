# PinkChat - GUI Implementation

## Oversigt over ændringer

### Nye filer oprettet

**Frontend (HTML/CSS/JavaScript):**
- `src/presentation/web/index.html` - Hoved HTML fil med pastel tema GUI
- `src/presentation/web/style.css` - CSS styling med pastel farver og afrundede hjørner
- `src/presentation/web/chat.js` - JavaScript client der håndterer WebSocket kommunikation

**Backend Server Komponenter:**
- `src/HttpServer.java` - HTTP server til at servere statiske GUI filer
- `src/WebSocketServer.java` - WebSocket server der fungerer som TCP client til ekstern server
- `src/MainServer.java` - Main server der starter HTTP og WebSocket servere

**Build Configuration:**
- `pom.xml` - Maven konfiguration med Java-WebSocket dependency

**Mapper oprettet:**
- `src/presentation/controller/` - Controller lag (reserveret til fremtidig brug)
- `src/presentation/web/` - Web frontend filer
- `src/infrastructure/webserver/` - Web server infrastructure (tom, filer flyttet til default package)

### Ændrede filer

- `src/TcpServer.java` - Refaktoreret til at understøtte både standalone execution og programmatic startup (ikke brugt i MainServer da TCP kører eksternt)
  - Tilføjet constructor med dependencies
  - Tilføjet `start()` metode
  - Tilføjet `stop()` metode
  - `main()` metode opdateret til at bruge nye constructor

## Arkitektur

### Clean Architecture

Projektet følger Clean Architecture principper:

```
src/
├── presentation/           # Presentation layer
│   ├── controller/        # GUI controllers (reserveret)
│   └── web/              # Web frontend (HTML/CSS/JS)
├── infrastructure/        # Infrastructure layer
│   └── webserver/        # Web server infrastructure (tom)
└── (default package)      # Domain & Application logic
    ├── TcpServer.java    # TCP server (kører eksternt)
    ├── TcpClient.java    # TCP client
    ├── ClientHandler.java
    ├── ClientRegistry.java
    ├── ChatRoomManager.java
    ├── Chatroom.java
    ├── Protocol.java
    ├── Message.java
    ├── ServerMessage.java
    ├── HttpServer.java   # HTTP server (flyttet fra infrastructure)
    ├── WebSocketServer.java # WebSocket server (flyttet fra infrastructure)
    └── MainServer.java   # Main server coordinator
```

**Note:** HttpServer og WebSocketServer er flyttet til default package for at undgå import problemer med eksisterende klasser som også er i default package.

### Server Arkitektur (Ekstern TCP Server)

**MainServer** kører lokalt og koordinerer to servere:

1. **HTTP Server (Port 8080)** - Serverer GUI filer (HTML/CSS/JS)
2. **WebSocket Server (Port 8081)** - Realtids kommunikation med GUI

**Ekstern TCP Server:**
- Kører på en anden maskine (konfigureret i MainServer)
- Port 5000 (eller anden port som konfigureret)
- Håndterer TcpClient forbindelser og chat logik

**Integration:**
- WebSocketServer fungerer som en TCP client til den eksterne server
- Hver WebSocket forbindelse opretter sin egen TCP forbindelse til den eksterne server
- Beskeder videresendes mellem GUI (WebSocket) og ekstern TCP server

### Threading og Trådsikkerhed

**Eksisterende Threading Model (på ekstern server):**
- TcpServer bruger ExecutorService med thread pool
- ClientHandler kører i separate threads
- ConcurrentHashMap bruges i ClientRegistry, ChatRoomManager, Chatroom
- PrintWriter writes er synchronized for at undgå interleaved output

**GUI Threading (lokalt):**
- WebSocketServer bruger Java-WebSocket library som håndterer sin egen threading
- Hver WebSocket forbindelse har sin egen listener thread til at læse fra TCP server
- WebSocket forbindelser spores i ConcurrentHashMap for trådsikkerhed
- GUI client (JavaScript) kører i browserens event loop
- Ingen GUI opdateringer sker fra Java threads - alt kommunikation sker via WebSocket

**Integration mellem Ekstern TCP og GUI:**
- WebSocketServer opretter en TCP forbindelse til den eksterne server for hver GUI client
- Beskeder fra GUI videresendes til ekstern TCP server via TCP
- Beskeder fra ekstern TCP server videresendes til GUI via WebSocket
- Trådsikkerhed sikres via ConcurrentHashMap for WebSocket forbindelser
- Listener threads er daemon threads og stoppes automatisk ved disconnect

### Kommunikationsprotokol

**Eksisterende protokol (bevaret):**
- Klient → Server: `TYPE|TARGET|PAYLOAD`
- Server → Klient: `TIMESTAMP|TYPE|SENDER|TARGET|PAYLOAD`

**Kommandoer understøttet:**
- `LOGIN` - Login med brugernavn
- `CREATE_ROOM` - Opret nyt chatrum
- `JOIN` - Join et chatrum
- `LEAVE` - Forlad et chatrum
- `TEXT` - Send besked til chatrum
- `PRIVAT` - Send privat besked
- `QUIT` - Log ud

**GUI WebSocket Integration:**
- GUI client bruger samme protokol som TCP client
- WebSocketServer parser beskeder med `Protocol.parse()`
- Server beskeder formateres med `Protocol.formatServerMessage()`
- Ingen ændringer i protokollen nødvendig

## GUI Design

### Farver (Pastel Tema)
- `--pastel-pink: #FFB6C1`
- `--pastel-light-pink: #FFC0CB`
- `--pastel-lavender: #E6E6FA`
- `--pastel-light-purple: #D8BFD8`
- `--pastel-cream: #FFFDD0`
- `--pastel-blue: #ADD8E6`

### Layout
```
┌──────────────────────────────────────────────────────┐
│                    PinkChat                          │
├───────────────┬────────────────────────┬─────────────┤
│               │                        │             │
│   Chatrum     │      Chatområde        │  Deltagere  │
│               │                        │             │
│  # General    │  Anna: Hej!            │  Anna       │
│  # Gaming     │  Sofie: Hej Anna       │  Sofie      │
│  # Java       │                        │  Emma       │
│               │                        │             │
│  + Join room  │                        │             │
│               │├────────────────────────┤             │
│               │ Skriv en besked...  [Send]          │
│               │                        │             │
├───────────────┴────────────────────────┴─────────────┤
│                       Log ud                         │
└──────────────────────────────────────────────────────┘
```

### Features
- Login screen med brugernavn input
- Chatrum sidebar med join/create knapper
- Chat område med beskeder
- Deltagerliste (placeholder - kræver server support)
- Modal dialogs for join/create room
- Responsive design til mobil
- Systembeskeder (join/leave notifications)
- Fejlhåndtering i GUI

## Sådan kører du projektet

### Forudsætninger
- Java 11 eller nyere
- Maven (for dependency management)
- Ekstern TCP server kører på en anden maskine

### Konfiguration af Ekstern Server

Før du starter MainServer, skal du konfigurere den eksterne TCP server i `src/MainServer.java`:

```java
private static final String EXTERNAL_SERVER_HOST = "localhost"; // Ændr til ekstern server IP
private static final int EXTERNAL_SERVER_PORT = 5000; // Ændr til ekstern server port
```

### Installation af Dependencies

Da Maven ikke er installeret på systemet, skal du enten:

1. **Installer Maven:**
   - Download fra https://maven.apache.org/download.cgi
   - Følg installation instruktioner

2. **Eller download Java-WebSocket JAR manuelt:**
   - Download fra https://github.com/TooTallNate/Java-WebSocket/releases
   - Tilføj JAR til classpath når du kompilerer

### Kompilering og Kørsel

**Med Maven:**
```bash
mvn compile
mvn exec:java -Dexec.mainClass="MainServer"
```

**Uden Maven (manuelt):**
```bash
# Download Java-WebSocket JAR og tilføj til classpath
javac -cp ".;Java-WebSocket-1.5.4.jar" *.java
java -cp ".;Java-WebSocket-1.5.4.jar" MainServer
```

### Adgang til GUI

Når MainServer kører:
- **GUI:** http://localhost:8080
- **WebSocket:** ws://localhost:8081/chat
- **Ekstern TCP Server:** Konfigureret i MainServer (f.eks. ekstern-server:5000)

### Test af GUI

1. Start MainServer
2. Åbn browser og gå til http://localhost:8080
3. Indtast brugernavn og klik "Forbind"
4. Du er automatisk joinet i "alle" chatrummet
5. Send beskeder i chatfeltet
6. Opret nye chatrum eller join eksisterende
7. Test med multiple browser tabs eller TcpClient samtidigt

## Begrænsninger og Fremtidige Forbedringer

### Nuværende Begrænsninger
- Deltagerliste viser placeholder - kræver server support for at vise rigtige deltagere
- GUI clients og TCP clients har ikke fuld integration i chatrum membership
- Ingen persistens af chatrum eller beskeder
- Ingen bruger authentication udover brugernavn

### Mulige Forbedringer
- Implementer rigtig deltagertiliste i WebSocketServer
- Fuld integration af GUI clients i Chatroom members
- Persistens med database
- Bruger authentication med passwords
- Private chat rooms
- Fil deling
- Emojis og rich text
- Mobile app version

## Fejlfinding

### Maven ikke installeret
Installer Maven eller download Java-WebSocket JAR manuelt.

### Port allerede i brug
Ændr port numre i MainServer:
- TCP_PORT
- HTTP_PORT
- WEBSOCKET_PORT

### WebSocket forbindelse fejler
Tjek at Java-WebSocket library er korrekt tilføjet til classpath.

### GUI viser ikke beskeder
Tjek browser console for JavaScript fejl.
Tjek at WebSocketServer kører på port 8081.

## Konklusion

GUI'en er implementeret som et presentation layer oven på den eksisterende backend:
- Eksisterende TCP klient fungerer stadig
- GUI client bruger samme protokol og backend komponenter
- Threading og trådsikkerhed er bevaret
- Clean Architecture principper er fulgt
- Designet er simpelt, intuitivt og med pastel tema
