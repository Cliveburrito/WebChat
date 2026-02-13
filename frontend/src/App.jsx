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

function App() {
    const { token, currentUser, authView, setAuthView, isAuthed, loginSuccess, logout } = useAuth();

    const [theme, setTheme] = useState(localStorage.getItem("theme") || "light");
    const [stealthMode, setStealthMode] = useState(false);

    const {
        conversations,
        allUsers,
        activeChat,
        messages,
        msgPage,
        hasMore,
        setActiveChat,
        setMessages,
        fetchMessages,
        bumpConversation,
        openDirectChat,
        onGroupCreated,
        toggleMute,
        markChatRead,
        activeChatId,
    } = useChatData({ token, currentUser });

    // WS connection + presence only
    const { stompClient, onlineUsers } = useChatSocket({
        token,
        username: currentUser,
        debug: true,
    });

    // ✅ realtime for chats: messages, confirmation, attachments, unread, bump
    useChatTopics({
        stompClient,
        conversations,
        activeChatId,
        currentUser,
        setMessages,
        bumpConversation,
        markChatRead,
        debug: true, // βγάλτο μετά
    });

    const toggleTheme = useCallback(() => {
        const newTheme = theme === "light" ? "dark" : "light";
        setTheme(newTheme);
        localStorage.setItem("theme", newTheme);
    }, [theme]);

    const toggleStealthMode = useCallback(async () => {
        const newStatus = !stealthMode;
        try {
            const response = await fetch(`/api/users/me/stealth?enabled=${newStatus}`, {
                method: "PATCH",
                headers: { Authorization: `Bearer ${token}` },
            });
            if (response.ok) setStealthMode(newStatus);
        } catch (err) {
            console.error("Stealth update failed:", err);
        }
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
            <header className="header">
                <div>
                    <strong>WebChat</strong> | {currentUser}
                </div>

                <div style={{ display: "flex", gap: "10px" }}>
                    <button
                        onClick={toggleTheme}
                        style={{
                            padding: "5px 12px",
                            cursor: "pointer",
                            borderRadius: "15px",
                            border: "1px solid var(--border-color)",
                            background: "var(--bg-sidebar)",
                            color: "var(--text-main)",
                            fontSize: "0.85rem",
                        }}
                    >
                        {theme === "light" ? "🌙 Dark" : "☀️ Light"}
                    </button>

                    <span
                        onClick={toggleStealthMode}
                        style={{
                            cursor: "pointer",
                            fontSize: "1.4rem",
                            filter: stealthMode ? "drop-shadow(0 0 5px #6c5ce7)" : "grayscale(1)",
                            opacity: stealthMode ? 1 : 0.5,
                            transition: "all 0.3s ease",
                        }}
                        title={stealthMode ? "Invisible Mode!" : "Visible"}
                    >
            👻
          </span>

                    <button
                        onClick={logout}
                        style={{
                            padding: "5px 12px",
                            cursor: "pointer",
                            borderRadius: "15px",
                            border: "none",
                            background: "#ff4d4d",
                            color: "white",
                            fontSize: "0.85rem",
                            fontWeight: "bold",
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
                    onLoadMore={() => fetchMessages(activeChatId, msgPage + 1)}
                    currentUser={currentUser}
                    token={token}
                    setMessages={setMessages}
                    stompClient={stompClient}
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
