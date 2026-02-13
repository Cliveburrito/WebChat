import { useMemo, useState } from "react";
import ChatList from "./ChatList";
import CreateGroupModal from "./CreateGroupModal";

export default function Sidebar({
                                    conversations = [],
                                    users = [],
                                    activeChat,
                                    onSelectChat,
                                    currentUser,
                                    token,
                                    onGroupCreated,
                                    onlineUsers = []
                                }) {
    const [isOpen, setIsOpen] = useState(false);

    // (προαιρετικό) μπορείς να το σβήσεις τελείως γιατί το modal έχει δικό του useMemo,
    // το αφήνω εδώ μόνο αν θέλεις να το ξαναχρησιμοποιήσεις αλλού.
    const contactsCount = useMemo(() => {
        if (!users) return 0;
        return users.filter(u => u.username !== currentUser).length;
    }, [users, currentUser]);

    return (
        <aside className="sidebar left">
            <div style={{ padding: "15px", borderBottom: "1px solid var(--border-color)" }}>
                <button
                    style={{
                        width: "100%", padding: "12px", fontWeight: "bold",
                        borderRadius: "8px", cursor: "pointer",
                        background: "var(--primary-blue)", color: "white", border: "none"
                    }}
                    onClick={() => setIsOpen(true)}
                    title={`Create group (${contactsCount} contacts)`}
                >
                    + New Group Chat
                </button>
            </div>

            <div className="sidebar-content">
                <div className="sidebar-section">
                    <h4 className="sidebar-title">My Chats</h4>
                    <div className="sidebar-list">
                        <ChatList
                            conversations={conversations}
                            activeChat={activeChat}
                            onSelectChat={onSelectChat}
                            onlineUsers={onlineUsers}
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
