import { useMemo, useState } from "react";
import PresenceDot from "../presence/PresenceDot";

export default function CreateGroupModal({
                                             open,
                                             users,
                                             currentUser,
                                             token,
                                             onlineUsers,
                                             onClose,
                                             onGroupCreated,
                                         }) {
    const [groupName, setGroupName] = useState("");
    const [selectedIds, setSelectedIds] = useState([]);
    const [error, setError] = useState("");
    const [isCreating, setIsCreating] = useState(false);

    const contacts = useMemo(() => {
        if (!users) return [];
        return users.filter((u) => u.username !== currentUser);
    }, [users, currentUser]);

    const toggleUser = (id) => {
        setSelectedIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
    };

    const reset = () => {
        setGroupName("");
        setSelectedIds([]);
        setError("");
        setIsCreating(false);
    };

    const close = () => {
        reset();
        onClose?.();
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
                headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
                body: JSON.stringify({ groupName: name, memberIds: selectedIds }),
            });

            if (!res.ok) throw new Error("Αποτυχία δημιουργίας group.");
            const created = await res.json();
            onGroupCreated?.(created);
            close();
        } catch (e) {
            setError(e.message);
        } finally {
            setIsCreating(false);
        }
    };

    if (!open) return null;

    return (
        <div style={styles.backdrop} onClick={close}>
            <div style={styles.modal} onClick={(e) => e.stopPropagation()}>
                <div style={styles.modalHeader}>
                    <strong>Create Group Chat</strong>
                    <button style={styles.xBtn} onClick={close}>✕</button>
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
                                            background: isSelected ? "rgba(0, 132, 255, 0.1)" : "transparent",
                                        }}
                                    >
                                        <input
                                            type="checkbox"
                                            checked={isSelected}
                                            onChange={() => toggleUser(u.id)}
                                            style={{ cursor: "pointer", width: "16px", height: "16px" }}
                                        />
                                        <PresenceDot online={isOnline} size={8} className="modal-dot" />
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
                    <button onClick={close} style={styles.cancelBtn}>Cancel</button>
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
    );
}

const styles = {
    backdrop: {
        position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
        backgroundColor: 'rgba(0,0,0,0.7)', display: 'flex',
        alignItems: 'center', justifyContent: 'center', zIndex: 2000,
    },
    modal: {
        backgroundColor: 'var(--bg-sidebar)', padding: '24px',
        borderRadius: '12px', width: '420px', maxWidth: '95%',
        boxShadow: '0 12px 40px rgba(0,0,0,0.4)', color: 'var(--text-main)',
        border: '1px solid var(--border-color)',
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
        display: 'flex', flexDirection: 'column', gap: 2
    },
    memberRow: {
        display: 'flex', alignItems: 'center', padding: '10px',
        cursor: 'pointer', userSelect: 'none', borderRadius: '6px',
        transition: 'all 0.2s', marginBottom: '2px', gap: 10
    },
    actions: { display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '20px' },
    cancelBtn: {
        padding: '10px 20px', borderRadius: '8px', border: 'none',
        cursor: 'pointer', background: 'rgba(0,0,0,0.1)', color: 'var(--text-main)'
    },
    createBtn: {
        padding: '10px 20px', borderRadius: '8px', border: 'none',
        cursor: 'pointer', background: 'var(--primary-blue)', color: 'white', fontWeight: 'bold'
    },
    error: {
        background: 'rgba(255, 77, 77, 0.1)', color: '#ff4d4d',
        padding: '10px', borderRadius: '6px', marginBottom: '15px', fontSize: '0.85rem'
    }
};
