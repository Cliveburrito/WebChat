export default function RightSidebar({ users, currentUser, onOpenDirectChat }) {
    return (
        <aside className="sidebar right">
            <div className="sidebar-section">
                <h4 className="sidebar-title">All Users</h4>
                <div className="sidebar-list">
                    {users.filter(u => u.username !== currentUser).map(u => (
                        <div
                            key={u.id}
                            className="item"
                            onClick={() => onOpenDirectChat(u.id)}
                            style={{ cursor: "pointer" }}
                        >
                            <strong>{u.username}</strong>
                            <small>{u.email}</small>
                        </div>
                    ))}
                </div>
            </div>
        </aside>
    );
}