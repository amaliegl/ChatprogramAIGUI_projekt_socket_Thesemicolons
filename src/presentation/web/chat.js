/**
 * ChatClient - Handles WebSocket communication with the chat server
 * and manages the GUI state for the chat application.
 */
class ChatClient {
    constructor() {
        this.ws = null;
        this.username = null;
        this.currentRoom = null;
        this.joinedRooms = new Set();
        this.participants = new Set();
        
        // DOM elements
        this.loginScreen = document.getElementById('login-screen');
        this.chatScreen = document.getElementById('chat-screen');
        this.usernameInput = document.getElementById('username-input');
        this.loginBtn = document.getElementById('login-btn');
        this.loginError = document.getElementById('login-error');
        this.currentUsername = document.getElementById('current-username');
        this.logoutBtn = document.getElementById('logout-btn');
        this.chatroomsList = document.getElementById('chatrooms-list');
        this.participantsList = document.getElementById('participants-list');
        this.currentRoomName = document.getElementById('current-room-name');
        this.messagesContainer = document.getElementById('messages-container');
        this.messageInput = document.getElementById('message-input');
        this.sendBtn = document.getElementById('send-btn');
        
        // Modal elements
        this.joinModal = document.getElementById('join-modal');
        this.createModal = document.getElementById('create-modal');
        this.joinRoomInput = document.getElementById('join-room-input');
        this.createRoomInput = document.getElementById('create-room-input');
        
        this.initializeEventListeners();
    }

    /**
     * Initialize all event listeners for the GUI
     */
    initializeEventListeners() {
        // Login
        this.loginBtn.addEventListener('click', () => this.handleLogin());
        this.usernameInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') this.handleLogin();
        });

        // Logout
        this.logoutBtn.addEventListener('click', () => this.handleLogout());

        // Send message
        this.sendBtn.addEventListener('click', () => this.handleSendMessage());
        this.messageInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') this.handleSendMessage();
        });

        // Join room
        document.getElementById('join-room-btn').addEventListener('click', () => {
            this.joinModal.classList.remove('hidden');
            this.joinRoomInput.focus();
        });

        document.getElementById('join-confirm-btn').addEventListener('click', () => this.handleJoinRoom());
        document.getElementById('join-cancel-btn').addEventListener('click', () => {
            this.joinModal.classList.add('hidden');
            this.joinRoomInput.value = '';
        });

        // Create room
        document.getElementById('create-room-btn').addEventListener('click', () => {
            this.createModal.classList.remove('hidden');
            this.createRoomInput.focus();
        });

        document.getElementById('create-confirm-btn').addEventListener('click', () => this.handleCreateRoom());
        document.getElementById('create-cancel-btn').addEventListener('click', () => {
            this.createModal.classList.add('hidden');
            this.createRoomInput.value = '';
        });
    }

    /**
     * Handle login - connect to server and send login command
     */
    handleLogin() {
        const username = this.usernameInput.value.trim();
        if (!username) {
            this.showError('Brugernavn kan ikke være tomt');
            return;
        }

        this.username = username;
        this.connectToServer();
    }

    /**
     * Establish WebSocket connection to the server
     */
    connectToServer() {
        // Connect to WebSocket server (will be implemented in Java backend)
        this.ws = new WebSocket('ws://localhost:8081/chat');
        
        this.ws.onopen = () => {
            console.log('WebSocket connected');
            this.sendLogin();
        };

        this.ws.onmessage = (event) => this.handleServerMessage(event.data);
        
        this.ws.onerror = (error) => {
            console.error('WebSocket error:', error);
            this.showError('Kunne ikke forbinde til serveren');
        };

        this.ws.onclose = () => {
            console.log('WebSocket disconnected');
            this.handleDisconnect();
        };
    }

    /**
     * Send login command to server
     */
    sendLogin() {
        const message = `LOGIN|${this.username}|`;
        this.ws.send(message);
    }

    /**
     * Handle incoming messages from server
     * Server message format: TIMESTAMP|TYPE|SENDER|TARGET|PAYLOAD
     */
    handleServerMessage(data) {
        const parts = data.split('|');
        if (parts.length < 5) return;

        const [timestamp, type, sender, target, payload] = parts;

        switch (type.toUpperCase()) {
            case 'LOGIN':
                this.handleLoginSuccess(payload);
                break;
            case 'ERROR':
                this.handleError(payload);
                break;
            case 'TEXT':
                this.handleChatMessage(sender, target, payload, timestamp);
                break;
            case 'PRIVAT':
                this.handlePrivateMessage(sender, target, payload, timestamp);
                break;
            case 'JOIN_CHATRUM':
                this.handleJoinSuccess(payload);
                break;
            case 'LEAVE_CHATRUM':
                this.handleLeaveSuccess(payload);
                break;
            case 'NYT_CHATRUM':
                this.handleNewRoom(payload);
                break;
            case 'INFO':
                this.handleSystemMessage(payload);
                break;
            case 'QUIT':
                this.handleLogout();
                break;
        }
    }

    /**
     * Handle successful login
     */
    handleLoginSuccess(payload) {
        this.loginScreen.classList.add('hidden');
        this.chatScreen.classList.remove('hidden');
        this.currentUsername.textContent = this.username;
        this.joinedRooms.add('alle');
        this.updateChatroomsList();
        this.selectRoom('alle');
    }

    /**
     * Handle error messages from server
     */
    handleError(payload) {
        if (!this.username) {
            this.showError(payload);
        } else {
            this.addSystemMessage(payload);
        }
    }

    /**
     * Handle chat message
     */
    handleChatMessage(sender, target, payload, timestamp) {
        if (target.toLowerCase() === this.currentRoom?.toLowerCase()) {
            this.addMessage(sender, payload, timestamp, sender === this.username);
        }
    }

    /**
     * Handle private message
     */
    handlePrivateMessage(sender, target, payload, timestamp) {
        // For now, treat private messages like regular messages
        // Could be enhanced to show in a separate private chat area
        this.addMessage(sender, `[Privat] ${payload}`, timestamp, false);
    }

    /**
     * Handle successful room join
     */
    handleJoinSuccess(payload) {
        const roomName = this.extractRoomName(payload);
        if (roomName) {
            this.joinedRooms.add(roomName.toLowerCase());
            this.updateChatroomsList();
            this.selectRoom(roomName);
        }
        this.addSystemMessage(payload);
    }

    /**
     * Handle successful room leave
     */
    handleLeaveSuccess(payload) {
        const roomName = this.extractRoomName(payload);
        if (roomName) {
            this.joinedRooms.delete(roomName.toLowerCase());
            this.updateChatroomsList();
            
            if (this.currentRoom?.toLowerCase() === roomName.toLowerCase()) {
                if (this.joinedRooms.size > 0) {
                    this.selectRoom(Array.from(this.joinedRooms)[0]);
                } else {
                    this.currentRoom = null;
                    this.currentRoomName.textContent = 'Vælg et chatrum';
                    this.messagesContainer.innerHTML = '';
                    this.messageInput.disabled = true;
                    this.sendBtn.disabled = true;
                }
            }
        }
        this.addSystemMessage(payload);
    }

    /**
     * Handle new room creation
     */
    handleNewRoom(payload) {
        this.addSystemMessage(payload);
    }

    /**
     * Handle system messages (join/leave notifications)
     */
    handleSystemMessage(payload) {
        this.addSystemMessage(payload);
    }

    /**
     * Extract room name from server message payload
     */
    extractRoomName(payload) {
        const match = payload.match(/'([^']+)'/);
        return match ? match[1] : null;
    }

    /**
     * Add a message to the chat area
     */
    addMessage(sender, content, timestamp, isOwn) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${isOwn ? 'own' : 'other'}`;
        
        const senderDiv = document.createElement('div');
        senderDiv.className = 'message-sender';
        senderDiv.textContent = sender;
        
        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        contentDiv.textContent = content;
        
        const timeDiv = document.createElement('div');
        timeDiv.className = 'message-time';
        timeDiv.textContent = timestamp || '';
        
        messageDiv.appendChild(senderDiv);
        messageDiv.appendChild(contentDiv);
        messageDiv.appendChild(timeDiv);
        
        this.messagesContainer.appendChild(messageDiv);
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
    }

    /**
     * Add a system message to the chat area
     */
    addSystemMessage(content) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message system';
        messageDiv.textContent = content;
        
        this.messagesContainer.appendChild(messageDiv);
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
    }

    /**
     * Update the chatrooms list in the sidebar
     */
    updateChatroomsList() {
        this.chatroomsList.innerHTML = '';
        
        this.joinedRooms.forEach(room => {
            const roomDiv = document.createElement('div');
            roomDiv.className = `chatroom-item ${this.currentRoom?.toLowerCase() === room.toLowerCase() ? 'active' : ''}`;
            roomDiv.textContent = room;
            roomDiv.addEventListener('click', () => this.selectRoom(room));
            this.chatroomsList.appendChild(roomDiv);
        });
    }

    /**
     * Select a chatroom to view
     */
    selectRoom(roomName) {
        this.currentRoom = roomName;
        this.currentRoomName.textContent = roomName;
        this.messageInput.disabled = false;
        this.sendBtn.disabled = false;
        this.messageInput.focus();
        this.updateChatroomsList();
        this.messagesContainer.innerHTML = '';
        
        // Request participants for this room (would need server support)
        this.participants.clear();
        this.updateParticipantsList();
    }

    /**
     * Update the participants list in the sidebar
     */
    updateParticipantsList() {
        this.participantsList.innerHTML = '';
        
        // For now, show placeholder - would need server support for real participant list
        const placeholder = document.createElement('div');
        placeholder.className = 'participant-item';
        placeholder.textContent = 'Deltagere ikke tilgængelige';
        this.participantsList.appendChild(placeholder);
    }

    /**
     * Handle sending a message
     */
    handleSendMessage() {
        const content = this.messageInput.value.trim();
        if (!content || !this.currentRoom) return;

        const message = `TEXT|${this.currentRoom}|${content}`;
        this.ws.send(message);
        this.messageInput.value = '';
    }

    /**
     * Handle joining a room
     */
    handleJoinRoom() {
        const roomName = this.joinRoomInput.value.trim();
        if (!roomName) return;

        const message = `JOIN|${roomName}|`;
        this.ws.send(message);
        
        this.joinModal.classList.add('hidden');
        this.joinRoomInput.value = '';
    }

    /**
     * Handle creating a room
     */
    handleCreateRoom() {
        const roomName = this.createRoomInput.value.trim();
        if (!roomName) return;

        const message = `CREATE_ROOM|${roomName}|`;
        this.ws.send(message);
        
        this.createModal.classList.add('hidden');
        this.createRoomInput.value = '';
    }

    /**
     * Handle logout
     */
    handleLogout() {
        if (this.ws) {
            this.ws.send('QUIT||');
            this.ws.close();
        }
        this.resetState();
    }

    /**
     * Handle unexpected disconnect
     */
    handleDisconnect() {
        this.showError('Forbindelse til serveren mistet');
        this.resetState();
    }

    /**
     * Reset application state
     */
    resetState() {
        this.username = null;
        this.currentRoom = null;
        this.joinedRooms.clear();
        this.participants.clear();
        
        this.loginScreen.classList.remove('hidden');
        this.chatScreen.classList.add('hidden');
        this.usernameInput.value = '';
        this.loginError.textContent = '';
        this.messagesContainer.innerHTML = '';
        this.chatroomsList.innerHTML = '';
        this.participantsList.innerHTML = '';
    }

    /**
     * Show error message
     */
    showError(message) {
        this.loginError.textContent = message;
    }
}

// Initialize the chat client when the page loads
document.addEventListener('DOMContentLoaded', () => {
    new ChatClient();
});
