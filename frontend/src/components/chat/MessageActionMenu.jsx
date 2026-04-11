import { useState } from "react";

export default function MessageActionMenu({ canEditDelete, onReply, onEdit, onDelete }) {
    const [isOpen, setIsOpen] = useState(false);
    const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);

    return (
        <div className="message-action-menu-shell">
            <button
                type="button"
                className="message-side-action"
                onClick={() => setIsOpen((open) => !open)}
                aria-label="Message actions"
                title="Message actions"
            >
                <span aria-hidden="true">...</span>
            </button>
            {isOpen && (
                <div className="message-action-menu">
                    <button
                        type="button"
                        onClick={() => {
                            setIsOpen(false);
                            onReply?.();
                        }}
                    >
                        Reply
                    </button>
                    {canEditDelete && (
                        <>
                            <button
                                type="button"
                                onClick={() => {
                                    setIsOpen(false);
                                    onEdit?.();
                                }}
                            >
                                Edit
                            </button>
                            <button
                                type="button"
                                className="danger"
                                onClick={() => {
                                    setIsOpen(false);
                                    setConfirmDeleteOpen(true);
                                }}
                            >
                                Delete
                            </button>
                        </>
                    )}
                </div>
            )}

            {confirmDeleteOpen && (
                <div className="message-confirm-overlay" role="dialog" aria-modal="true" aria-label="Delete message">
                    <div className="message-confirm">
                        <div>
                            <strong>Delete message?</strong>
                            <p>This will remove the message text and attachments for everyone.</p>
                        </div>
                        <div className="message-confirm-actions">
                            <button type="button" onClick={() => setConfirmDeleteOpen(false)}>
                                Cancel
                            </button>
                            <button
                                type="button"
                                className="danger"
                                onClick={() => {
                                    setConfirmDeleteOpen(false);
                                    onDelete?.();
                                }}
                            >
                                Delete
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
