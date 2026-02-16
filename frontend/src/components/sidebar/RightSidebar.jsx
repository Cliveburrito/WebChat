import Avatar from "../common/Avatar"; // Το ".." βγαίνει από το sidebar και μπαίνει στο common

export default function RightSidebar({ users, currentUser, onOpenDirectChat, onlineUsers = [] }) {
    return (
        <aside className="sidebar right">
            <div className="sidebar-section">
                <div className="sidebar-header-row">
                    <h4 className="sidebar-title">Global Directory</h4>
                    <span className="sidebar-subtitle">{users.filter(u => u.username !== currentUser).length} users</span>
                </div>
                <div className="sidebar-list">
                    {users.filter(u => u.username !== currentUser).map(u => {
                        const isOnline = onlineUsers.includes(u.username);
                        return (
                            <div key={u.id} className="chat-item" onClick={() => onOpenDirectChat(u.id)}>
                                <Avatar name={u.username} isOnline={isOnline} size={40} />
                                <div className="chat-item-info">
                                    <span className="chat-item-name">{u.username}</span>
                                    <small className="preview-text">{u.email}</small>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>
        </aside>
    );
}