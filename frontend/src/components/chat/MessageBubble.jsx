import { useCallback, useEffect, useState } from "react";
import { instrumentedFetch } from "../../api/apiJson";
import MessageActionMenu from "./MessageActionMenu";
import ReactionPicker from "./ReactionPicker";
import "./MessageBubble.css";

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

export default function MessageBubble({ msg, currentUser, currentUserId, chatWatermarks, token, onReply, onEdit, onDelete, onOpenPreview }) {
    const isMe = msg.senderUsername === currentUser;
    const isDeleted = Boolean(msg.deleted || msg.deletedAt);
    const attachments = Array.isArray(msg.attachments) ? msg.attachments : [];
    const reactions = Array.isArray(msg.reactions) ? msg.reactions : [];
    const messageDomId = msg.id ? `message-${msg.id}` : undefined;
    const canReact = msg.id && !isDeleted && !String(msg.id).startsWith("temp-") && !String(msg.id).startsWith("evt-");
    const canEditDelete = canReact && isMe;
    const lifecycleError = msg.editFailed
        ? "Edit failed. Your original message was restored."
        : msg.deleteFailed
            ? "Delete failed. The message is still visible."
            : "";
    const [attachmentUrls, setAttachmentUrls] = useState({});
    const [isEditing, setIsEditing] = useState(false);
    const [editText, setEditText] = useState(msg.content || "");

    useEffect(() => {
        if (!isEditing) setEditText(msg.content || "");
    }, [isEditing, msg.content]);

    const toggleReaction = useCallback(async (emoji) => {
        if (!canReact) return;

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

    const saveEdit = useCallback(async () => {
        const trimmed = editText.trim();
        if (!trimmed) return;
        const ok = await onEdit?.(msg, trimmed);
        if (ok !== false) {
            setIsEditing(false);
        }
    }, [editText, msg, onEdit]);

    const startEdit = useCallback(() => {
        setEditText(msg.content || "");
        setIsEditing(true);
    }, [msg.content]);

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
                {!isDeleted && attachments.length > 0 && (
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
                    {isEditing ? (
                        <div className="message-edit-form">
                            <textarea
                                className="message-edit-input"
                                value={editText}
                                maxLength={500}
                                rows={2}
                                onChange={(event) => setEditText(event.target.value)}
                                onKeyDown={(event) => {
                                    if (event.key === "Enter" && !event.shiftKey) {
                                        event.preventDefault();
                                        saveEdit();
                                    }
                                    if (event.key === "Escape") {
                                        setEditText(msg.content || "");
                                        setIsEditing(false);
                                    }
                                }}
                                autoFocus
                            />
                            <div className="message-edit-actions">
                                <button type="button" onClick={saveEdit} disabled={!editText.trim()}>Save</button>
                                <button
                                    type="button"
                                    onClick={() => {
                                        setEditText(msg.content || "");
                                        setIsEditing(false);
                                    }}
                                >
                                    Cancel
                                </button>
                            </div>
                        </div>
                    ) : isDeleted ? (
                        <span className="message-text deleted">Message deleted</span>
                    ) : (
                        msg.content && <span className="message-text">{msg.content}</span>
                    )}

                    {lifecycleError && (
                        <div className="message-error" role="status">
                            {lifecycleError}
                        </div>
                    )}

                    <div className="message-meta">
                        <span className="message-time">
                            {msg.createdAt || msg.sentAt
                                ? new Date(msg.createdAt || msg.sentAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                                : ''}
                        </span>
                        {!isDeleted && msg.editedAt && <span className="message-edited">edited</span>}

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

                {!isDeleted && !isEditing && canReact && (
                    <div className="message-side-actions">
                        <ReactionPicker onReact={toggleReaction} />
                        <MessageActionMenu
                            canEditDelete={canEditDelete}
                            onReply={() => onReply?.(msg)}
                            onEdit={startEdit}
                            onDelete={() => onDelete?.(msg)}
                        />
                    </div>
                )}

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

            </div>
        </div>
    );
}
