import { useState, useEffect, useCallback, useRef } from "react";
import Sidebar from "./components/Sidebar";
import ChatArea from "./components/ChatArea";
import RightSidebar from "./components/RightSidebar";
import Login from "./components/Login";
import Register from "./components/Register";
import { apiJson } from "./api";
import { useChatSocket } from "./hooks/useChatSocket";

function App() {
    const [token, setToken] = useState(localStorage.getItem("token"));
    const [currentUser, setCurrentUser] = useState(localStorage.getItem("username") || "");
    const [conversations, setConversations] = useState([]);
    const [allUsers, setAllUsers] = useState([]);
    const [activeChat, setActiveChat] = useState(null);
    const [messages, setMessages] = useState([]);
    const [msgPage, setMsgPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [currentUserId, setCurrentUserId] = useState(null);
    const [authView, setAuthView] = useState("login");

    // 1. STATE ΓΙΑ ΤΟ THEME (Αρχικοποίηση από localStorage)
    const [theme, setTheme] = useState(localStorage.getItem("theme") || "light");

    const activeChatRef = useRef(null);
    useEffect(() => { activeChatRef.current = activeChat; }, [activeChat]);

    // 2. ΣΥΝΑΡΤΗΣΗ ΕΝΑΛΛΑΓΗΣ ΘΕΜΑΤΟΣ
    const toggleTheme = () => {
        const newTheme = theme === "light" ? "dark" : "light";
        setTheme(newTheme);
        localStorage.setItem("theme", newTheme);
    };

    const bumpConversation = useCallback((conversationId, { content, createdAt, isIncoming }) => {
        setConversations((prev) => {
            const idx = prev.findIndex((c) => c.id === conversationId);
            if (idx === -1) return prev;
            const isOpen = activeChatRef.current?.id === conversationId;
            const updated = {
                ...prev[idx],
                lastMessage: content,
                lastMessageAt: createdAt || new Date().toISOString(),
                unreadCount: isIncoming && !isOpen ? (prev[idx].unreadCount + 1) : (isOpen ? 0 : prev[idx].unreadCount),
            };
            const copy = [...prev];
            copy.splice(idx, 1);
            return [updated, ...copy];
        });
    }, []);

    useChatSocket({
        token,
        username: currentUser,
        onNotification: (dto) => {
            bumpConversation(dto.conversationId, {
                content: dto.content,
                createdAt: dto.createdAt,
                isIncoming: dto.senderUsername !== currentUser
            });
            if (activeChatRef.current?.id === dto.conversationId) {
                if (dto.senderUsername !== currentUser) {
                    setMessages((prev) => [...prev, dto]);
                    markChatRead(dto.conversationId);
                }
            }
        },
    });

    const fetchChats = useCallback(async () => {
        if (!token) return;
        try {
            const data = await apiJson("/api/chats/my", { token });
            setConversations(Array.isArray(data) ? data : []);
        } catch (err) { console.error(err); }
    }, [token]);

    const fetchUsers = useCallback(async () => {
        if (!token) return;
        try {
            const data = await apiJson("/api/users/getall", { token });
            const list = Array.isArray(data) ? data : [];
            setAllUsers(list);
            const me = list.find((u) => u.username === currentUser);
            if (me) setCurrentUserId(me.id);
        } catch (err) { console.error(err); }
    }, [token, currentUser]);

    const fetchMessages = useCallback(async (chatId, page = 0) => {
        if (!token || !chatId) return;
        try {
            const data = await apiJson(`/api/chats/${chatId}/messages?page=${page}&size=20`, { token });
            const fetched = (data?.content || []).slice().sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
            if (page === 0) setMessages(fetched);
            else setMessages((prev) => [...fetched, ...prev]);
            setHasMore(!data?.last);
            setMsgPage(page);
        } catch (err) { console.error(err); }
    }, [token]);

    const markChatRead = useCallback(async (chatId) => {
        if (!token || !chatId) return;
        setConversations((prev) => prev.map((c) => (c.id === chatId ? { ...c, unreadCount: 0 } : c)));
        try { await apiJson(`/api/chats/${chatId}/read`, { token, method: "POST" }); } catch {}
    }, [token]);

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
                        const chat = list.find((c) => c.id === convId);
                        if (chat) setActiveChat(chat);
                        return list;
                    });
                }, 100);
            }
        } catch (err) { console.error(err); }
    }, [token, currentUserId, fetchChats]);

    const onGroupCreated = useCallback(async (createdConv) => {
        await fetchChats();
        const newId = createdConv?.conversationID;
        if (newId) {
            setConversations((list) => {
                const chat = list.find((c) => c.id === newId);
                if (chat) setActiveChat(chat);
                return list;
            });
        }
    }, [fetchChats]);

    useEffect(() => { if (token) fetchChats(); }, [token, fetchChats]);
    useEffect(() => { if (token) fetchUsers(); }, [token, fetchUsers]);

    useEffect(() => {
        if (!activeChat?.id) return;
        setHasMore(true);
        fetchMessages(activeChat.id, 0);
        markChatRead(activeChat.id);
    }, [activeChat?.id, fetchMessages, markChatRead]);

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
        /* 3. ΕΦΑΡΜΟΓΗ ΤΗΣ ΚΛΑΣΗΣ ΣΤΟ CONTAINER */
        <div className={`app-container ${theme === "dark" ? "dark-theme" : ""}`}>
            <header className="header">
                <div>
                    <strong>WebChat</strong> | {currentUser}
                </div>

                {}
                <div style={{ display: 'flex', gap: '10px' }}>
                    <button
                        onClick={toggleTheme}
                        style={{
                            padding: '5px 12px',
                            cursor: 'pointer',
                            borderRadius: '15px',
                            border: '1px solid var(--border-color)',
                            background: 'var(--sidebar-bg)',
                            color: 'var(--text-main)',
                            fontSize: '0.85rem'
                        }}
                    >
                        {theme === "light" ? "🌙 Dark" : "☀️ Light"}
                    </button>

                    <button
                        onClick={() => { localStorage.clear(); window.location.reload(); }}
                        style={{
                            padding: '5px 12px',
                            cursor: 'pointer',
                            borderRadius: '15px',
                            border: 'none',
                            background: '#ff4d4d',
                            color: 'white',
                            fontSize: '0.85rem',
                            fontWeight: 'bold'
                        }}
                    >
                        Logout
                    </button>
                </div>
            </header>

            <div className="container">
                <Sidebar conversations={conversations} activeChat={activeChat} onSelectChat={setActiveChat} onGroupCreated={onGroupCreated} token={token} />
                <ChatArea activeChat={activeChat} messages={messages} currentUser={currentUser} token={token} setMessages={setMessages} onMessageSent={(msg) => bumpConversation(msg.conversationId, { content: msg.content, createdAt: msg.createdAt, isIncoming: false })} />
                <RightSidebar users={allUsers} currentUser={currentUser} onOpenDirectChat={openDirectChat} />
            </div>
        </div>
    );
}

export default App;