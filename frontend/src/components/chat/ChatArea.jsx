import { useState, useRef, useEffect, useLayoutEffect, useCallback } from "react";
import ChatHeader from "./ChatHeader";
import MessagesPanel from "./MessagesPanel";
import MessageComposer from "./MessageComposer";
import { apiForm } from "../../api/apiJson";
import "./ChatArea.css";

const STOP_TYPING = "__STOP__";
const REMOTE_LINGER_MS = 1500;
const LOCAL_THROTTLE_MS = 1200;

export default function ChatArea({
                                     activeChat,
                                     messages,
                                     onLoadMore,
                                     hasMore,
                                     isLoadingMessages,
                                     currentUser,
                                     token,
                                     setMessages,
                                     onMessageSent,
                                     stompClient,
                                     onToggleMute,
                                     watermarks
                                 }) {
    // --- States ---
    const [text, setText] = useState("");
    const [selectedFiles, setSelectedFiles] = useState([]);
    const [typingUser, setTypingUser] = useState(null);

    // --- Refs για Scrolling & UI Logic ---
    const scrollRef = useRef(null);
    const prevScrollHeightRef = useRef(0);
    const isPrependingRef = useRef(false);
    const shouldAutoScrollRef = useRef(true);

    // --- Refs για Typing & Uploads ---
    const pendingUploadsRef = useRef(new Map());
    const typingCooldownRef = useRef(null);
    const remoteTypingTimerRef = useRef(null);

    // --- Derived Values ---
    const chatId = activeChat?.conversationId || activeChat?.id;
    const isConnected = !!stompClient?.connected;

    // --- 🟢 SCROLL LOGIC (The Fix) ---
    const isNearBottom = (el) => {
        const threshold = 150;
        return el.scrollHeight - el.scrollTop - el.clientHeight < threshold;
    };

    const handleLoadMore = useCallback(() => {
        if (!hasMore || !scrollRef.current || isPrependingRef.current) return;

        prevScrollHeightRef.current = scrollRef.current.scrollHeight;
        isPrependingRef.current = true;
        shouldAutoScrollRef.current = false;

        onLoadMore?.();
    }, [hasMore, onLoadMore]);

    const handleScroll = useCallback(() => {
        if (scrollRef.current) {
            shouldAutoScrollRef.current = isNearBottom(scrollRef.current);

            // Προαιρετικό: Αυτόματο load more αν ο χρήστης φτάσει στην κορυφή
            if (scrollRef.current.scrollTop <= 5 && hasMore && !isPrependingRef.current) {
                handleLoadMore();
            }
        }
    }, [hasMore, handleLoadMore]);

    useLayoutEffect(() => {
        const el = scrollRef.current;
        if (!el) return;

        if (isPrependingRef.current) {
            // Κλειδώνουμε το scroll στη θέση που ήταν πριν έρθουν τα παλιά μηνύματα
            const heightDifference = el.scrollHeight - prevScrollHeightRef.current;
            el.scrollTop = heightDifference;
            isPrependingRef.current = false;
        } else if (shouldAutoScrollRef.current) {
            // Scroll στον πάτο για νέα μηνύματα
            el.scrollTop = el.scrollHeight;
        }
    }, [messages]);

    // --- 🟡 TYPING LOGIC ---
    const clearRemoteTyping = useCallback(() => {
        setTypingUser(null);
        if (remoteTypingTimerRef.current) {
            clearTimeout(remoteTypingTimerRef.current);
            remoteTypingTimerRef.current = null;
        }
    }, []);

    const publishTyping = useCallback((payload) => {
        if (!isConnected || !chatId) return;
        try {
            stompClient.publish({
                destination: `/app/chat/${chatId}/typing`,
                body: payload,
            });
        } catch (e) { console.error("Typing publish failed", e); }
    }, [isConnected, chatId, stompClient]);

    useEffect(() => {
        if (!isConnected || !chatId) {
            clearRemoteTyping();
            return;
        }

        const sub = stompClient.subscribe(`/topic/chat/${chatId}/typing`, (frame) => {
            const body = String(frame.body ?? "").trim();
            if (!body || body === STOP_TYPING || body === currentUser) {
                clearRemoteTyping();
                return;
            }
            setTypingUser(body);
            if (remoteTypingTimerRef.current) clearTimeout(remoteTypingTimerRef.current);
            remoteTypingTimerRef.current = setTimeout(clearRemoteTyping, REMOTE_LINGER_MS);
        });

        return () => {
            sub.unsubscribe();
            clearRemoteTyping();
        };
    }, [isConnected, chatId, stompClient, currentUser, clearRemoteTyping]);

    // --- 🔵 ATTACHMENT LOGIC ---
    useEffect(() => {
        const pendingEntries = [...pendingUploadsRef.current.entries()];
        if (!pendingEntries.length) return;

        pendingEntries.forEach(async ([tempId, pending]) => {
            const confirmed = messages.find(m => m.clientTempId === tempId && !String(m.id).startsWith('temp-'));
            if (!confirmed) return;

            pendingUploadsRef.current.delete(tempId);
            const formData = new FormData();
            pending.files.forEach(f => formData.append("file", f));
            formData.append("conversationId", String(pending.conversationId));
            formData.append("messageId", String(confirmed.id));

            try {
                await apiForm("/api/files/upload", { token, formData });
            } catch {
                setMessages(prev => prev.map(m => m.id === confirmed.id ? { ...m, status: "ATTACH_FAILED" } : m));
            }
        });
    }, [messages, token, setMessages]);

    // --- 🟠 HANDLERS ---
    const handleInputChange = (e) => {
        const val = e.target.value;
        setText(val);
        if (!isConnected || !chatId) return;

        if (!val.trim()) {
            publishTyping(STOP_TYPING);
            return;
        }

        if (!typingCooldownRef.current) {
            publishTyping(currentUser);
            typingCooldownRef.current = setTimeout(() => {
                typingCooldownRef.current = null;
            }, LOCAL_THROTTLE_MS);
        }
    };

    const handleSend = async () => {
        const trimmed = text.trim();
        const hasFiles = selectedFiles.length > 0;
        if ((!trimmed && !hasFiles) || !chatId) return;

        if (typingCooldownRef.current) {
            clearTimeout(typingCooldownRef.current);
            typingCooldownRef.current = null;
        }
        publishTyping(STOP_TYPING);

        const tempId = `temp-${Date.now()}`;
        const optimisticMsg = {
            id: tempId,
            clientTempId: tempId,
            conversationId: chatId,
            senderUsername: currentUser,
            content: trimmed,
            attachments: selectedFiles.map((f, i) => ({ id: `l-${tempId}-${i}`, originalName: f.name, pending: true })),
            createdAt: new Date().toISOString(),
            status: "SENDING",
        };

        if (hasFiles) pendingUploadsRef.current.set(tempId, { files: selectedFiles, conversationId: chatId });

        setMessages(prev => [...prev, optimisticMsg]);
        setText("");
        setSelectedFiles([]);
        shouldAutoScrollRef.current = true;

        onMessageSent?.({ ...optimisticMsg, content: trimmed || "Attachment" });

        try {
            const res = await fetch(`/api/messages/chat/${chatId}/smsg`, {
                method: "POST",
                headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
                body: JSON.stringify({ content: trimmed, tempId }),
            });
            if (!res.ok) throw new Error();
        } catch {
            setMessages(prev => prev.map(m => m.id === tempId ? { ...m, status: "FAILED" } : m));
        }
    };

    if (!activeChat) {
        return (
            <main className="chat-area empty">
                <div className="empty-state">💬 Select a chat to start</div>
            </main>
        );
    }

    return (
        <main className="chat-area">
            <ChatHeader activeChat={activeChat} onToggleMute={onToggleMute} />

            <MessagesPanel
                scrollRef={scrollRef}
                onScroll={handleScroll}
                hasMore={hasMore}
                isLoadingMessages={isLoadingMessages}
                onLoadMore={handleLoadMore}
                messages={messages}
                currentUser={currentUser}
                typingUser={typingUser}
                watermarks={watermarks}
                activeChatId={chatId}
            />

            <MessageComposer
                value={text}
                onChange={handleInputChange}
                onKeyDown={(e) => e.key === "Enter" && !e.shiftKey && (e.preventDefault(), handleSend())}
                selectedFiles={selectedFiles}
                onFileChange={(e) => setSelectedFiles(prev => [...prev, ...Array.from(e.target.files || [])])}
                onRemoveFile={(idx) => setSelectedFiles(prev => prev.filter((_, i) => i !== idx))}
                onSend={handleSend}
                disabled={!text.trim() && selectedFiles.length === 0}
            />
        </main>
    );
}