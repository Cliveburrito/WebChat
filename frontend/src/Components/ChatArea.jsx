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
                                     onMessageSent,
                                     stompClient,
                                 }) {
    // --- State ---
    const [text, setText] = useState("");
    const [typingUser, setTypingUser] = useState(null);

    // --- Refs for Scroll Management ---
    const scrollRef = useRef(null);
    const prevScrollHeightRef = useRef(0);
    const isPrependingRef = useRef(false);
    const shouldAutoScrollRef = useRef(true);

    // --- Refs for Typing Logic ---
    const typingTimeoutRef = useRef(null);       // Throttles outgoing signals
    const remoteTypingTimerRef = useRef(null);    // Clears "is typing" UI locally

    // --- Helper: Check if user is near bottom ---
    const isNearBottom = (el) => {
        const threshold = 100;
        return el.scrollHeight - el.scrollTop - el.clientHeight < threshold;
    };

    // --- Effect: Handle STOMP Subscriptions for Typing ---
    useEffect(() => {
        // If the socket isn't connected or no chat is active, get out.
        if (!stompClient?.connected || !activeChat?.id) return;

        // FIXED: Using 'frame' consistently in the callback
        const sub = stompClient.subscribe(`/topic/chat/${activeChat.id}/typing`, (frame) => {
            const typingName = frame.body;

            // console.log("Typing signal received from:", typingName);

            // Only show if it's someone else typing
            if (typingName !== currentUser) {
                setTypingUser(typingName);

                // Auto-clear the "is typing" status after 4 seconds of silence
                if (remoteTypingTimerRef.current) clearTimeout(remoteTypingTimerRef.current);
                remoteTypingTimerRef.current = setTimeout(() => {
                    setTypingUser(null);
                }, 4000);
            }
        });

        return () => {
            sub.unsubscribe();
            if (remoteTypingTimerRef.current) clearTimeout(remoteTypingTimerRef.current);
            setTypingUser(null);
        };
    }, [activeChat?.id, stompClient, currentUser]);

    // --- Effect: Reset state on Chat Switch ---
    useEffect(() => {
        setText("");
        setTypingUser(null);
        shouldAutoScrollRef.current = true;
        isPrependingRef.current = false;
    }, [activeChat?.id]);

    // --- Layout Effect: Smart Scrolling ---
    useLayoutEffect(() => {
        const el = scrollRef.current;
        if (!el) return;

        if (isPrependingRef.current) {
            // Maintain scroll position when loading history
            el.scrollTop = el.scrollHeight - prevScrollHeightRef.current;
            isPrependingRef.current = false;
        } else if (shouldAutoScrollRef.current) {
            // Snap to bottom for new messages or typing indicator
            el.scrollTop = el.scrollHeight;
        }
    }, [messages, typingUser]);

    // --- Handlers ---
    const handleScroll = () => {
        if (scrollRef.current) {
            shouldAutoScrollRef.current = isNearBottom(scrollRef.current);
        }
    };

    const handleInputChange = (e) => {
        const val = e.target.value;
        setText(val);

        // Throttle typing signals: Send max once every 2 seconds
        if (!typingTimeoutRef.current && val.length > 0 && stompClient?.connected) {
            stompClient.publish({
                destination: `/app/chat/${activeChat.id}/typing`,
                body: currentUser
            });

            typingTimeoutRef.current = setTimeout(() => {
                typingTimeoutRef.current = null;
            }, 2000);
        }
    };

    const handleLoadMore = () => {
        if (scrollRef.current) {
            prevScrollHeightRef.current = scrollRef.current.scrollHeight;
            isPrependingRef.current = true;
            onLoadMore?.();
        }
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

            if (!response.ok) throw new Error("Failed to send message");

            const saved = await response.json();
            shouldAutoScrollRef.current = true;
            setMessages((prev) => [...prev, saved]);
            if (onMessageSent) onMessageSent(saved);
            setText("");
        } catch (err) {
            console.error("Send Error:", err);
        }
    };

    const onKeyDown = (e) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    // --- Render Guard for "No Active Chat" ---
    if (!activeChat) {
        return (
            <main className="chat-area">
                <div className="chat-empty-state" style={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    justifyContent: "center",
                    height: "100%",
                    color: "#888"
                }}>
                    <div className="empty-icon" style={{ fontSize: "3rem", marginBottom: "10px" }}>💬</div>
                    <p>Select a conversation to start chatting</p>
                </div>
            </main>
        );
    }

    return (
        <main className="chat-area">
            <header className="chat-header">
                <strong>{activeChat.name}</strong>
            </header>

            <div id="messages" ref={scrollRef} onScroll={handleScroll}>
                {hasMore && (
                    <button className="load-more-btn" onClick={handleLoadMore}>
                        Load older messages
                    </button>
                )}

                {messages.map((m, i) => (
                    <MessageBubble
                        key={m.id ?? `temp-${i}`}
                        msg={m}
                        currentUser={currentUser}
                    />
                ))}

                {/* Real-time Typing Indicator */}
                {typingUser && (
                    <div className="typing-indicator">
                        <span>{typingUser} is typing</span>
                        <div className="typing-dots">
                            <span></span><span></span><span></span>
                        </div>
                    </div>
                )}
            </div>

            <div className="input-area">
                <input
                    value={text}
                    onChange={handleInputChange}
                    onKeyDown={onKeyDown}
                    placeholder="Type a message..."
                />
                <button onClick={handleSend} disabled={!text.trim()}>
                    Send
                </button>
            </div>
        </main>
    );
}