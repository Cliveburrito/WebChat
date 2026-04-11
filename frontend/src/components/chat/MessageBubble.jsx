import { useCallback, useEffect, useState } from "react";
import { instrumentedFetch } from "../../api/apiJson";
import "./MessageBubble.css";

const REACTION_EMOJIS = ["👍", "❤️", "😂", "😮", "😢", "🔥"];

// --- Υπο-component για τα Ticks ---
const MessageStatus = ({ msg, chatWatermarks, currentUserId }) => {
    // 1. Έλεγχος για Temp ID (Pending / Uploading)
    // Αν το ID δεν είναι αριθμός ή ξεκινάει με "temp-", δείχνουμε ρολόι
    if (!msg.id || String(msg.id).startsWith("temp-") || String(msg.id).startsWith("evt-")) {
        return <span className="status-icon pending" aria-label="pending">⏳</span>;
    }

    // 2. Υπολογισμός Max Read/Delivered από τους άλλους χρήστες
    // Ψάχνουμε σε όλα τα μέλη (εκτός από εμάς) ποιο είναι το μεγαλύτερο ID που έχουν δει
    let maxReadId = 0;
    let maxDeliveredId = 0;

    Object.entries(chatWatermarks).forEach(([userId, status]) => {
        if (String(userId) === String(currentUserId)) return;
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

export default function MessageBubble({ msg, currentUser, currentUserId, chatWatermarks, token, onReply, onOpenPreview }) {
    const isMe = msg.senderUsername === currentUser;
    const attachments = Array.isArray(msg.attachments) ? msg.attachments : [];
    const reactions = Array.isArray(msg.reactions) ? msg.reactions : [];
    const messageDomId = msg.id ? `message-${msg.id}` : undefined;
    const canReact = msg.id && !String(msg.id).startsWith("temp-") && !String(msg.id).startsWith("evt-");
    const [attachmentUrls, setAttachmentUrls] = useState({});
    const [isReactionPickerOpen, setIsReactionPickerOpen] = useState(false);

    const toggleReaction = useCallback(async (emoji) => {
        if (!canReact) return;
        setIsReactionPickerOpen(false);

        try {
            const response = await instrumentedFetch(`/api/messages/${encodeURIComponent(msg.id)}/reactions`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify({ emoji }),
            });

            if (!response.ok) {
                throw new Error(`Reaction failed for message ${msg.id}`);
            }
        } catch (error) {
            console.error("Message reaction failed:", error);
        }
    }, [canReact, msg.id, token]);

    const downloadAttachment = useCallback(async (att) => {
        if (att.pending || att.localUrl) return;
        try {
            const response = await instrumentedFetch(`/api/files/download/${encodeURIComponent(att.storageName)}`, {
                headers: { Authorization: `Bearer ${token}` },
            });
            if (!response.ok) {
                throw new Error(`Download failed for ${att.storageName}`);
            }

            const blob = await response.blob();
            const objectUrl = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.href = objectUrl;
            link.download = att.originalName || "attachment";
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(objectUrl);
        } catch (error) {
            console.error("Attachment download failed:", error);
        }
    }, [token]);

    useEffect(() => {
        const previewableAttachments = attachments.filter((att) => (att.contentType?.startsWith("image/") || att.contentType?.startsWith("video/")) && !att.pending);
        if (!previewableAttachments.length || !token) {
            setAttachmentUrls({});
            return undefined;
        }

        let cancelled = false;
        const createdUrls = [];

        Promise.all(previewableAttachments.map(async (att) => {
            if (att.localUrl) {
                return [att.storageName || att.id || att.originalName, att.localUrl];
            }

            const previewStorageName = att.thumbnailUrl || att.storageName;
            const response = await instrumentedFetch(
                `/api/files/preview/${encodeURIComponent(previewStorageName)}?conversationId=${encodeURIComponent(att.conversationId ?? msg.conversationId)}`,
                {
                headers: { Authorization: `Bearer ${token}` },
                }
            );

            if (!response.ok) {
                throw new Error(`Preview fetch failed for ${att.storageName}`);
            }

            const blob = await response.blob();
            const objectUrl = URL.createObjectURL(blob);
            createdUrls.push(objectUrl);
            return [att.storageName, objectUrl];
        }))
            .then((entries) => {
                if (!cancelled) {
                    setAttachmentUrls(Object.fromEntries(entries));
                }
            })
            .catch((error) => {
                if (!cancelled) {
                    console.error("Message attachment preview fetch failed:", error);
                    setAttachmentUrls({});
                }
            });

        return () => {
            cancelled = true;
            createdUrls.forEach((url) => URL.revokeObjectURL(url));
        };
    }, [attachments, token]);

    return (
        <div id={messageDomId} className={`message-wrapper ${isMe ? 'me' : 'other'}`}>
            <div className="message-bubble">
                {!isMe && <span className="message-sender">{msg.senderUsername}</span>}

                {(msg.replyToMessageId || msg.replyToContent) && (
                    <div className="reply-reference">
                        <span className="reply-reference-sender">
                            {msg.replyToSenderUsername || "Replied message"}
                        </span>
                        <span className="reply-reference-text">
                            {msg.replyToContent || `Message #${msg.replyToMessageId}`}
                        </span>
                    </div>
                )}

                {/* Attachments Section */}
                {attachments.length > 0 && (
                    <div className="attachments-list">
                        {attachments.map((att, idx) => {
                            const isImage = att.contentType?.startsWith("image/") || att.localUrl;
                            const isVideo = att.contentType?.startsWith("video/");
                            const attachmentKey = att.storageName || att.id || att.originalName;
                            const fileUrl = attachmentUrls[attachmentKey] || att.localUrl;
                            const displayName = att.originalName || att.storageName || "Attachment";
                            const canDownload = Boolean(att.storageName && token && !att.pending);

                            return (
                                <div key={idx} className="attachment-item">
                                    {isImage || isVideo ? (
                                        <button
                                            type="button"
                                            className="attachment-preview-btn"
                                            onClick={() => onOpenPreview?.(attachments, idx)}
                                            disabled={att.pending || !fileUrl}
                                        >
                                            {isVideo ? (
                                                <div className="attachment-video-shell">
                                                    <video
                                                        src={fileUrl}
                                                        className={`attachment-img ${att.pending ? 'pending' : ''}`}
                                                        muted
                                                        playsInline
                                                    />
                                                    <span className="attachment-play">▶</span>
                                                </div>
                                            ) : (
                                                <img
                                                    src={fileUrl}
                                                    alt="attachment"
                                                    className={`attachment-img ${att.pending ? 'pending' : ''}`}
                                                />
                                            )}
                                        </button>
                                    ) : (
                                        <button
                                            type="button"
                                            className="attachment-pill"
                                            onClick={() => downloadAttachment(att)}
                                            disabled={!canDownload}
                                            title={displayName}
                                        >
                                            📎 {displayName} {att.pending && "(uploading...)"}
                                        </button>
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
                                <MessageStatus
                                    msg={msg}
                                    chatWatermarks={chatWatermarks || {}}
                                    currentUserId={currentUserId}
                                />
                            </span>
                        )}
                    </div>
                </div>

                <button
                    type="button"
                    className="message-reply-btn"
                    onClick={() => onReply?.(msg)}
                    aria-label="Reply to message"
                    title="Reply"
                >
                    <span aria-hidden="true">↩</span>
                    <span className="message-reply-text">Reply</span>
                </button>

                {reactions.length > 0 && (
                    <div className="message-reactions">
                        {reactions.map((reaction) => (
                            <button
                                key={reaction.emoji}
                                type="button"
                                className={`message-reaction-chip ${reaction.reactedByMe ? "mine" : ""}`}
                                onClick={() => toggleReaction(reaction.emoji)}
                                disabled={!canReact}
                                title={`${reaction.count} reaction${reaction.count === 1 ? "" : "s"}`}
                            >
                                <span>{reaction.emoji}</span>
                                <span>{reaction.count}</span>
                            </button>
                        ))}
                    </div>
                )}

                {canReact && (
                    <div className="reaction-picker-shell">
                        <button
                            type="button"
                            className="reaction-picker-toggle"
                            onClick={() => setIsReactionPickerOpen((open) => !open)}
                            aria-label="React to message"
                            title="React"
                        >
                            <span aria-hidden="true">☺</span>
                        </button>
                        {isReactionPickerOpen && (
                            <div className="reaction-picker">
                                {REACTION_EMOJIS.map((emoji) => (
                                    <button
                                        key={emoji}
                                        type="button"
                                        className="reaction-picker-option"
                                        onClick={() => toggleReaction(emoji)}
                                    >
                                        {emoji}
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}
