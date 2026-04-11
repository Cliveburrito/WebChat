import { useCallback, useState } from "react";
import Sidebar from "./components/sidebar/Sidebar";
import ChatArea from "./components/chat/ChatArea";
import RightSidebar from "./components/sidebar/RightSidebar";
import Icon from "./components/common/Icon";
import ProfileSettingsModal from "./components/profile/ProfileSettingsModal";

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
    const [isChatDrawerOpen, setIsChatDrawerOpen] = useState(false);
    const [isDirectoryDrawerOpen, setIsDirectoryDrawerOpen] = useState(false);
    const [isProfileOpen, setIsProfileOpen] = useState(false);

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
        selfWatermarks,
        msgPage,
        hasMore,
        setActiveChat,
        setMessages,
        fetchMessages,
        bumpConversation,
        updateConversationMessagePreview,
        updateMyProfile,
        updateMyAvatar,
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
        updateConversationMessagePreview,
        queueAck,
        markChatRead,
        upsertConversation,
        upsertUser,
        onWatermarkUpdate,
    });

    const currentUserProfile = allUsers.find((user) => user.username === currentUser);

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

    const closeMobileDrawers = useCallback(() => {
        setIsChatDrawerOpen(false);
        setIsDirectoryDrawerOpen(false);
    }, []);

    const selectChat = useCallback((chat) => {
        setActiveChat(chat);
        closeMobileDrawers();
    }, [setActiveChat, closeMobileDrawers]);

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
                    <button
                        type="button"
                        className="mobile-nav-btn"
                        onClick={() => setIsChatDrawerOpen(true)}
                        aria-label="Open chats"
                    >
                        <Icon name="menu" size={19} />
                    </button>
                    <span className="brand-mark">W</span>
                    <div className="brand-copy">
                        <strong>WebChat</strong>
                        <button type="button" className="user-tag profile-link" onClick={() => setIsProfileOpen(true)}>
                            {currentUserProfile?.displayName || currentUser}
                        </button>
                    </div>
                </div>

                <div className="header-controls">
                    <button className="control-btn" onClick={toggleTheme}>
                        <Icon name={theme === "light" ? "moon" : "sun"} size={17} />
                        <span>{theme === "light" ? "Dark" : "Light"}</span>
                    </button>

                    <button
                        type="button"
                        className={`stealth-ghost ${stealthMode ? "active" : ""}`}
                        onClick={toggleStealthMode}
                        title={stealthMode ? "Invisible Mode" : "Visible"}
                        aria-label={stealthMode ? "Disable invisible mode" : "Enable invisible mode"}
                    >
                        <Icon name="eyeOff" size={18} />
                    </button>

                    <button
                        type="button"
                        className="mobile-nav-btn"
                        onClick={() => setIsDirectoryDrawerOpen(true)}
                        aria-label="Open directory"
                    >
                        <Icon name="users" size={19} />
                    </button>

                    <button className="logout-btn" onClick={logout}>Logout</button>
                </div>
            </header>

            {/* MAIN CONTENT AREA */}
            <div className="main-layout">
                {(isChatDrawerOpen || isDirectoryDrawerOpen) && (
                    <button
                        type="button"
                        className="mobile-drawer-backdrop"
                        onClick={closeMobileDrawers}
                        aria-label="Close sidebars"
                    />
                )}

                <Sidebar
                    conversations={conversations}
                    users={allUsers}
                    activeChat={activeChat}
                    onSelectChat={selectChat}
                    onGroupCreated={onGroupCreated}
                    currentUser={currentUser}
                    currentUserId={currentUserId}
                    token={token}
                    onlineUsers={onlineUsers}
                    watermarks={watermarks}
                    isMobileOpen={isChatDrawerOpen}
                    onRequestClose={() => setIsChatDrawerOpen(false)}
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
                    selfWatermark={selfWatermarks?.[String(activeChatId)]}
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
                    onOpenDirectChat={(userId) => {
                        openDirectChat(userId);
                        closeMobileDrawers();
                    }}
                    onlineUsers={onlineUsers}
                    isMobileOpen={isDirectoryDrawerOpen}
                    onRequestClose={() => setIsDirectoryDrawerOpen(false)}
                />
            </div>

            <ProfileSettingsModal
                open={isProfileOpen}
                profile={currentUserProfile || { username: currentUser }}
                onClose={() => setIsProfileOpen(false)}
                onSaveProfile={updateMyProfile}
                onUploadAvatar={updateMyAvatar}
            />
        </div>
    );
}

export default App;
