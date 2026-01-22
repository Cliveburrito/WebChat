import { useState, useRef, useEffect, useLayoutEffect } from "react";
import MessageBubble from "./MessageBubble";

export default function ChatArea({
                                     activeChat,
                                     messages,
                                     onLoadMore,
                                     hasMore,
                                     currentUser,
                                     token,
                                     setMessages,
                                     onMessageSent, //
                                 }) {
    const [text, setText] = useState("");

    const scrollRef = useRef(null);
    const prevScrollHeightRef = useRef(0);
    const isPrependingRef = useRef(false);
    const shouldAutoScrollRef = useRef(true);

    const isNearBottom = (el) => {
        const threshold = 80;
        const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
        return distanceFromBottom < threshold;
    };

    useEffect(() => {
        setText("");
        shouldAutoScrollRef.current = true;
        isPrependingRef.current = false;
    }, [activeChat?.id]);

    const handleScroll = () => {
        const el = scrollRef.current;
        if (!el) return;
        shouldAutoScrollRef.current = isNearBottom(el);
    };

    useLayoutEffect(() => {
        const el = scrollRef.current;
        if (!el) return;

        if (isPrependingRef.current) {
            const newScrollHeight = el.scrollHeight;
            const diff = newScrollHeight - prevScrollHeightRef.current;
            el.scrollTop = el.scrollTop + diff;
            isPrependingRef.current = false;
            return;
        }

        if (shouldAutoScrollRef.current) {
            el.scrollTop = el.scrollHeight;
        }
    }, [messages]);

    const handleLoadMore = () => {
        const el = scrollRef.current;
        if (!el) return;

        prevScrollHeightRef.current = el.scrollHeight;
        isPrependingRef.current = true;
        onLoadMore?.();
    };

    const handleSend = async () => {
        const trimmed = text.trim();
        if (!trimmed || !activeChat) return;

        try {
            const response = await fetch(`/api/messages/chat/${activeChat.id}/smsg`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify({
                    senderUsername: currentUser,
                    conversationId: activeChat.id,
                    content: trimmed,
                }),
            });

            if (!response.ok) {
                const t = await response.text().catch(() => "");
                throw new Error(t || `Send failed (${response.status})`);
            }

            const saved = await response.json();

            // είσαι εσύ, άρα auto-scroll κάτω
            shouldAutoScrollRef.current = true;

            // θpdate chat messages
            setMessages((prev) => [...prev, saved]);

            //  update sidebar (lastMessage) ΑΜΕΣΑ
            if (onMessageSent) onMessageSent(saved);

            setText("");
        } catch (err) {
            console.error(err);
            alert(err.message || "Send failed");
        }
    };

    const onKeyDown = (e) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    if (!activeChat) {
        return (
            <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center" }}>
                Select a chat
            </div>
        );
    }

    return (
        <main className="chat-area">
            <div style={{ padding: "15px", background: "#fff", borderBottom: "1px solid #ddd" }}>
                <strong>{activeChat.name}</strong>
            </div>

            <div id="messages" ref={scrollRef} onScroll={handleScroll}>
                {hasMore && (
                    <button className="load-more-btn" onClick={handleLoadMore}>
                        Load history...
                    </button>
                )}
                {messages.map((m, i) => (
                    <MessageBubble key={m.id ?? `${m.createdAt ?? "t"}-${i}`} msg={m} currentUser={currentUser} />
                ))}
            </div>

            <div className="input-area">
                <input value={text} onChange={(e) => setText(e.target.value)} onKeyDown={onKeyDown} placeholder="Type a message..." />
                <button onClick={handleSend}>Send</button>
            </div>
        </main>
    );
}
