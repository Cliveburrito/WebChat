import { useState, useMemo } from "react";

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
    // --- State ---
    const [isOpen, setIsOpen] = useState(false);
    const [groupName, setGroupName] = useState("");
    const [selectedIds, setSelectedIds] = useState([]);
    const [error, setError] = useState("");
    const [isCreating, setIsCreating] = useState(false);

    // --- Logic ---
    const contacts = useMemo(() => {
        if (!users) return [];
        return users.filter(u => u.username !== currentUser);
    }, [users, currentUser]);

    // --- Handlers ---
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
        if (!name) return setError("Δώσε ένα όνομα στο group.");
        if (selectedIds.length < 2) return setError("Επίλεξε τουλάχιστον 2 μέλη.");

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
            {/* --- Header: Create Group Button --- */}
            <div style={styles.sidebarHeader}>
                <button
                    style={styles.newGroupBtn}
                    onClick={() => setIsOpen(true)}
                >
                    + New Group Chat
                </button>
            </div>

            {/* --- Content: Chat List --- */}
            <div className="sidebar-content">
                <div className="sidebar-section">
                    <h4 className="sidebar-title">My Chats</h4>
                    <div className="sidebar-list">
                        {conversations.length === 0 ? (
                            <p style={styles.emptyText}>No chats yet</p>
                        ) : (
                            conversations.map((chat) => {
                                // 1. Τίτλος (Display Name)
                                const chatTitle = chat.displayName || chat.name || "Unknown Chat";
                                const cid = chat.conversationId || chat.id;
                                const isActive = activeChat?.conversationId === cid || activeChat?.id === cid;

                                // 2. ΛΟΓΙΚΗ (Η Παλιά που δουλεύει)
                                // Direct αν δεν έχει κόμμα στο όνομα
                                const isDirect = chat.name && !chat.name.includes(", ");
                                // Online αν το username υπάρχει ακριβώς στη λίστα
                                const isOnline = isDirect && onlineUsers.includes(chat.name);

                                return (
                                    <div
                                        key={cid}
                                        className={`item ${isActive ? "active" : ""} ${chat.unreadCount > 0 ? "unread" : ""}`}
                                        onClick={() => onSelectChat(chat)}
                                        style={styles.chatItem}
                                    >
                                        {/* Left Side: Avatar/Dot + Name + Message */}
                                        <div style={styles.chatItemLeft}>

                                            {/* Το Online Dot */}
                                            {isDirect && (
                                                <span
                                                    className={`status-dot ${isOnline ? "online" : "offline"}`}
                                                    // Προσθήκη inline style για σιγουριά στο χρώμα (όπως ζήτησες πριν)
                                                    style={{
                                                        ...styles.statusDot,
                                                        backgroundColor: isOnline ? '#2ecc71' : '#bdc3c7'
                                                    }}
                                                ></span>
                                            )}

                                            <div style={{ minWidth: 0, flex: 1 }}>
                                                <strong style={styles.chatName}>
                                                    {chatTitle}
                                                </strong>
                                                <small className="last-msg-text">
                                                    {chat.lastContent || chat.lastMessage || "No messages yet"}
                                                </small>
                                            </div>
                                        </div>

                                        {/* Right Side: Mute Icon + Badge */}
                                        <div style={styles.chatItemRight}>
                                            {chat.muted && (
                                                <span title="Muted" style={styles.muteIcon}>🔕</span>
                                            )}
                                            {chat.unreadCount > 0 && (
                                                <span className="unread-badge">{chat.unreadCount}</span>
                                            )}
                                        </div>
                                    </div>
                                );
                            })
                        )}
                    </div>
                </div>
            </div>

            {/* --- Modal: Create Group (Ίδιο με πριν) --- */}
            {isOpen && (
                <div style={styles.backdrop} onClick={closeModal}>
                    <div style={styles.modal} onClick={(e) => e.stopPropagation()}>
                        <div style={styles.modalHeader}>
                            <strong>Create Group Chat</strong>
                            <button style={styles.xBtn} onClick={closeModal}>✕</button>
                        </div>

                        {error && <div style={styles.error}>⚠️ {error}</div>}

                        <div style={{ marginBottom: 15 }}>
                            <label style={styles.label}>Group Name</label>
                            <input
                                value={groupName}
                                onChange={(e) => setGroupName(e.target.value)}
                                placeholder="e.g. Family, Work..."
                                style={styles.input}
                            />
                        </div>

                        <div style={{ marginBottom: 15 }}>
                            <label style={styles.label}>Select Members ({selectedIds.length})</label>
                            <div style={styles.membersBox}>
                                {contacts.length > 0 ? (
                                    contacts.map((u) => {
                                        const isOnline = onlineUsers.includes(u.username);
                                        const isSelected = selectedIds.includes(u.id);
                                        return (
                                            <label
                                                key={u.id}
                                                style={{
                                                    ...styles.memberRow,
                                                    background: isSelected ? "rgba(0, 132, 255, 0.1)" : "transparent"
                                                }}
                                            >
                                                <input
                                                    type="checkbox"
                                                    checked={isSelected}
                                                    onChange={() => toggleUser(u.id)}
                                                    style={{ cursor: "pointer", width: "16px", height: "16px" }}
                                                />
                                                {/* Dot και μέσα στο Modal */}
                                                <span
                                                    className={`status-dot ${isOnline ? "online" : "offline"}`}
                                                    style={{
                                                        margin: "0 10px",
                                                        width: '8px',
                                                        height: '8px',
                                                        borderRadius: '50%',
                                                        display: 'inline-block',
                                                        backgroundColor: isOnline ? '#2ecc71' : '#bdc3c7'
                                                    }}
                                                ></span>
                                                <span style={{ color: "var(--text-main)", fontWeight: isSelected ? "bold" : "normal" }}>
                                                    {u.username}
                                                </span>
                                            </label>
                                        );
                                    })
                                ) : (
                                    <p style={{ textAlign: "center", padding: "10px", color: "var(--text-secondary)" }}>No users found</p>
                                )}
                            </div>
                        </div>

                        <div style={styles.actions}>
                            <button onClick={closeModal} style={styles.cancelBtn}>Cancel</button>
                            <button
                                onClick={handleCreateGroup}
                                style={styles.createBtn}
                                disabled={isCreating || !groupName.trim() || selectedIds.length < 2}
                            >
                                {isCreating ? "Creating..." : "Create Group"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </aside>
    );
}

// --- Styles Object ---
const styles = {
    sidebarHeader: {
        padding: "15px",
        borderBottom: "1px solid var(--border-color)"
    },
    newGroupBtn: {
        width: "100%", padding: "12px", fontWeight: "bold",
        borderRadius: "8px", cursor: "pointer",
        background: "var(--primary-blue)", color: "white", border: "none"
    },
    emptyText: {
        padding: "20px", color: "var(--text-secondary)", fontSize: "0.9rem"
    },

    // Chat Item Structure
    chatItem: {
        display: "flex", justifyContent: "space-between", gap: 10, alignItems: "center"
    },
    chatItemLeft: {
        display: "flex", alignItems: "center", gap: 10, minWidth: 0, flex: 1
    },
    chatItemRight: {
        display: "flex", alignItems: "center", gap: "8px", justifyContent: "flex-end"
    },
    chatName: {
        display: "block", textOverflow: "ellipsis", overflow: "hidden", whiteSpace: "nowrap"
    },
    // Προσθήκη style για το dot ώστε να είμαστε σίγουροι
    statusDot: {
        width: '10px',
        height: '10px',
        borderRadius: '50%',
        display: 'inline-block',
    },
    muteIcon: {
        fontSize: "0.9rem", opacity: 0.6
    },

    // Modal Styles
    backdrop: {
        position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
        backgroundColor: 'rgba(0,0,0,0.7)', display: 'flex',
        alignItems: 'center', justifyContent: 'center', zIndex: 2000,
    },
    modal: {
        backgroundColor: 'var(--bg-sidebar)', padding: '24px',
        borderRadius: '12px', width: '420px', maxWidth: '95%',
        boxShadow: '0 12px 40px rgba(0,0,0,0.4)', color: 'var(--text-main)',
        border: '1px solid var(--border-color)', animation: 'modalFadeIn 0.3s ease'
    },
    modalHeader: {
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        marginBottom: '20px', fontSize: '1.2rem', borderBottom: '1px solid var(--border-color)',
        paddingBottom: '10px'
    },
    xBtn: {
        background: 'none', border: 'none', color: 'var(--text-secondary)',
        cursor: 'pointer', fontSize: '1.5rem', padding: '5px'
    },
    label: {
        display: 'block', marginBottom: '8px', fontWeight: 'bold', fontSize: '0.9rem', color: 'var(--text-secondary)'
    },
    input: {
        width: '100%', padding: '12px', borderRadius: '8px',
        border: '1px solid var(--border-color)', background: 'var(--bg-main)',
        color: 'var(--text-main)', outline: 'none'
    },
    membersBox: {
        maxHeight: '220px', overflowY: 'auto', border: '1px solid var(--border-color)',
        borderRadius: '8px', padding: '5px', background: 'var(--bg-main)',
        display: 'flex', flexDirection: 'column'
    },
    memberRow: {
        display: 'flex', alignItems: 'center', padding: '10px',
        cursor: 'pointer', userSelect: 'none', borderRadius: '6px',
        transition: 'all 0.2s', marginBottom: '2px'
    },
    actions: {
        display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '20px'
    },
    cancelBtn: {
        padding: '10px 20px', borderRadius: '8px', border: 'none',
        cursor: 'pointer', background: 'rgba(0,0,0,0.1)', color: 'var(--text-main)'
    },
    createBtn: {
        padding: '10px 20px', borderRadius: '8px', border: 'none',
        cursor: 'pointer', background: '#0084ff', color: 'white', fontWeight: 'bold'
    },
    error: {
        background: 'rgba(255, 77, 77, 0.1)', color: '#ff4d4d',
        padding: '10px', borderRadius: '6px', marginBottom: '15px', fontSize: '0.85rem'
    }
};