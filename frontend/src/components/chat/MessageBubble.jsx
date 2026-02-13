export default function MessageBubble({ msg, currentUser }) {
    const isMe = msg.senderUsername === currentUser;
    // Σιγουρευόμαστε ότι έχουμε array, ακόμα κι αν το backend στείλει null
    const attachments = Array.isArray(msg.attachments) ? msg.attachments : [];

    return (
        <div className={`message-wrapper ${isMe ? 'me' : 'other'}`}>
            <div className="message-bubble">
                {!isMe && <span className="message-sender">{msg.senderUsername}</span>}

                {/* Εμφάνιση Attachments */}
                {attachments.length > 0 && (
                    <div className="attachments-list" style={{ display: 'flex', flexDirection: 'column', gap: '5px' }}>
                        {attachments.map((att, idx) => {
                            // Έλεγχος αν είναι εικόνα (από το contentType ή το localUrl)
                            const isImage = att.contentType?.startsWith("image/") || att.localUrl;

                            // URL: Χρησιμοποιεί το storageName αν υπάρχει (ιστορικό) ή το localUrl (αν ανεβαίνει τώρα)
                            const fileUrl = att.storageName
                                ? `/api/files/download/${encodeURIComponent(att.storageName)}`
                                : att.localUrl;

                            if (!fileUrl && !att.pending) return null;

                            return (
                                <div key={idx} className="attachment-item">
                                    {isImage ? (
                                        <img
                                            src={fileUrl}
                                            alt="upload"
                                            style={{ maxWidth: '200px', borderRadius: '8px', opacity: att.pending ? 0.5 : 1 }}
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

                {msg.content && <div className="message-content">{msg.content}</div>}

                <div className="message-time">
                    {msg.createdAt || msg.sentAt
                        ? new Date(msg.createdAt || msg.sentAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                        : ''}
                </div>
            </div>
        </div>
    );
}