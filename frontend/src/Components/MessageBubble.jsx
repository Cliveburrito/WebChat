export default function MessageBubble({ msg, currentUser }) {
    const isMe = msg.senderUsername === currentUser; //
    return (
        <div className={`message-wrapper ${isMe ? 'me' : 'other'}`}>
            <div className="message-bubble">
                {!isMe && <span className="message-sender">{msg.senderUsername}</span>}
                <div className="message-content">{msg.content}</div> {/* */}
                <div className="message-time">
                    {msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'}) : ''}
                </div>
            </div>
        </div>
    );
}