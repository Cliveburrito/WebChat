import Avatar from "../common/Avatar";
import "./ChatHeader.css";

export default function ChatHeader({ activeChat, onToggleMute }) {
    const chatId = activeChat?.conversationId || activeChat?.id;
    const chatName = activeChat.displayName || activeChat.name;

    return (
        <header className="chat-area-header">
            <div className="header-info">
                <Avatar name={chatName} size={40} />
                <div className="text-info">
                    <strong className="chat-title">{chatName}</strong>
                    <span className="chat-status">
                        {activeChat.muted ? "Muted" : ""}
                    </span>
                </div>
            </div>

            <div className="header-actions">
                <button
                    className={`mute-btn ${activeChat.muted ? 'active' : ''}`}
                    onClick={() => onToggleMute(chatId, activeChat.muted)}
                    title={activeChat.muted ? "Unmute" : "Mute"}
                >
                    {activeChat.muted ? "🔕" : "🔔"}
                </button>
            </div>
        </header>
    );
}