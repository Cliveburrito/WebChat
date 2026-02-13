import { useState, useRef, useEffect, useLayoutEffect, useCallback } from "react";
import ChatHeader from "./ChatHeader";
import MessagesPanel from "./MessagesPanel";
import MessageComposer from "./MessageComposer";
import { apiForm } from "../../api/apiJson";

const STOP_TYPING = "__STOP__";
const REMOTE_LINGER_MS = 1500;   // πόσο να μένει το indicator χωρίς νέα events
const LOCAL_THROTTLE_MS = 1200; // πόσο συχνά στέλνουμε typing

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
                                     onToggleMute,
                                 }) {
    const [text, setText] = useState("");
    const [selectedFiles, setSelectedFiles] = useState([]);
    const [typingUser, setTypingUser] = useState(null);

    const pendingUploadsRef = useRef(new Map());

    const scrollRef = useRef(null);
    const prevScrollHeightRef = useRef(0);
    const isPrependingRef = useRef(false);
    const shouldAutoScrollRef = useRef(true);

    const typingCooldownRef = useRef(null);
    const remoteTypingTimerRef = useRef(null);

    const chatId = activeChat?.conversationId || activeChat?.id;
    const isConnected = !!stompClient?.connected;

    const isNearBottom = (el) => {
        const threshold = 100;
        return el.scrollHeight - el.scrollTop - el.clientHeight < threshold;
    };

    const clearRemoteTyping = useCallback(() => {
        setTypingUser(null);
        if (remoteTypingTimerRef.current) {
            clearTimeout(remoteTypingTimerRef.current);
            remoteTypingTimerRef.current = null;
        }
    }, []);

    const publishTyping = useCallback(
        (payload) => {
            if (!isConnected || !chatId) return;
            try {
                stompClient.publish({
                    destination: `/app/chat/${chatId}/typing`,
                    body: payload,
                });
            } catch (e) {
                console.debug("typing publish failed:", e);
            }
        },
        [isConnected, chatId, stompClient]
    );

    // Subscribe to typing events
    useEffect(() => {
        if (!isConnected || !chatId) {
            clearRemoteTyping();
            return;
        }

        const topic = `/topic/chat/${chatId}/typing`;
        const sub = stompClient.subscribe(topic, (frame) => {
            const body = String(frame.body ?? "").trim();

            // STOP or empty => clear typing immediately
            if (!body || body === STOP_TYPING) {
                clearRemoteTyping();
                return;
            }

            // ignore my own typing echoes
            if (body === currentUser) return;

            setTypingUser(body);

            if (remoteTypingTimerRef.current) clearTimeout(remoteTypingTimerRef.current);
            remoteTypingTimerRef.current = setTimeout(() => {
                setTypingUser(null);
                remoteTypingTimerRef.current = null;
            }, REMOTE_LINGER_MS);
        });

        return () => {
            sub.unsubscribe();
            clearRemoteTyping();
        };
    }, [isConnected, chatId, stompClient, currentUser, clearRemoteTyping]);

    // Reset local UI on chat change
    useEffect(() => {
        setText("");
        setSelectedFiles([]);
        clearRemoteTyping();
        shouldAutoScrollRef.current = true;
        isPrependingRef.current = false;

        if (typingCooldownRef.current) {
            clearTimeout(typingCooldownRef.current);
            typingCooldownRef.current = null;
        }
    }, [chatId, clearRemoteTyping]);

    // ✅ Super reliable: if a message arrives from someone else, they are not typing
    useEffect(() => {
        if (!chatId || !messages?.length) return;
        const last = messages[messages.length - 1];
        if (last?.senderUsername && last.senderUsername !== currentUser) {
            clearRemoteTyping();
        }
    }, [messages, chatId, currentUser, clearRemoteTyping]);

    // Attachment upload after message confirmation
    useEffect(() => {
        const pendingEntries = [...pendingUploadsRef.current.entries()];
        if (!pendingEntries.length) return;

        for (const [tempId, pending] of pendingEntries) {
            const confirmed = messages.find(
                (m) => String(m.clientTempId || "") === String(tempId) && String(m.id) !== String(tempId)
            );
            const messageId = confirmed?.id;

            if (!messageId || String(messageId).startsWith("temp-")) continue;

            pendingUploadsRef.current.delete(tempId);

            (async () => {
                const formData = new FormData();
                pending.files.forEach((f) => formData.append("file", f));
                formData.append("conversationId", String(pending.conversationId));
                formData.append("messageId", String(messageId));

                try {
                    await apiForm("/api/files/upload", { token, formData });
                } catch (err) {
                    console.error("Attachment upload failed:", err);
                    setMessages((prev) =>
                        prev.map((m) => (String(m.id) === String(messageId) ? { ...m, status: "ATTACH_FAILED" } : m))
                    );
                }
            })();
        }
    }, [messages, token, setMessages]);

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

    const handleScroll = () => {
        if (scrollRef.current) {
            shouldAutoScrollRef.current = isNearBottom(scrollRef.current);
        }
    };

    const stopTyping = useCallback(() => {
        // clear local throttle so we can send typing again immediately later
        if (typingCooldownRef.current) {
            clearTimeout(typingCooldownRef.current);
            typingCooldownRef.current = null;
        }
        publishTyping(STOP_TYPING);
    }, [publishTyping]);

    const handleInputChange = (e) => {
        const val = e.target.value;
        setText(val);

        if (!isConnected || !chatId) return;

        // empty -> stop
        if (val.trim().length === 0) {
            stopTyping();
            return;
        }

        // throttle typing
        if (!typingCooldownRef.current) {
            publishTyping(currentUser);
            typingCooldownRef.current = setTimeout(() => {
                typingCooldownRef.current = null;
            }, LOCAL_THROTTLE_MS);
        }
    };

    const handleLoadMore = () => {
        if (scrollRef.current) {
            prevScrollHeightRef.current = scrollRef.current.scrollHeight;
            isPrependingRef.current = true;
            onLoadMore?.();
        }
    };

    const handleFileChange = (e) => {
        const files = Array.from(e.target.files || []);
        if (!files.length) return;
        setSelectedFiles((prev) => [...prev, ...files]);
        e.target.value = "";
    };

    const removeSelectedFile = (idx) => {
        setSelectedFiles((prev) => prev.filter((_, i) => i !== idx));
    };

    const handleSend = async () => {
        const trimmed = text.trim();
        const hasFiles = selectedFiles.length > 0;
        if ((!trimmed && !hasFiles) || !chatId) return;

        // ✅ stop typing immediately (use STOP token)
        stopTyping();

        const tempId = `temp-${Date.now()}`;
        const filesToUpload = selectedFiles;
        const contentToSend = trimmed || "";

        const optimisticMsg = {
            id: tempId,
            clientTempId: tempId,
            conversationId: chatId,
            senderUsername: currentUser,
            content: trimmed,
            attachments: filesToUpload.map((f, i) => ({
                id: `local-${tempId}-${i}`,
                originalName: f.name,
                fileSize: f.size,
                pending: true,
            })),
            createdAt: new Date().toISOString(),
            status: "SENDING",
        };

        setMessages((prev) => [...prev, optimisticMsg]);
        setText("");
        setSelectedFiles([]);
        shouldAutoScrollRef.current = true;

        onMessageSent?.({
            ...optimisticMsg,
            content: trimmed || (hasFiles ? "Attachment" : ""),
        });

        if (hasFiles) {
            pendingUploadsRef.current.set(tempId, {
                files: filesToUpload,
                conversationId: chatId,
            });
        }

        try {
            const response = await fetch(`/api/messages/chat/${chatId}/smsg`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify({ content: contentToSend, tempId }),
            });

            if (!response.ok) throw new Error(`Failed to send (${response.status})`);
        } catch (err) {
            console.error("Send Error:", err);
            pendingUploadsRef.current.delete(tempId);
            setMessages((prev) => prev.map((m) => (m.id === tempId ? { ...m, status: "FAILED" } : m)));
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
            <ChatHeader activeChat={activeChat} onToggleMute={onToggleMute} />

            <MessagesPanel
                scrollRef={scrollRef}
                onScroll={handleScroll}
                hasMore={hasMore}
                onLoadMore={handleLoadMore}
                messages={messages}
                currentUser={currentUser}
                typingUser={typingUser}
            />

            <MessageComposer
                value={text}
                onChange={handleInputChange}
                onKeyDown={onKeyDown}
                selectedFiles={selectedFiles}
                onFileChange={handleFileChange}
                onRemoveFile={removeSelectedFile}
                onSend={handleSend}
                disabled={!text.trim() && selectedFiles.length === 0}
            />
        </main>
    );
}
