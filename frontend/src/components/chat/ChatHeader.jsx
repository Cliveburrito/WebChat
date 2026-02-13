export default function ChatHeader({ activeChat, onToggleMute }) {
    const chatId = activeChat?.conversationId || activeChat?.id;

    return (
        <header className="chat-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 20px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <strong>{activeChat.displayName || activeChat.name}</strong>
                {activeChat.muted && (
                    <span style={{ fontSize: '0.7rem', backgroundColor: '#eee', color: '#555', padding: '2px 6px', borderRadius: '4px' }}>
            MUTED
          </span>
                )}
            </div>

            <button
                onClick={() => onToggleMute(chatId, activeChat.muted)}
                style={{
                    background: 'none',
                    border: 'none',
                    cursor: 'pointer',
                    fontSize: '1.4rem',
                    transition: 'all 0.2s ease',
                    opacity: activeChat.muted ? 0.5 : 1,
                    filter: activeChat.muted ? 'grayscale(100%)' : 'none'
                }}
                title={activeChat.muted ? "Click to Unmute" : "Click to Mute"}
            >
                {activeChat.muted ? "🔕" : "🔔"}
            </button>

        </header>
    );
}
