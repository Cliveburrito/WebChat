import { useState, useEffect, useCallback, useRef } from "react";
import Sidebar from "./components/Sidebar";
import ChatArea from "./components/ChatArea";
import RightSidebar from "./components/RightSidebar";
import Login from "./components/Login";
import Register from "./components/Register";
import { apiJson } from "./api";
import { useChatSocket } from "./hooks/useChatSocket";

function App() {
    // ==========================================
    // 1. STATE MANAGEMENT
    // ==========================================

    // Auth & Identity
    const [token, setToken] = useState(localStorage.getItem("token"));
    const [currentUser, setCurrentUser] = useState(localStorage.getItem("username") || "");
    const [currentUserId, setCurrentUserId] = useState(null);
    const [authView, setAuthView] = useState("login");

    // Chat Data & UI
    const [conversations, setConversations] = useState([]);
    const [allUsers, setAllUsers] = useState([]);
    const [activeChat, setActiveChat] = useState(null);
    const [messages, setMessages] = useState([]);

    // Pagination for Messages
    const [msgPage, setMsgPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);

    // Settings
    const [theme, setTheme] = useState(localStorage.getItem("theme") || "light");
    const [stealthMode, setStealthMode] = useState(false);

    // ==========================================
    // 2. REFS
    // ==========================================
    const activeChatRef = useRef(null);
    useEffect(() => {
        activeChatRef.current = activeChat;
    }, [activeChat]);

    // ==========================================
    // 3. API FETCHERS (Data Loading)
    // ==========================================

    const fetchChats = useCallback(async () => {
        if (!token) return;
        try {
            const data = await apiJson("/api/chats/my", { token });
            setConversations(Array.isArray(data) ? data : []);
        } catch (err) { console.error("Error fetching chats:", err); }
    }, [token]);

    const fetchUsers = useCallback(async () => {
        if (!token) return;
        try {
            const data = await apiJson("/api/users/getall", { token });
            const list = Array.isArray(data) ? data : [];
            setAllUsers(list);
            const me = list.find((u) => u.username === currentUser);
            if (me) setCurrentUserId(me.id);
        } catch (err) { console.error("Error fetching users:", err); }
    }, [token, currentUser]);

    const fetchMessages = useCallback(async (chatId, page = 0) => {
        if (!token || !chatId) return;
        try {
            const data = await apiJson(`/api/chats/${chatId}/messages?page=${page}&size=20`, { token });

            // --- FIX STARTS HERE ---
            // Check if 'data' is the array itself (Redis/List style) or a Page object (Old style)
            const rawMessages = Array.isArray(data) ? data : (data?.content || []);

            const fetched = rawMessages.slice().sort((a, b) => new Date(a.createdAt || a.sentAt) - new Date(b.createdAt || b.sentAt));
            // --- FIX ENDS HERE ---

            if (page === 0) {
                setMessages(fetched);
            } else {
                setMessages((prev) => [...fetched, ...prev]);
            }

            // Logic update: If we got fewer messages than requested (20), we reached the end.
            setHasMore(rawMessages.length === 20);
            setMsgPage(page);
        } catch (err) { console.error("Error fetching messages:", err); }
    }, [token]);

    const markChatRead = useCallback(async (chatId) => {
        if (!token || !chatId) return;
        setConversations(prev => prev.map(c =>
            (c.id === chatId || c.conversationId === chatId) ? { ...c, unreadCount: 0 } : c
        ));
        try { await apiJson(`/api/chats/${chatId}/read`, { token, method: "POST" }); } catch { /* emp   ty */ }
    }, [token]);

    // ==========================================
    // 4. CHAT ACTIONS & LOGIC
    // ==========================================

    const bumpConversation = useCallback((conversationId, { content, createdAt, isIncoming }) => {
        setConversations((prev) => {
            const idx = prev.findIndex((c) => (c.id === conversationId || c.conversationId === conversationId));
            if (idx === -1) return prev;

            const isOpen = (activeChatRef.current?.id === conversationId || activeChatRef.current?.conversationId === conversationId);
            const updated = {
                ...prev[idx],
                lastContent: content,
                lastMessage: content,
                lastMessageAt: createdAt || new Date().toISOString(),
                unreadCount: isIncoming && !isOpen ? (prev[idx].unreadCount + 1) : (isOpen ? 0 : prev[idx].unreadCount),
            };
            const copy = [...prev];
            copy.splice(idx, 1);
            return [updated, ...copy];
        });
    }, []);

    const openDirectChat = useCallback(async (otherUserId) => {
        if (!token || !currentUserId) return;
        try {
            const data = await apiJson("/api/chats/direct", {
                token,
                method: "POST",
                body: { id1: currentUserId, id2: otherUserId },
            });
            const convId = data?.conversationID;
            await fetchChats();

            if (convId) {
                setTimeout(() => {
                    setConversations((list) => {
                        const chat = list.find((c) => c.id === convId || c.conversationId === convId);
                        if (chat) setActiveChat(chat);
                        return list;
                    });
                }, 100);
            }
        } catch (err) { console.error("Error opening direct chat:", err); }
    }, [token, currentUserId, fetchChats]);

    const onGroupCreated = useCallback(async (createdConv) => {
        await fetchChats();
        const newId = createdConv?.conversationID || createdConv?.id;
        if (newId) {
            setConversations((list) => {
                const chat = list.find((c) => c.id === newId || c.conversationId === newId);
                if (chat) setActiveChat(chat);
                return list;
            });
        }
    }, [fetchChats]);

    const toggleMute = async (conversationId, currentMuteStatus) => {
        const newStatus = !currentMuteStatus;

        // 1. Ενημέρωσε το UI ΑΜΕΣΩΣ (Optimistic Update) για να φανεί η αλλαγή γρήγορα
        setConversations(prev => prev.map(c =>
            (c.conversationId === conversationId || c.id === conversationId)
                ? { ...c, muted: newStatus }
                : c
        ));

        if (activeChat?.conversationId === conversationId || activeChat?.id === conversationId) {
            // ΑΥΤΟ ΕΙΝΑΙ ΠΟΥ ΑΛΛΑΖΕΙ ΤΟ ΚΑΜΠΑΝΑΚΙ ΣΤΟ HEADER
            setActiveChat(prev => ({ ...prev, muted: newStatus }));
        }

        try {
            // 2. Στείλε το request στο backend
            const response = await fetch(`/api/chats/${conversationId}/mute?status=${newStatus}`, {
                method: "PATCH",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${token}`
                }
            });

            if (!response.ok) throw new Error("Failed to mute");
            console.log("Mute status updated on server");

        } catch (err) {
            console.error("Mute toggle failed, reverting:", err);
            // Αν αποτύχει, γύρνα το πίσω (προαιρετικό safety)
        }
    };

    // ==========================================
    // 5. WEBSOCKET INTEGRATION
    // ==========================================
    const { stompClient, onlineUsers } = useChatSocket({
        token,
        username: currentUser,
        onNotification: (dto) => {
            // Check if the conversation already exists in our sidebar list
            const chatExists = conversations.some(c =>
                (c.conversationId === dto.conversationId || c.id === dto.conversationId)
            );

            if (!chatExists) {
                // New conversation detected: Refresh the sidebar list from the API
                fetchChats();
            } else {
                // Existing conversation: Bump it to the top with the new message
                bumpConversation(dto.conversationId, {
                    content: dto.content,
                    createdAt: dto.createdAt,
                    isIncoming: dto.senderUsername !== currentUser
                });
            }

            // UI Logic for the currently open chat window
            const currentActiveId = activeChatRef.current?.id || activeChatRef.current?.conversationId;
            const isActive = (currentActiveId === dto.conversationId);

            if (dto.senderUsername !== currentUser) {
                // Play sound logic would go here if not muted
                if (isActive) {
                    setMessages((prev) => [...prev, dto]);
                    markChatRead(dto.conversationId);
                }
            }
        },
    });
    // ==========================================
    // 6. UI & SETTINGS HANDLERS
    // ==========================================

    const toggleTheme = () => {
        const newTheme = theme === "light" ? "dark" : "light";
        setTheme(newTheme);
        localStorage.setItem("theme", newTheme);
    };

    const toggleStealthMode = async () => {
        const newStatus = !stealthMode;
        try {
            const response = await fetch(`/api/users/me/stealth?enabled=${newStatus}`, {
                method: "PATCH",
                headers: { Authorization: `Bearer ${token}` }
            });
            if (response.ok) setStealthMode(newStatus);
        } catch (err) { console.error("Stealth update failed:", err); }
    };

    const handleLogout = () => {
        localStorage.clear();
        window.location.reload();
    };

    // ==========================================
    // 7. INITIALIZATION & LIFECYCLE EFFECTS
    // ==========================================

    useEffect(() => {
        if (token) {
            fetchChats();
            fetchUsers();
        }
    }, [token, fetchChats, fetchUsers]);

    useEffect(() => {
        const chatId = activeChat?.conversationId || activeChat?.id;
        if (!chatId) return;

        setHasMore(true);
        fetchMessages(chatId, 0);
        markChatRead(chatId);
    }, [activeChat?.id, activeChat?.conversationId, fetchMessages, markChatRead]);

    // ==========================================
    // 8. RENDER LOGIC
    // ==========================================

    if (!token) {
        return authView === "login" ? (
            <Login
                onLoginSuccess={(t, u) => {
                    setToken(t); setCurrentUser(u);
                    localStorage.setItem("token", t); localStorage.setItem("username", u);
                }}
                onGoToRegister={() => setAuthView("register")}
            />
        ) : (
            <Register
                onRegisterSuccess={() => setAuthView("login")}
                onGoToLogin={() => setAuthView("login")}
            />
        );
    }

    return (
        <div className={`app-container ${theme === "dark" ? "dark-theme" : ""}`}>
            <header className="header">
                <div>
                    <strong>WebChat</strong> | {currentUser}
                </div>

                <div style={{ display: 'flex', gap: '10px' }}>
                    <button
                        onClick={toggleTheme}
                        style={{
                            padding: '5px 12px', cursor: 'pointer', borderRadius: '15px',
                            border: '1px solid var(--border-color)', background: 'var(--sidebar-bg)',
                            color: 'var(--text-main)', fontSize: '0.85rem'
                        }}
                    >
                        {theme === "light" ? "🌙 Dark" : "☀️ Light"}
                    </button>

                    <span
                        onClick={toggleStealthMode}
                        style={{
                            cursor: 'pointer', fontSize: '1.4rem',
                            filter: stealthMode ? 'drop-shadow(0 0 5px #6c5ce7)' : 'grayscale(1)',
                            opacity: stealthMode ? 1 : 0.5, transition: 'all 0.3s ease'
                        }}
                        title={stealthMode ? "Invisible Mode!" : "Visible"}
                    >
                        👻
                    </span>

                    <button
                        onClick={handleLogout}
                        style={{
                            padding: '5px 12px', cursor: 'pointer', borderRadius: '15px',
                            border: 'none', background: '#ff4d4d', color: 'white',
                            fontSize: '0.85rem', fontWeight: 'bold'
                        }}
                    >
                        Logout
                    </button>
                </div>
            </header>

            <div className="container">
                <Sidebar
                    conversations={conversations}
                    users={allUsers}
                    activeChat={activeChat}
                    onSelectChat={setActiveChat}
                    onGroupCreated={onGroupCreated}
                    currentUser={currentUser}
                    token={token}
                    onlineUsers={onlineUsers}
                />

                <ChatArea
                    activeChat={activeChat}
                    messages={messages}
                    hasMore={hasMore}
                    onLoadMore={() => fetchMessages(activeChat?.conversationId || activeChat?.id, msgPage + 1)}
                    currentUser={currentUser}
                    token={token}
                    setMessages={setMessages}
                    stompClient={stompClient}
                    onMessageSent={(msg) => bumpConversation(msg.conversationId, {
                        content: msg.content,
                        createdAt: msg.createdAt,
                        isIncoming: false
                    })}
                    onToggleMute={toggleMute}
                />

                <RightSidebar
                    users={allUsers}
                    currentUser={currentUser}
                    onOpenDirectChat={openDirectChat}
                    onlineUsers={onlineUsers}
                />
            </div>
        </div>
    );
}

export default App;