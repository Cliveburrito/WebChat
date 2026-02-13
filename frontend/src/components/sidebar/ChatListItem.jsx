import PresenceDot from "../presence/PresenceDot";

export default function ChatListItem({ chat, activeChat, onSelectChat, onlineUsers }) {
    const chatTitle = chat.displayName || chat.name || "Unknown Chat";
    const cid = chat.conversationId || chat.id;
    const isActive = activeChat?.conversationId === cid || activeChat?.id === cid;

    const isDirect = chat.name && !chat.name.includes(", ");
    const isOnline = isDirect && onlineUsers.includes(chat.name);

    return (
        <div
            className={`item ${isActive ? "active" : ""} ${chat.unreadCount > 0 ? "unread" : ""}`}
            onClick={() => onSelectChat(chat)}
            style={{ display: "flex", justifyContent: "space-between", gap: 10, alignItems: "center" }}
        >
            <div style={{ display: "flex", alignItems: "center", gap: 10, minWidth: 0, flex: 1 }}>
                {isDirect && <PresenceDot online={isOnline} />}

                <div style={{ minWidth: 0, flex: 1 }}>
                    <strong style={{ display: "block", textOverflow: "ellipsis", overflow: "hidden", whiteSpace: "nowrap" }}>
                        {chatTitle}
                    </strong>
                    <small className="last-msg-text">
                        {chat.lastContent || chat.lastMessage || "No messages yet"}
                    </small>
                </div>
            </div>

            <div style={{ display: "flex", alignItems: "center", gap: 8, justifyContent: "flex-end" }}>
                {chat.muted && <span title="Muted" style={{ fontSize: "0.9rem", opacity: 0.6 }}>🔕</span>}
                {chat.unreadCount > 0 && <span className="unread-badge">{chat.unreadCount}</span>}
            </div>
        </div>
    );
}
