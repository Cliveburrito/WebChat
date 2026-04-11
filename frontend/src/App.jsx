import { useCallback, useState } from "react";
import Sidebar from "./components/sidebar/Sidebar";
import ChatArea from "./components/chat/ChatArea";
import RightSidebar from "./components/sidebar/RightSidebar";

import Login from "./components/auth/Login";
import Register from "./components/auth/Register";

import { useAuth } from "./hooks/useAuth";
import { useChatData } from "./hooks/useChatData";
import { useChatSocket } from "./hooks/useChatSocket";
import { useChatTopics } from "./hooks/useChatTopics";
import { instrumentedFetch } from "./api/apiJson";

import "./App.css"; // Σιγουρέψου ότι το import είναι εδώ

function App() {
    const { token, currentUser, authView, setAuthView, isAuthed, loginSuccess, logout } = useAuth();
    const [theme, setTheme] = useState(localStorage.getItem("theme") || "light");
    const [stealthMode, setStealthMode] = useState(false);

    // 1. WebSocket Connection
    const { stompClient, onlineUsers } = useChatSocket({
        token,
        username: currentUser,
    });

    // 2. Chat Data Logic
    const {
        conversations,
        allUsers,
        activeChat,
        messages,
        watermarks,
        msgPage,
        hasMore,
        setActiveChat,
        setMessages,
        fetchMessages,
        bumpConversation,
        queueAck,
        openDirectChat,
        onGroupCreated,
        toggleMute,
        markChatRead,
        onWatermarkUpdate,
        activeChatId,
        currentUserId,
        upsertConversation,
        upsertUser,
    } = useChatData({ token, currentUser, stompClient });

    // 3. Live Subscriptions
    useChatTopics({
        stompClient,
        conversations,
        allUsers,
        activeChatId,
        currentUser,
        currentUserId,
        setMessages,
        bumpConversation,
        queueAck,
        markChatRead,
        upsertConversation,
        upsertUser,
        onWatermarkUpdate,
    });

    const toggleTheme = useCallback(() => {
        const newTheme = theme === "light" ? "dark" : "light";
        setTheme(newTheme);
        localStorage.setItem("theme", newTheme);
    }, [theme]);

    const toggleStealthMode = useCallback(async () => {
        const newStatus = !stealthMode;
        try {
            const response = await instrumentedFetch(`/api/users/me/stealth?enabled=${newStatus}`, {
                method: "PATCH",
                headers: { Authorization: `Bearer ${token}` },
            });
            if (response.ok) setStealthMode(newStatus);
        } catch (err) { console.error("Stealth update failed:", err); }
    }, [stealthMode, token]);

    if (!isAuthed) {
        return authView === "login" ? (
            <Login onLoginSuccess={loginSuccess} onGoToRegister={() => setAuthView("register")} />
        ) : (
            <Register onRegisterSuccess={() => setAuthView("login")} onGoToLogin={() => setAuthView("login")} />
        );
    }

    return (
        <div className={`app-container ${theme === "dark" ? "dark-theme" : ""}`}>
            {/* GLOBAL HEADER */}
            <header className="main-header">
                <div className="brand">
                    <strong>WebChat</strong> <span className="user-tag">| {currentUser}</span>
                </div>

                <div className="header-controls">
                    <button className="control-btn" onClick={toggleTheme}>
                        {theme === "light" ? "🌙 Dark" : "☀️ Light"}
                    </button>

                    <span
                        className={`stealth-ghost ${stealthMode ? "active" : ""}`}
                        onClick={toggleStealthMode}
                        title={stealthMode ? "Invisible Mode" : "Visible"}
                    >
                        👻
                    </span>

                    <button className="logout-btn" onClick={logout}>Logout</button>
                </div>
            </header>

            {/* MAIN CONTENT AREA */}
            <div className="main-layout">
                <Sidebar
                    conversations={conversations}
                    users={allUsers}
                    activeChat={activeChat}
                    onSelectChat={setActiveChat}
                    onGroupCreated={onGroupCreated}
                    currentUser={currentUser}
                    currentUserId={currentUserId}
                    token={token}
                    onlineUsers={onlineUsers}
                    watermarks={watermarks}
                />

                <ChatArea
                    activeChat={activeChat}
                    messages={messages}
                    hasMore={hasMore}
                    onLoadMore={() => fetchMessages(activeChatId, msgPage + 1)}
                    currentUser={currentUser}
                    currentUserId={currentUserId}
                    token={token}
                    setMessages={setMessages}
                    stompClient={stompClient}
                    watermarks={watermarks}
                    onMessageSent={(msg) =>
                        bumpConversation(msg.conversationId, {
                            content: msg.content,
                            createdAt: msg.createdAt,
                            isIncoming: false,
                        })
                    }
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
