import Avatar from "../common/Avatar";
import Icon from "../common/Icon";
import { formatLastSeen } from "../../utils/lastSeen";
import "./ChatListItem.css";

const SidebarStatus = ({ chat, watermarks, currentUserId }) => {
    if (chat.lastSenderId !== currentUserId || !chat.lastMessageId) return null;

    const cid = chat.conversationId || chat.id;
    const chatWatermarks = watermarks?.[cid] || {};
    let maxRead = 0;
    let maxDelivered = 0;

    Object.entries(chatWatermarks).forEach(([userId, status]) => {
        if (String(userId) === String(currentUserId)) return;
        if (status.lastReadId > maxRead) maxRead = status.lastReadId;
        if (status.lastDeliveredId > maxDelivered) maxDelivered = status.lastDeliveredId;
    });

    if (chat.lastMessageId <= maxRead) return <span className="status-icon read" aria-label="read">✓✓</span>;
    if (chat.lastMessageId <= maxDelivered) return <span className="status-icon delivered" aria-label="delivered">✓✓</span>;
    return <span className="status-icon sent" aria-label="sent">✓</span>;
};

export default function ChatListItem({ chat, activeChat, onSelectChat, onlineUsers, watermarks, currentUserId }) {
    const chatTitle = chat.displayName || chat.name || "Unknown Chat";
    const cid = chat.conversationId || chat.id;
    const isActive = (activeChat?.conversationId || activeChat?.id) === cid;

    const isDirect = chat.isGroup === false;
    const isOnline = isDirect && onlineUsers.includes(chatTitle);
    const directPresenceText = isDirect
        ? (isOnline ? "Online" : formatLastSeen(chat.directParticipantLastSeenAt))
        : "";

    const unreadCount = Number(chat.unreadCount ?? chat.unread_count ?? 0);

    const timeDisplay = chat.lastMessageAt
        ? new Date(chat.lastMessageAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        : "";

    return (
        <div className={`chat-item ${isActive ? "active" : ""}`} onClick={() => onSelectChat(chat)}>
            {/* Column 1: Avatar */}
            <Avatar name={chatTitle} avatarUrl={chat.avatarUrl} isOnline={isDirect ? isOnline : undefined} />

            {/* Column 2: Info (Name & Preview) */}
            <div className="chat-item-info">
                <div className="chat-item-header">
                    <span className="chat-item-name">{chatTitle}</span>
                    <span className={`chat-item-time ${unreadCount > 0 ? "highlight" : ""}`}>
                        {directPresenceText || timeDisplay}
                    </span>
                </div>

                <div className="chat-item-footer">
                    <div className="chat-item-preview">
                        <SidebarStatus chat={chat} watermarks={watermarks} currentUserId={currentUserId} />
                        <span className="preview-text">
                            {chat.lastSenderId === currentUserId && "You: "}
                            {chat.lastContent || "No messages yet"}
                        </span>
                    </div>

                    <div className="chat-item-badges">
                        {chat.muted && (
                            <span className="mute-icon" title="Muted">
                                <Icon name="bellOff" size={14} />
                            </span>
                        )}
                        {unreadCount > 0 && (
                            <span className="unread-badge">{unreadCount}</span>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
