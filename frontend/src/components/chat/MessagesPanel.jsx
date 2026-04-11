import React from "react";
import MessageBubble from "./MessageBubble";
import TypingIndicator from "./TypingIndicator";
import "./MessagesPanel.css";

export default function MessagesPanel({
                                          scrollRef,
                                          onScroll,
                                          hasMore,
                                          isLoadingMessages,
                                          onLoadMore,
                                          messages,
                                          currentUser,
                                          currentUserId,
                                          token,
                                          typingUser,
                                          watermarks,
                                          activeChatId,
                                          activeChat,
                                          selfWatermark,
                                          unreadBoundaryId,
                                          onReply,
                                          onEdit,
                                          onDelete,
                                          onOpenPreview
                                      }) {
    const currentChatWatermarks = watermarks?.[activeChatId] || {};
    const fallbackBoundaryId = Number(selfWatermark?.lastReadId ?? activeChat?.myLastReadMessageId ?? 0);
    const markerBoundaryId = unreadBoundaryId ?? (
        Number(activeChat?.unreadCount ?? activeChat?.unread_count ?? 0) > 0 && Number.isFinite(fallbackBoundaryId)
            ? fallbackBoundaryId
            : null
    );
    const showUnreadMarker = markerBoundaryId !== null;

    // --- Logic για αυτόματο Load More στο Scroll ---
    const handleScrollInternal = (e) => {
        onScroll(e); // Pass the event up!
    };

    // --- Logic για Date Dividers (Today, Yesterday, κλπ) ---
    const renderWithDateDividers = () => {
        const elements = [];
        let lastDateString = null;

        messages.forEach((m, i) => {
            const dateObj = new Date(m.createdAt || m.sentAt);
            const dateString = dateObj.toLocaleDateString([], {
                day: 'numeric', month: 'long', year: 'numeric'
            });

            if (dateString !== lastDateString) {
                // Υπολογισμός Today/Yesterday
                const today = new Date().toLocaleDateString([], { day: 'numeric', month: 'long', year: 'numeric' });
                const yesterday = new Date();
                yesterday.setDate(yesterday.getDate() - 1);
                const yesterdayString = yesterday.toLocaleDateString([], { day: 'numeric', month: 'long', year: 'numeric' });

                let displayDate = dateString;
                if (dateString === today) displayDate = "Today";
                else if (dateString === yesterdayString) displayDate = "Yesterday";

                elements.push(
                    <div key={`date-${dateString}`} className="date-divider">
                        <span>{displayDate}</span>
                    </div>
                );
                lastDateString = dateString;
            }

            if (
                showUnreadMarker
                && Number(m.id) > markerBoundaryId
                && m.senderUsername !== currentUser
                && !elements.some((element) => element.key === "unread-marker")
            ) {
                elements.push(
                    <div key="unread-marker" className="unread-separator">
                        <span>Unread messages</span>
                    </div>
                );
            }

            elements.push(
                <MessageBubble
                    key={m.id ?? `temp-${i}`}
                    msg={m}
                    currentUser={currentUser}
                    currentUserId={currentUserId}
                    chatWatermarks={currentChatWatermarks}
                    token={token}
                    onReply={onReply}
                    onEdit={onEdit}
                    onDelete={onDelete}
                    onOpenPreview={onOpenPreview}
                />
            );
        });
        return elements;
    };

    return (
        <div
            id="messages"
            ref={scrollRef}
            onScroll={handleScrollInternal} // This calls the above
            className="messages-container"
        >
            {/* Κουμπί για χειροκίνητο Load More (ως fallback) */}
            {hasMore && (
                <div className="load-more-wrapper">
                    <button className="load-more-btn" onClick={onLoadMore} disabled={isLoadingMessages}>
                        {isLoadingMessages ? "Loading..." : "↑ Load older messages"}
                    </button>
                </div>
            )}

            {renderWithDateDividers()}

            <TypingIndicator typingUser={typingUser} />
        </div>
    );
}
