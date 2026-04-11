import { useMemo, useState } from "react";
import ChatList from "../common/ChatList.jsx";
import CreateGroupModal from "./CreateGroupModal";

export default function Sidebar({
                                    conversations = [],
                                    users = [],
                                    activeChat,
                                    onSelectChat,
                                    currentUser,   // Username (String)
                                    currentUserId, // <--- NEW: Το ID του χρήστη (Long/Int)
                                    token,
                                    onGroupCreated,
                                    onlineUsers = [],
                                    watermarks,     // <--- NEW: Τα ticks από το App.js
                                    isMobileOpen = false,
                                    onRequestClose
                                }) {
    const [isOpen, setIsOpen] = useState(false);

    const contactsCount = useMemo(() => {
        if (!users) return 0;
        return users.filter(u => u.username !== currentUser).length;
    }, [users, currentUser]);

    return (
        <aside className={`sidebar left ${isMobileOpen ? "mobile-open" : ""}`}>
            <div className="sidebar-mobile-topbar">
                <strong>Chats</strong>
                <button type="button" onClick={onRequestClose} aria-label="Close chats">x</button>
            </div>
            <div className="sidebar-compose">
                <button
                    className="new-chat-btn"
                    onClick={() => setIsOpen(true)}
                    title={`Create group (${contactsCount} contacts)`}
                >
                    <span>+</span>
                    <strong>New Group Chat</strong>
                </button>
            </div>

            <div className="sidebar-content">
                <div className="sidebar-section">
                    <div className="sidebar-header-row">
                        <h4 className="sidebar-title">My Chats</h4>
                        <span className="sidebar-subtitle">{conversations.length} chats</span>
                    </div>
                    <div className="sidebar-list">
                        {/* Περνάμε τα props κάτω στο ChatList */}
                        <ChatList
                            conversations={conversations}
                            activeChat={activeChat}
                            onSelectChat={onSelectChat}
                            onlineUsers={onlineUsers}
                            watermarks={watermarks}       // <--- Pass down
                            currentUserId={currentUserId} // <--- Pass down
                        />
                    </div>
                </div>
            </div>

            <CreateGroupModal
                open={isOpen}
                users={users}
                currentUser={currentUser}
                token={token}
                onlineUsers={onlineUsers}
                onGroupCreated={onGroupCreated}
                onClose={() => setIsOpen(false)}
            />
        </aside>
    );
}
