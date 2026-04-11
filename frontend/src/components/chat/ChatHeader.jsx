import Avatar from "../common/Avatar";
import "./ChatHeader.css";

// src/chat/ChatHeader.jsx
export default function ChatHeader({ activeChat, onToggleMute, onToggleDetails, isDetailsOpen }) {
    const chatName = activeChat.displayName || activeChat.name;
    const chatId = activeChat.conversationId || activeChat.id;

    return (
        <header className="chat-area-header">
            <div className="header-info" onClick={onToggleDetails}> {/* Κλικ στο avatar/όνομα ανοίγει επίσης το info */}
                <Avatar name={chatName} size={40} />
                <div className="text-info">
                    <strong className="chat-title">{chatName}</strong>
                    <span className="chat-status">{activeChat.muted ? "Muted" : "Tap for info"}</span>
                </div>
            </div>

            <div className="header-actions">
                <button
                    className={`mute-btn ${activeChat.muted ? 'active' : ''}`}
                    onClick={() => onToggleMute(chatId, activeChat.muted)}
                >
                    {activeChat.muted ? "🔕" : "🔔"}
                </button>

                {/* Νέο κουμπί για το Sidebar */}
                <button
                    className={`info-btn ${isDetailsOpen ? 'active' : ''}`}
                    onClick={onToggleDetails}
                >
                    ⓘ
                </button>
            </div>
        </header>
    );
}
