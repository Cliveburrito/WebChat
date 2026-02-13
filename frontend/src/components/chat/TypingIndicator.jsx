export default function TypingIndicator({ typingUser }) {
    if (!typingUser) return null;

    return (
        <div className="typing-indicator">
            <span>{typingUser} is typing</span>
            <div className="typing-dots">
                <span></span><span></span><span></span>
            </div>
        </div>
    );
}
