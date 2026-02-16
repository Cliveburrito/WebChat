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
                                          typingUser,
                                          watermarks,
                                          activeChatId
                                      }) {
    const currentChatWatermarks = watermarks?.[activeChatId] || {};

    // --- Logic για αυτόματο Load More στο Scroll ---
    const handleScrollInternal = () => {
        // Το load more ελέγχεται κεντρικά από το ChatArea για να αποφεύγουμε διπλά requests.
        onScroll();
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

            elements.push(
                <MessageBubble
                    key={m.id ?? `temp-${i}`}
                    msg={m}
                    currentUser={currentUser}
                    chatWatermarks={currentChatWatermarks}
                />
            );
        });
        return elements;
    };

    return (
        <div
            id="messages"
            ref={scrollRef}
            onScroll={handleScrollInternal}
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