import MessageBubble from "./MessageBubble";
import TypingIndicator from "./TypingIndicator";

export default function MessagesPanel({
                                          scrollRef,
                                          onScroll,
                                          hasMore,
                                          onLoadMore,
                                          messages,
                                          currentUser,
                                          typingUser,
                                      }) {
    return (
        <div id="messages" ref={scrollRef} onScroll={onScroll}>
            {hasMore && (
                <button className="load-more-btn" onClick={onLoadMore}>
                    Load older messages
                </button>
            )}

            {messages.map((m, i) => (
                <MessageBubble key={m.id ?? `temp-${i}`} msg={m} currentUser={currentUser} />
            ))}

            <TypingIndicator typingUser={typingUser} />
        </div>
    );
}
