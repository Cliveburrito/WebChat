import "./MessageBubble.css";

// --- Υπο-component για τα Ticks ---
const MessageStatus = ({ msg, chatWatermarks }) => {
    // 1. Έλεγχος για Temp ID (Pending / Uploading)
    // Αν το ID δεν είναι αριθμός ή ξεκινάει με "temp-", δείχνουμε ρολόι
    if (!msg.id || String(msg.id).startsWith("temp-") || String(msg.id).startsWith("evt-")) {
        return <span className="status-icon pending" aria-label="pending">⏳</span>;
    }

    // 2. Υπολογισμός Max Read/Delivered από τους άλλους χρήστες
    // Ψάχνουμε σε όλα τα μέλη (εκτός από εμάς) ποιο είναι το μεγαλύτερο ID που έχουν δει
    let maxReadId = 0;
    let maxDeliveredId = 0;

    Object.values(chatWatermarks).forEach(status => {
        if (status.lastReadId > maxReadId) maxReadId = status.lastReadId;
        if (status.lastDeliveredId > maxDeliveredId) maxDeliveredId = status.lastDeliveredId;
    });

    // 3. Logic Comparison
    if (msg.id <= maxReadId) {
        return <span className="status-icon read" aria-label="read">✓✓</span>; // Μπλε Διπλό
    }
    if (msg.id <= maxDeliveredId) {
        return <span className="status-icon delivered" aria-label="delivered">✓✓</span>; // Γκρι Διπλό
    }

    // 4. Default: Sent to Server (Μονό Γκρι)
    return <span className="status-icon sent" aria-label="sent">✓</span>;
};

export default function MessageBubble({ msg, currentUser, chatWatermarks }) {
    const isMe = msg.senderUsername === currentUser;
    const attachments = Array.isArray(msg.attachments) ? msg.attachments : [];

    return (
        <div className={`message-wrapper ${isMe ? 'me' : 'other'}`}>
            <div className="message-bubble">
                {!isMe && <span className="message-sender">{msg.senderUsername}</span>}

                {/* Attachments Section */}
                {attachments.length > 0 && (
                    <div className="attachments-list">
                        {attachments.map((att, idx) => {
                            const isImage = att.contentType?.startsWith("image/") || att.localUrl;
                            const fileUrl = att.storageName
                                ? `/api/files/download/${encodeURIComponent(att.storageName)}`
                                : att.localUrl;

                            if (!fileUrl && !att.pending) return null;

                            return (
                                <div key={idx} className="attachment-item">
                                    {isImage ? (
                                        <img
                                            src={fileUrl}
                                            alt="attachment"
                                            className={`attachment-img ${att.pending ? 'pending' : ''}`}
                                        />
                                    ) : (
                                        <a href={fileUrl} target="_blank" rel="noreferrer" className="attachment-pill">
                                            📎 {att.originalName || "File"} {att.pending && "(uploading...)"}
                                        </a>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                )}

                {/* Message Content & Meta */}
                <div className="message-row">
                    {msg.content && <span className="message-text">{msg.content}</span>}

                    <div className="message-meta">
                        <span className="message-time">
                            {msg.createdAt || msg.sentAt
                                ? new Date(msg.createdAt || msg.sentAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                                : ''}
                        </span>

                        {/* Εμφανίζουμε τα Ticks ΜΟΝΟ στα δικά μας μηνύματα */}
                        {isMe && (
                            <span className="message-ticks">
                                <MessageStatus msg={msg} chatWatermarks={chatWatermarks || {}} />
                            </span>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}