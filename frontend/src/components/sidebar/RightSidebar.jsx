import PresenceDot from "../presence/PresenceDot";

export default function RightSidebar({ users, currentUser, onOpenDirectChat, onlineUsers = [] }) {
    return (
        <aside className="sidebar right">
            <div className="sidebar-section">
                <h4 className="sidebar-title">All Users</h4>
                <div className="sidebar-list">
                    {users.filter(u => u.username !== currentUser).map(u => {
                        const isOnline = onlineUsers.includes(u.username);
                        return (
                            <div
                                key={u.id}
                                className="item"
                                onClick={() => onOpenDirectChat(u.id)}
                                style={{ cursor: "pointer", display: "flex", alignItems: "center", gap: "10px" }}
                            >
                                <PresenceDot online={isOnline} />
                                <div style={{ display: "flex", flexDirection: "column" }}>
                                    <strong>{u.username}</strong>
                                    <small>{u.email}</small>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>
        </aside>
    );
}
