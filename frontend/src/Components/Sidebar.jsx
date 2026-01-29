import { useState, useMemo } from "react";

export default function Sidebar({
                                    conversations,
                                    users,
                                    activeChat,
                                    onSelectChat,
                                    currentUser,
                                    token,
                                    onGroupCreated,
                                    onlineUsers = [] // Added Prop
                                }) {
    const [isOpen, setIsOpen] = useState(false);
    const [groupName, setGroupName] = useState("");
    const [selectedIds, setSelectedIds] = useState([]);
    const [error, setError] = useState("");
    const [isCreating, setIsCreating] = useState(false);

    const contacts = useMemo(() => {
        return (users || []).filter(u => u.username !== currentUser);
    }, [users, currentUser]);

    const toggleUser = (id) => {
        setSelectedIds(prev =>
            prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]
        );
    };

    const resetModal = () => {
        setGroupName("");
        setSelectedIds([]);
        setError("");
        setIsCreating(false);
    };

    const closeModal = () => {
        setIsOpen(false);
        resetModal();
    };

    const handleCreateGroup = async () => {
        setError("");
        const name = groupName.trim();
        if (!name) return setError("Βάλε όνομα στο group.");
        if (selectedIds.length < 2) return setError("Διάλεξε τουλάχιστον 2 μέλη.");

        setIsCreating(true);
        try {
            const res = await fetch("/api/chats/group", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify({
                    groupName: name,
                    memberIds: selectedIds,
                }),
            });

            if (!res.ok) throw new Error("Αποτυχία δημιουργίας group.");

            const created = await res.json();
            onGroupCreated?.(created);
            closeModal();
        } catch (e) {
            setError(e.message);
        } finally {
            setIsCreating(false);
        }
    };

    return (
        <aside className="sidebar left">
            <div style={{ padding: "15px", borderBottom: "1px solid #ddd" }}>
                <button
                    className="create-group-btn"
                    style={{ width: "100%", padding: "10px", fontWeight: "bold" }}
                    onClick={() => setIsOpen(true)}
                >
                    + New Group Chat
                </button>
            </div>

            <div className="sidebar-content">
                <div className="sidebar-section">
                    <h4 className="sidebar-title">My Chats</h4>
                    <div className="sidebar-list">
                        {conversations.map((chat) => {
                            // Only show dots for Direct Chats (Assuming chat.name is the username)
                            // If your DTO has an 'isGroup' flag, use it here: !chat.isGroup
                            const isDirectChat = !chat.name.includes(", ");
                            const isOnline = isDirectChat && onlineUsers.includes(chat.name);

                            return (
                                <div
                                    key={chat.id}
                                    className={`item ${activeChat?.id === chat.id ? "active" : ""} ${chat.unreadCount > 0 ? "unread" : ""}`}
                                    onClick={() => onSelectChat(chat)}
                                    style={{ display: "flex", justifyContent: "space-between", gap: 10, alignItems: "center" }}
                                >
                                    <div style={{ display: "flex", alignItems: "center", gap: 10, minWidth: 0, flex: 1 }}>
                                        {/* Presence Dot for Direct Chats */}
                                        {isDirectChat && (
                                            <span className={`status-dot ${isOnline ? "online" : "offline"}`}></span>
                                        )}

                                        <div style={{ minWidth: 0, flex: 1 }}>
                                            <strong>{chat.name}</strong>
                                            <small className="last-msg-text">{chat.lastMessage || "No messages yet"}</small>
                                        </div>
                                    </div>

                                    {chat.unreadCount > 0 && (
                                        <span className="unread-badge">{chat.unreadCount}</span>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </div>
            </div>

            {isOpen && (
                <div style={styles.backdrop} onClick={closeModal}>
                    <div style={styles.modal} onClick={(e) => e.stopPropagation()}>
                        <div style={styles.modalHeader}>
                            <strong>Δημιουργία Group Chat</strong>
                            <button style={styles.xBtn} onClick={closeModal}>✕</button>
                        </div>

                        {error && <div style={styles.error}>⚠️ {error}</div>}

                        <div style={{ marginBottom: 12 }}>
                            <label style={styles.label}>Όνομα Group</label>
                            <input
                                value={groupName}
                                onChange={(e) => setGroupName(e.target.value)}
                                placeholder="π.χ. Παρέα, Δουλειά..."
                                style={styles.input}
                            />
                        </div>

                        <div style={{ marginBottom: 12 }}>
                            <label style={styles.label}>Επιλογή Μελών</label>
                            <div style={styles.membersBox}>
                                {contacts.map((u) => {
                                    const isOnline = onlineUsers.includes(u.username);
                                    return (
                                        <label key={u.id} style={styles.memberRow}>
                                            <input
                                                type="checkbox"
                                                checked={selectedIds.includes(u.id)}
                                                onChange={() => toggleUser(u.id)}
                                            />
                                            {/* Presence dot in member selection */}
                                            <span className={`status-dot ${isOnline ? "online" : "offline"}`} style={{ margin: "0 8px" }}></span>
                                            <span>{u.username}</span>
                                        </label>
                                    );
                                })}
                            </div>
                        </div>

                        <div style={styles.actions}>
                            <button onClick={closeModal} style={styles.cancelBtn}>Ακύρωση</button>
                            <button onClick={handleCreateGroup} style={styles.createBtn} disabled={isCreating}>
                                {isCreating ? "Δημιουργία..." : "Δημιουργία"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </aside>
    );
}

// ... styles unchanged