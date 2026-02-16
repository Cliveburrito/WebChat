import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { apiJson } from "../api/apiJson";

const MESSAGE_PAGE_SIZE = 50;

// 🚀 BEAST MODE UPDATE: Προσθέσαμε το stompClient στα props
export function useChatData({ token, currentUser, stompClient }) {
    const [conversations, setConversations] = useState([]);
    const [allUsers, setAllUsers] = useState([]);
    const [activeChat, setActiveChat] = useState(null);
    const [messages, setMessages] = useState([]);
    const [msgPage, setMsgPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [isLoadingMessages, setIsLoadingMessages] = useState(false);
    const [currentUserId, setCurrentUserId] = useState(null);

    // ✅ NEW: State για τα Ticks (Read/Delivered IDs)
    // Format: { [convId]: { [userId]: { lastReadId: 0, lastDeliveredId: 0 } } }
    const [watermarks, setWatermarks] = useState({});

    const activeChatRef = useRef(null);
    const inFlightRequestsRef = useRef(0);
    const requestedPagesRef = useRef(new Set());
    const lastReadAckRef = useRef(new Map());
    const lastDeliveredAckRef = useRef(new Map());
    useEffect(() => {
        activeChatRef.current = activeChat;
    }, [activeChat]);

    const normalizeId = (x) => String(x ?? "");

    // ✅ NEW: Callback που καλείται από το useChatTopics όταν έρχεται Live Update
    const onWatermarkUpdate = useCallback((payload) => {
        const { conversationId, userId, messageId, type } = payload;
        // Μετατροπή του type σε κλειδί state
        const key = type === 'READ' ? 'lastReadId' : 'lastDeliveredId';

        setWatermarks(prev => ({
            ...prev,
            [conversationId]: {
                ...prev[conversationId],
                [userId]: {
                    ...prev[conversationId]?.[userId],
                    [key]: messageId
                }
            }
        }));
    }, []);

    // ... (fetchChats & fetchUsers παραμένουν ίδια) ...
    const fetchChats = useCallback(async () => {
        if (!token) return;
        try {
            const data = await apiJson("/api/chats/my", { token });
            setConversations(Array.isArray(data) ? data : []);
        } catch (err) {
            console.error("Fetch chats failed:", err);
        }
    }, [token]);

    const fetchUsers = useCallback(async () => {
        if (!token) return;
        try {
            const data = await apiJson("/api/users/getall", { token });
            const list = Array.isArray(data) ? data : [];
            setAllUsers(list);
            const me = list.find((u) => u.username === currentUser);
            if (me) setCurrentUserId(me.id);
        } catch (err) {
            console.error("Fetch users failed:", err);
        }
    }, [token, currentUser]);

    // ... (fetchMessages παραμένει ίδιο) ...
    const fetchMessages = useCallback(
        async (chatId, page = 0) => {
            if (!token || !chatId) return;

            const dedupeKey = `${normalizeId(chatId)}:${page}`;
            if (requestedPagesRef.current.has(dedupeKey)) return;

            requestedPagesRef.current.add(dedupeKey);
            inFlightRequestsRef.current += 1;
            setIsLoadingMessages(true);
            try {
                const data = await apiJson(`/api/chats/${chatId}/messages?page=${page}&size=${MESSAGE_PAGE_SIZE}`, { token });
                const raw = Array.isArray(data) ? data : data?.content || [];
                const sorted = raw
                    .slice()
                    .sort((a, b) => new Date(a.sentAt || a.createdAt) - new Date(b.sentAt || b.createdAt));

                if (page === 0) setMessages(sorted);
                else setMessages((prev) => [...sorted, ...prev]);

                setHasMore(raw.length === MESSAGE_PAGE_SIZE);
                setMsgPage(page);
            } catch (err) {
                console.error("Fetch messages failed:", err);
            } finally {
                requestedPagesRef.current.delete(dedupeKey);
                inFlightRequestsRef.current = Math.max(0, inFlightRequestsRef.current - 1);
                setIsLoadingMessages(inFlightRequestsRef.current > 0);
            }
        },
        [token]
    );

    // 🚀 BEAST MODE UPDATE: WebSocket Ack αντί για REST API
    const markChatRead = useCallback((chatId, messageId = null) => {
        if (!chatId) return;
        const key = normalizeId(chatId);

        // 1. Optimistic UI Update (Μηδενισμός Unread Count τοπικά)
        setConversations((prev) =>
            prev.map((c) =>
                normalizeId(c.conversationId || c.id) === key && (c.unreadCount > 0 || c.unread_count > 0)
                    ? { ...c, unreadCount: 0, unread_count: 0 }
                    : c
            )
        );

        // 2. Εύρεση του target Message ID
        // Αν δεν μας δώσουν ID, παίρνουμε το τελευταίο από το messages state (αν ανήκει σε αυτό το chat)
        let targetId = messageId;
        if (!targetId && activeChatRef.current && normalizeId(activeChatRef.current.conversationId || activeChatRef.current.id) === key) {
            // Προσοχή: Εδώ δεν έχουμε άμεση πρόσβαση στο latest 'messages' state μέσα στο callback χωρίς dependency.
            // Γι' αυτό το περνάμε συνήθως ως όρισμα.
        }

        // 3. Αποστολή WebSocket ACK (READ)
        const previousAckId = Number(lastReadAckRef.current.get(key) || 0);
        if (stompClient?.connected && targetId && !String(targetId).startsWith('temp-') && Number(targetId) > previousAckId) {
            lastReadAckRef.current.set(key, Number(targetId));
            console.debug("Sending READ Ack:", { chatId, targetId });
            stompClient.publish({
                destination: "/app/chat.ack",
                body: JSON.stringify({
                    conversationId: chatId,
                    messageId: targetId,
                    type: 'READ'
                })
            });
        }
    }, [stompClient]); // Δεν βάζουμε το messages εδώ για να αποφύγουμε re-renders

    // 🚀 BEAST MODE UPDATE: Auto-Delivery Ack
    const bumpConversation = useCallback((conversationId, { content, createdAt, isIncoming, messageId }) => { // Πήρε extra param: messageId
        const key = normalizeId(conversationId);

        setConversations((prev) => {
            const idx = prev.findIndex((c) => normalizeId(c.conversationId || c.id) === key);
            if (idx === -1) return prev;

            const old = prev[idx];
            const isActive = normalizeId(activeChatRef.current?.conversationId || activeChatRef.current?.id) === key;

            const updated = {
                ...old,
                lastContent: content,
                lastMessageAt: createdAt || new Date().toISOString(),
                unreadCount: isIncoming && !isActive
                    ? Number(old.unreadCount || old.unread_count || 0) + 1
                    : isActive
                        ? 0
                        : Number(old.unreadCount || old.unread_count || 0),
            };

            updated.unread_count = updated.unreadCount;

            const rest = prev.filter((_, i) => i !== idx);
            return [updated, ...rest];
        });

        // ✅ Αν είναι εισερχόμενο, στείλε DELIVERED Ack αμέσως
        const previousDeliveredId = Number(lastDeliveredAckRef.current.get(key) || 0);
        if (isIncoming && stompClient?.connected && messageId && !String(messageId).startsWith('temp-') && Number(messageId) > previousDeliveredId) {
            lastDeliveredAckRef.current.set(key, Number(messageId));
            stompClient.publish({
                destination: "/app/chat.ack",
                body: JSON.stringify({
                    conversationId,
                    messageId: messageId,
                    type: 'DELIVERED'
                })
            });
        }
    }, [stompClient]);

    // ... (openDirectChat, onGroupCreated, toggleMute παραμένουν ίδια) ...
    const openDirectChat = useCallback(
        async (targetUserId) => {
            if (!token || !targetUserId || !currentUserId) {
                console.error("Cannot open chat: Missing IDs");
                return;
            }
            try {
                const chatDTO = await apiJson("/api/chats/direct", {
                    token,
                    method: "POST",
                    body: { id1: currentUserId, id2: targetUserId }
                });

                if (chatDTO) {
                    setConversations(prev => {
                        const chatId = chatDTO.conversationId || chatDTO.id;
                        const exists = prev.find(c => normalizeId(c.conversationId || c.id) === normalizeId(chatId));
                        if (exists) return prev;
                        return [chatDTO, ...prev];
                    });
                    setActiveChat(chatDTO);
                }
            } catch (err) {
                console.error("openDirectChat failed:", err);
            }
        },
        [token, currentUserId]
    );

    const onGroupCreated = useCallback((newGroup) => {
        if (!newGroup) return;
        setConversations((prev) => {
            const chatId = newGroup.conversationId || newGroup.id;
            const exists = prev.find((c) => normalizeId(c.conversationId || c.id) === normalizeId(chatId));
            if (exists) return prev;
            return [newGroup, ...prev];
        });
        setActiveChat(newGroup);
    }, []);

    const toggleMute = useCallback(
        async (chatId, currentStatus) => {
            const next = !currentStatus;
            setConversations((prev) =>
                prev.map((c) => normalizeId(c.conversationId || c.id) === normalizeId(chatId) ? { ...c, muted: next } : c)
            );
            if (normalizeId(activeChat?.id || activeChat?.conversationId) === normalizeId(chatId)) {
                setActiveChat((prev) => ({ ...prev, muted: next }));
            }
            try {
                await fetch(`/api/chats/${chatId}/mute?status=${next}`, {
                    method: "PATCH",
                    headers: { Authorization: `Bearer ${token}` },
                });
            } catch (err) { console.warn("toggleMute ignored:", err); }
        },
        [token, activeChat]
    );

    // Initial Load
    useEffect(() => {
        if (!token) return;
        let alive = true;
        (async () => {
            try {
                const chats = await apiJson("/api/chats/my", { token });
                if (alive) setConversations(Array.isArray(chats) ? chats : []);
            } catch (err) { console.error("Initial fetch chats failed:", err); }

            try {
                const users = await apiJson("/api/users/getall", { token });
                if (!alive) return;
                setAllUsers(Array.isArray(users) ? users : []);
                const me = users.find((u) => u.username === currentUser);
                if (me) setCurrentUserId(me.id);
            } catch (err) { console.error("Initial fetch users failed:", err); }
        })();
        return () => { alive = false; };
    }, [token, currentUser]);

    const currentChatId = activeChat?.conversationId || activeChat?.id;

    // ✅ Trigger Read when opening chat or receiving messages while open
    useEffect(() => {
        if (!currentChatId || !token) return;

        requestedPagesRef.current.clear();
        setMsgPage(0);
        setHasMore(true);

        // Φέρνουμε μηνύματα
        fetchMessages(currentChatId, 0);

        // Στέλνουμε Read Ack για το τελευταίο μήνυμα (αν υπάρχει στη λίστα)
        // Σημείωση: Επειδή το fetchMessages είναι async, το messages state μπορεί να μην έχει ενημερωθεί ακόμα.
        // Το markChatRead θα κληθεί ξανά από το useChatTopics όταν έρθει νέο μήνυμα.

    }, [currentChatId, token, fetchMessages]);

    // ✅ Extra Effect: Όταν αλλάζουν τα μηνύματα και είμαστε σε ενεργό chat -> Mark Last as Read
    useEffect(() => {
        if (currentChatId && messages.length > 0) {
            const lastMsg = messages[messages.length - 1];
            if (lastMsg?.id && !String(lastMsg.id).startsWith("temp-")) {
                markChatRead(currentChatId, lastMsg.id);
            }
        }
    }, [messages, currentChatId, markChatRead]);

    const activeChatId = useMemo(() => activeChat?.conversationId || activeChat?.id, [activeChat]);

    return {
        conversations,
        allUsers,
        activeChat,
        messages,
        watermarks, // <--- EXPORTED STATE
        msgPage,
        hasMore,
        isLoadingMessages,
        activeChatId,
        currentUserId,

        setActiveChat,
        setMessages,
        fetchMessages,
        markChatRead,
        bumpConversation,
        toggleMute,
        openDirectChat,
        onGroupCreated,
        onWatermarkUpdate, // <--- EXPORTED CALLBACK
        fetchChats,
        fetchUsers,
    };
}
