import Avatar from "../common/Avatar"; // Το ".." βγαίνει από το sidebar και μπαίνει στο common
import { formatLastSeen } from "../../utils/lastSeen";

export default function RightSidebar({ users, currentUser, onOpenDirectChat, onlineUsers = [], isMobileOpen = false, onRequestClose }) {
    return (
        <aside className={`sidebar right ${isMobileOpen ? "mobile-open" : ""}`}>
            <div className="sidebar-mobile-topbar">
                <strong>Directory</strong>
                <button type="button" onClick={onRequestClose} aria-label="Close directory">x</button>
            </div>
            <div className="sidebar-section">
                <div className="sidebar-header-row">
                    <h4 className="sidebar-title">Global Directory</h4>
                    <span className="sidebar-subtitle">{users.filter(u => u.username !== currentUser).length} users</span>
                </div>
                <div className="sidebar-list">
                    {users.filter(u => u.username !== currentUser).map(u => {
                        const isOnline = onlineUsers.includes(u.username);
                        const presenceText = isOnline ? "Online" : formatLastSeen(u.lastSeenAt);
                        const displayName = u.displayName || u.username;
                        return (
                            <div key={u.id} className="chat-item" onClick={() => onOpenDirectChat(u.id)}>
                                <Avatar name={displayName} avatarUrl={u.avatarUrl} isOnline={isOnline} size={40} />
                                <div className="chat-item-info">
                                    <span className="chat-item-name">{displayName}</span>
                                    <small className="preview-text">{presenceText || u.email}</small>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>
        </aside>
    );
}
