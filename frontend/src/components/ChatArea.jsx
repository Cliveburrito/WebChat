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
                                     onToggleMute // Το νέο prop για τη σίγαση
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
    const typingTimeoutRef = useRef(null);
    const remoteTypingTimerRef = useRef(null);

    // --- Helper: Check if user is near bottom ---
    const isNearBottom = (el) => {
        const threshold = 100;
        return el.scrollHeight - el.scrollTop - el.clientHeight < threshold;
    };

    // --- Effect: Handle STOMP Subscriptions for Typing ---
    useEffect(() => {
        const chatId = activeChat?.conversationId || activeChat?.id;

        // 1. Guard: Μην προχωράς αν δεν είμαστε έτοιμοι
        if (!stompClient?.connected || !chatId) {
            setTypingUser(null);
            return;
        }

        const topic = `/topic/chat/${chatId}/typing`;

        const sub = stompClient.subscribe(topic, (frame) => {
            const typingName = frame.body;

            // 2. Αν το σήμα αφορά άλλον χρήστη
            if (typingName && typingName !== currentUser) {
                setTypingUser(typingName);

                // 3. Debounce/Expiry logic
                if (remoteTypingTimerRef.current) clearTimeout(remoteTypingTimerRef.current);

                remoteTypingTimerRef.current = setTimeout(() => {
                    setTypingUser(null);
                }, 2500); // 2.5 δευτερόλεπτα είναι το "sweet spot"
            }
            // 4. Προαιρετικό: Αν ο server στείλει κενό string, σημαίνει "σταμάτησα επίσημα"
            else if (typingName === "") {
                setTypingUser(null);
            }
        });

        return () => {
            sub.unsubscribe();
            if (remoteTypingTimerRef.current) clearTimeout(remoteTypingTimerRef.current);
            // Μην ξεχνάς να το μηδενίζεις στο cleanup για να μη "μένει" το όνομα όταν αλλάζεις chat
            setTypingUser(null);
        };
    }, [activeChat?.conversationId, activeChat?.id, stompClient?.connected, currentUser, stompClient]);

    // --- Effect: Reset state on Chat Switch ---
    useEffect(() => {
        setText("");
        setTypingUser(null);
        shouldAutoScrollRef.current = true;
        isPrependingRef.current = false;
    }, [activeChat?.conversationId, activeChat?.id]);

    // --- Layout Effect: Smart Scrolling ---
    useLayoutEffect(() => {
        const el = scrollRef.current;
        if (!el) return;

        if (isPrependingRef.current) {
            el.scrollTop = el.scrollHeight - prevScrollHeightRef.current;
            isPrependingRef.current = false;
        } else if (shouldAutoScrollRef.current) {
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

        const chatId = activeChat?.conversationId || activeChat?.id;
        if (!typingTimeoutRef.current && val.length > 0 && stompClient?.connected && chatId) {
            stompClient.publish({
                destination: `/app/chat/${chatId}/typing`,
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
        const chatId = activeChat?.conversationId || activeChat?.id;
        if (!trimmed || !chatId) return;

        try {
            const response = await fetch(`/api/messages/chat/${chatId}/smsg`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify({
                    senderUsername: currentUser,
                    conversationId: chatId,
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

    if (!activeChat) {
        return (
            <main className="chat-area">
                <div className="chat-empty-state">
                    <div className="empty-icon">💬</div>
                    <p>Select a conversation to start chatting</p>
                </div>
            </main>
        );
    }

    return (
        <main className="chat-area">
            {/* Header με Toggle Mute */}
            <header className="chat-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 20px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <strong>{activeChat.displayName || activeChat.name}</strong>
                    {/* Αν είναι muted, δείχνουμε και ένα μικρό label */}
                    {activeChat.muted && <span style={{ fontSize: '0.7rem', backgroundColor: '#eee', color: '#555', padding: '2px 6px', borderRadius: '4px' }}>MUTED</span>}
                </div>

                <button
                    onClick={() => onToggleMute(activeChat.conversationId || activeChat.id, activeChat.muted)}
                    style={{
                        background: 'none',
                        border: 'none',
                        cursor: 'pointer',
                        fontSize: '1.4rem',
                        transition: 'all 0.2s ease',
                        opacity: activeChat.muted ? 0.5 : 1, // Πιο αχνό αν είναι muted
                        filter: activeChat.muted ? 'grayscale(100%)' : 'none' // Ασπρόμαυρο αν είναι muted
                    }}
                    title={activeChat.muted ? "Click to Unmute" : "Click to Mute"}
                >
                    {activeChat.muted ? "🔕" : "🔔"}
                </button>
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