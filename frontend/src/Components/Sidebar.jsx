import { useState, useMemo } from "react";

export default function Sidebar({
                                    conversations,
                                    users,
                                    activeChat,
                                    onSelectChat,
                                    currentUser,
                                    token,
                                    onGroupCreated,
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
            {/* Header με κουμπί για Group */}
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
                        {conversations.map((chat) => (
                            <div
                                key={chat.id}
                                className={`item ${activeChat?.id === chat.id ? "active" : ""} ${chat.unreadCount > 0 ? "unread" : ""}`}
                                onClick={() => onSelectChat(chat)}
                                style={{ display: "flex", justifyContent: "space-between", gap: 10, alignItems: "center" }}
                            >
                                <div style={{ minWidth: 0, flex: 1 }}>
                                    <strong>{chat.name}</strong>
                                    <small>{chat.lastMessage || "No messages yet"}</small>
                                </div>

                                {chat.unreadCount > 0 && (
                                    <span className="unread-badge">{chat.unreadCount}</span>
                                )}
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {}
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
                                {contacts.map((u) => (
                                    <label key={u.id} style={styles.memberRow}>
                                        <input
                                            type="checkbox"
                                            checked={selectedIds.includes(u.id)}
                                            onChange={() => toggleUser(u.id)}
                                        />
                                        <span style={{ marginLeft: 8 }}>{u.username}</span>
                                    </label>
                                ))}
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

const styles = {
    backdrop: { position: "fixed", inset: 0, background: "rgba(0,0,0,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 },
    modal: { width: 400, background: "#fff", borderRadius: 12, padding: 20, boxShadow: "0 10px 25px rgba(0,0,0,0.2)" },
    modalHeader: { display: "flex", justifyContent: "space-between", marginBottom: 15 },
    xBtn: { border: "none", background: "transparent", fontSize: 18, cursor: "pointer" },
    label: { display: "block", fontSize: 12, color: "#666", marginBottom: 5 },
    input: { width: "100%", padding: "10px", borderRadius: 8, border: "1px solid #ddd", marginBottom: 10 },
    membersBox: { border: "1px solid #eee", borderRadius: 8, padding: 10, maxHeight: 200, overflowY: "auto", background: "#f9f9f9" },
    memberRow: { display: "flex", alignItems: "center", padding: "5px 0", cursor: "pointer" },
    actions: { display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 15 },
    cancelBtn: { padding: "8px 15px", borderRadius: 8, border: "1px solid #ddd", background: "#fff", cursor: "pointer" },
    createBtn: { padding: "8px 15px", borderRadius: 8, border: "none", background: "#0084ff", color: "#fff", fontWeight: "bold", cursor: "pointer" },
    error: { background: "#ffebee", color: "#c62828", padding: "10px", borderRadius: 8, marginBottom: 10, fontSize: 13 }
};