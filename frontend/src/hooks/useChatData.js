import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { apiJson, instrumentedFetch } from "../api/apiJson";

const MESSAGE_PAGE_SIZE = 50;
const ACK_DEBOUNCE_MS = 800;

export function useChatData({ token, currentUser, stompClient }) {
    // --- STATE ---
    const [conversations, setConversations] = useState([]);
    const [allUsers, setAllUsers] = useState([]);
    const [activeChat, setActiveChat] = useState(null);
    const [messages, setMessages] = useState([]);
    const [msgPage, setMsgPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [isLoadingMessages, setIsLoadingMessages] = useState(false);
    const [currentUserId, setCurrentUserId] = useState(null);
    const [watermarks, setWatermarks] = useState({});
    const [selfWatermarks, setSelfWatermarks] = useState({});

    // --- DERIVED STATE (Πρώτο για να μην χτυπάει ReferenceError) ---
    const activeChatId = useMemo(() => activeChat?.conversationId || activeChat?.id, [activeChat]);

    // --- REFS ---
    const activeChatRef = useRef(null);
    const selfWatermarksRef = useRef({});
    const isFetchingRef = useRef(false);
    const abortControllerRef = useRef(null);
    const lastReadAckRef = useRef(new Map());
    const lastDeliveredAckRef = useRef(new Map());
    const pendingAckRef = useRef(new Map());
    const ackTimerRef = useRef(null);

    useEffect(() => { activeChatRef.current = activeChat; }, [activeChat]);
    useEffect(() => { selfWatermarksRef.current = selfWatermarks; }, [selfWatermarks]);

    const normalizeId = useCallback((x) => String(x ?? ""), []);

    const flushPendingAcks = useCallback(() => {
        if (!stompClient?.connected || pendingAckRef.current.size === 0) return;

        const pending = new Map(pendingAckRef.current);
        pendingAckRef.current.clear();

        pending.forEach((ack) => {
            stompClient.publish({
                destination: "/app/chat.ack",
                body: JSON.stringify(ack),
            });
        });
    }, [stompClient]);

    const queueAck = useCallback((chatId, messageId, type) => {
        if (!chatId || !messageId || !stompClient?.connected) return;

        const key = `${normalizeId(chatId)}:${type}`;
        const readKey = `${normalizeId(chatId)}:READ`;
        const deliveredKey = `${normalizeId(chatId)}:DELIVERED`;

        if (type === "DELIVERED") {
            const pendingRead = pendingAckRef.current.get(readKey);
            if (Number(pendingRead?.messageId || 0) >= Number(messageId)) return;
        }

        if (type === "READ") {
            const pendingDelivered = pendingAckRef.current.get(deliveredKey);
            if (Number(pendingDelivered?.messageId || 0) <= Number(messageId)) {
                pendingAckRef.current.delete(deliveredKey);
            }
        }

        const existing = pendingAckRef.current.get(key);
        const nextMessageId = Math.max(Number(existing?.messageId || 0), Number(messageId));

        pendingAckRef.current.set(key, {
            conversationId: chatId,
            messageId: nextMessageId,
            type,
        });

        if (ackTimerRef.current) window.clearTimeout(ackTimerRef.current);
        ackTimerRef.current = window.setTimeout(() => {
            ackTimerRef.current = null;
            flushPendingAcks();
        }, ACK_DEBOUNCE_MS);
    }, [flushPendingAcks, normalizeId, stompClient]);

    const applyKnownReadState = useCallback((chat) => {
        const convId = normalizeId(chat.conversationId || chat.id);
        const lastMessageId = Number(chat.lastMessageId || 0);
        const knownReadId = Number(selfWatermarksRef.current[convId]?.lastReadId || chat.myLastReadMessageId || 0);

        if (lastMessageId && String(chat.lastSenderId) !== String(currentUserId) && knownReadId >= lastMessageId) {
            return { ...chat, unreadCount: 0, unread_count: 0 };
        }

        return chat;
    }, [currentUserId, normalizeId]);

    const upsertConversation = useCallback((incoming) => {
        if (!incoming) return;
        const incomingId = normalizeId(incoming.conversationId || incoming.id);
        if (!incomingId) return;

        const normalizedIncoming = applyKnownReadState(incoming);

        setConversations((prev) => {
            const existing = prev.find((chat) => normalizeId(chat.conversationId || chat.id) === incomingId);
            const merged = existing ? { ...existing, ...normalizedIncoming } : normalizedIncoming;
            const rest = prev.filter((chat) => normalizeId(chat.conversationId || chat.id) !== incomingId);
            return [merged, ...rest];
        });

        setSelfWatermarks((prev) => ({
            ...prev,
            [incomingId]: {
                lastReadId: Number(incoming.myLastReadMessageId || prev[incomingId]?.lastReadId || 0),
                lastDeliveredId: Number(incoming.myLastDeliveredMessageId || prev[incomingId]?.lastDeliveredId || 0),
            },
        }));

        const participantWatermarks = Array.isArray(incoming.participantWatermarks) ? incoming.participantWatermarks : [];
        if (participantWatermarks.length) {
            setWatermarks((prev) => ({
                ...prev,
                [incomingId]: participantWatermarks.reduce((acc, watermark) => {
                    acc[watermark.userId] = {
                        lastReadId: Number(watermark.lastReadMessageId || 0),
                        lastDeliveredId: Number(watermark.lastDeliveredMessageId || 0),
                    };
                    return acc;
                }, prev[incomingId] || {}),
            }));
        }
    }, [applyKnownReadState, normalizeId]);

    const upsertUser = useCallback((incoming) => {
        if (!incoming?.id || !incoming?.username) return;
        setAllUsers((prev) => {
            const exists = prev.some((user) => String(user.id) === String(incoming.id));
            if (exists) {
                return prev.map((user) => String(user.id) === String(incoming.id) ? { ...user, ...incoming } : user);
            }
            return [...prev, incoming].sort((a, b) => String(a.username).localeCompare(String(b.username)));
        });
        if (incoming.username === currentUser) {
            setCurrentUserId(incoming.id);
        }
    }, [currentUser]);

    // --- CALLBACKS ---

    // 1. Watermark Update (Live Ticks)
    const onWatermarkUpdate = useCallback((payload) => {
        const { conversationId, userId, messageId, type } = payload;
        // console.log("💧 Watermark Update:", payload); // <-- LOG
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

    // 2. Fetch Chats
    const fetchChats = useCallback(async () => {
        if (!token) return;
        try {
            const data = await apiJson("/api/chats/my", { token });
            const list = (Array.isArray(data) ? data : []).map(applyKnownReadState);
            setConversations(list);

            const initialWatermarks = {};
            const initialSelfWatermarks = {};
            list.forEach(c => {
                const convId = normalizeId(c.conversationId || c.id);
                const participantWatermarks = Array.isArray(c.participantWatermarks) ? c.participantWatermarks : [];
                initialSelfWatermarks[convId] = {
                    lastReadId: Number(c.myLastReadMessageId || 0),
                    lastDeliveredId: Number(c.myLastDeliveredMessageId || 0),
                };
                if (!participantWatermarks.length) return;

                initialWatermarks[convId] = participantWatermarks.reduce((acc, watermark) => {
                    acc[watermark.userId] = {
                        lastReadId: Number(watermark.lastReadMessageId || 0),
                        lastDeliveredId: Number(watermark.lastDeliveredMessageId || 0),
                    };
                    return acc;
                }, {});
            });
            setWatermarks(initialWatermarks);
            setSelfWatermarks(initialSelfWatermarks);
        } catch (err) {
            console.error("Fetch chats failed:", err);
        }
    }, [token, normalizeId, applyKnownReadState]);

    // 3. Fetch Users
    const fetchUsers = useCallback(async () => {
        if (!token) return;
        try {
            const data = await apiJson("/api/users/getall", { token });
            const list = Array.isArray(data) ? data : [];
            setAllUsers(list);
            const me = list.find((u) => u.username === currentUser);
            if (me) {
                setCurrentUserId(me.id);
                return;
            }

            const self = await apiJson(`/api/users/getuser/${encodeURIComponent(currentUser)}`, { token });
            if (self?.id) {
                setCurrentUserId(self.id);
                setAllUsers((prev) => prev.some((u) => String(u.id) === String(self.id)) ? prev : [...prev, self]);
            }
        } catch (err) { console.error("Fetch users failed:", err); }
    }, [token, currentUser]);

    // 4. Fetch Messages
    const fetchMessages = useCallback(async (chatId, page = 0) => {
        if (!token || !chatId || isFetchingRef.current) return;

        if (page === 0) {
            if (abortControllerRef.current) abortControllerRef.current.abort();
            abortControllerRef.current = new AbortController();
        }

        isFetchingRef.current = true;
        setIsLoadingMessages(true);

        try {
            const data = await apiJson(
                `/api/chats/${chatId}/messages?page=${page}&size=${MESSAGE_PAGE_SIZE}`,
                { token }
            );

            const raw = Array.isArray(data) ? data : data?.content || [];
            const sorted = raw.slice().sort((a, b) => new Date(a.sentAt || a.createdAt) - new Date(b.sentAt || b.createdAt));

            if (page === 0) setMessages(sorted);
            else setMessages((prev) => [...sorted, ...prev]);

            setHasMore(raw.length === MESSAGE_PAGE_SIZE);
            setMsgPage(page);
        } catch (err) {
            if (err.name !== 'AbortError') console.error("Fetch messages failed:", err);
        } finally {
            isFetchingRef.current = false;
            setIsLoadingMessages(false);
        }
    }, [token]);

    // 5. Mark Chat Read (ACK) - ΕΔΩ ΕΙΝΑΙ Η ΔΙΟΡΘΩΣΗ
    const markChatRead = useCallback((chatId, messageId = null) => {
        if (!chatId) return;
        const key = normalizeId(chatId);

        // 🚀 ΚΟΦΤΗΣ 1: Αν στείλαμε ήδη αυτό το ID, σταμάτα.
        const previousAckId = Number(lastReadAckRef.current.get(key) || 0);
        if (messageId && Number(messageId) <= previousAckId) return;

        console.log("📢 markChatRead called. Sending ACK for:", messageId);

        setConversations(prev => prev.map(c =>
            normalizeId(c.conversationId || c.id) === key
                ? { ...c, unreadCount: 0, unread_count: 0, myLastReadMessageId: Number(messageId || c.myLastReadMessageId || 0) }
                : c
        ));
        setActiveChat(prev =>
            normalizeId(prev?.conversationId || prev?.id) === key
                ? { ...prev, unreadCount: 0, unread_count: 0, myLastReadMessageId: Number(messageId || prev.myLastReadMessageId || 0) }
                : prev
        );

        if (stompClient?.connected && messageId) {
            lastReadAckRef.current.set(key, Number(messageId)); // Αποθήκευση για να μην το ξαναστείλουμε
            setSelfWatermarks(prev => ({
                ...prev,
                [key]: {
                    ...(prev[key] || {}),
                    lastReadId: Number(messageId),
                }
            }));
            queueAck(chatId, messageId, "READ");
        }
    }, [stompClient, normalizeId, queueAck]);

    // 6. Bump Conversation (New Message Handling)
    const bumpConversation = useCallback((conversationId, { content, createdAt, isIncoming, messageId }) => {
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
                myLastDeliveredMessageId: isIncoming
                    ? Number(old.myLastDeliveredMessageId || 0)
                    : Math.max(Number(old.myLastDeliveredMessageId || 0), Number(messageId || 0)),
                unreadCount: isIncoming && !isActive
                    ? Number(old.unreadCount || 0) + 1
                    : isActive ? 0 : Number(old.unreadCount || 0),
            };
            updated.unread_count = updated.unreadCount;

            const rest = prev.filter((_, i) => i !== idx);
            return [updated, ...rest];
        });

        // Auto-Delivered ACK
        const previousDeliveredId = Number(lastDeliveredAckRef.current.get(key) || 0);
        if (isIncoming && stompClient?.connected && messageId && !String(messageId).startsWith('temp-') && Number(messageId) > previousDeliveredId) {
            lastDeliveredAckRef.current.set(key, Number(messageId));
            setSelfWatermarks(prev => ({
                ...prev,
                [key]: {
                    ...(prev[key] || {}),
                    lastDeliveredId: Number(messageId),
                }
            }));
            queueAck(conversationId, messageId, "DELIVERED");
        }
    }, [stompClient, normalizeId, queueAck]);

    useEffect(() => () => {
        if (ackTimerRef.current) {
            window.clearTimeout(ackTimerRef.current);
            ackTimerRef.current = null;
        }
        flushPendingAcks();
    }, [flushPendingAcks]);

    // Helpers...
    const openDirectChat = useCallback(async (targetUserId) => {
        if (!token || !targetUserId) return;
        let openerUserId = currentUserId;
        if (!openerUserId) {
            try {
                const self = await apiJson(`/api/users/getuser/${encodeURIComponent(currentUser)}`, { token });
                openerUserId = self?.id;
                if (openerUserId) {
                    setCurrentUserId(openerUserId);
                    upsertUser(self);
                }
            } catch (err) {
                console.error("Could not resolve current user before opening direct chat:", err);
            }
        }
        if (!openerUserId) return;

        // 1. Έλεγχος: Υπάρχει ήδη το chat στη λίστα μας;
        // Ψάχνουμε chat που ΔΕΝ είναι group και έχει participant τον targetUserId
        const targetUser = allUsers.find((user) => String(user.id) === String(targetUserId));
        const existingLocal = conversations.find(c =>
            !c.isGroup && (
                c.participants?.some(p => String(p.id) === String(targetUserId)) ||
                (targetUser?.username && c.displayName === targetUser.username)
            )
        );

        if (existingLocal) {
            console.log("📂 Chat already exists locally. Opening...", existingLocal);
            setActiveChat(existingLocal);
            return; // Σταματάμε εδώ, δεν χρειάζεται API call
        }

        // 2. Αν δεν το έχουμε, το ζητάμε από το backend
        try {
            const chatDTO = await apiJson("/api/chats/direct", {
                token,
                method: "POST",
                body: { id1: openerUserId, id2: targetUserId }
            });

            if (chatDTO) {
                console.log("✨ Fetched new/missing chat:", chatDTO);

                // 3. ΕΝΗΜΕΡΩΣΗ ΛΙΣΤΑΣ (ΧΩΡΙΣ ΝΑ ΣΒΗΣΟΥΜΕ ΤΑ ΠΑΛΙΑ)
                setConversations(prev => {
                    const newId = normalizeId(chatDTO.conversationId || chatDTO.id);

                    // Διπλός έλεγχος μήπως ήρθε από Socket εντωμεταξύ
                    if (prev.some(c => normalizeId(c.id || c.conversationId) === newId)) {
                        return prev;
                    }

                    // Βάζουμε το νέο chat ΠΡΩΤΟ και κρατάμε τα παλιά (...prev)
                    return [chatDTO, ...prev];
                });

                setActiveChat(chatDTO);
            }
        } catch (err) {
            console.error("openDirectChat failed:", err);
        }
    }, [token, currentUserId, currentUser, conversations, allUsers, normalizeId, upsertUser]);

    const onGroupCreated = useCallback((newGroup) => {
        if (!newGroup) return;
        upsertConversation(newGroup);
        setActiveChat(newGroup);
    }, [upsertConversation]);

    const toggleMute = useCallback(async (chatId, currentStatus) => {
        const next = !currentStatus;
        setConversations(prev => prev.map(c => normalizeId(c.id) === normalizeId(chatId) ? { ...c, muted: next } : c));
        try {
            await instrumentedFetch(`/api/chats/${chatId}/mute?status=${next}`, {
                method: "PATCH",
                headers: { Authorization: `Bearer ${token}` },
            });
        } catch (err) { console.warn("Mute update failed:", err); }
    }, [token, normalizeId]);

    // --- EFFECTS ---
    useEffect(() => {
        if (!token) return;
        fetchChats();
        fetchUsers();
    }, [token, fetchChats, fetchUsers]);

    const latestIncomingMessageId = useMemo(() => {
        if (!activeChatId || !messages.length) return null;

        for (let i = messages.length - 1; i >= 0; i -= 1) {
            const message = messages[i];
            if (String(message.conversationId ?? activeChatId) !== String(activeChatId)) continue;
            if (message.senderUsername === currentUser) continue;
            if (!message.id || String(message.id).startsWith("temp-") || String(message.id).startsWith("evt-")) continue;
            return message.id;
        }

        return null;
    }, [activeChatId, messages, currentUser]);

    // 🚀 MAIN TRIGGER: Chat Switch
    useEffect(() => {
        if (!activeChatId || !token) return;

        fetchMessages(activeChatId, 0);
    }, [activeChatId, token, fetchMessages]);

    useEffect(() => {
        if (!activeChatId || !latestIncomingMessageId) return;
        const currentUnread = Number(activeChat?.unreadCount ?? activeChat?.unread_count ?? 0);
        const persistedReadId = Number(selfWatermarks[normalizeId(activeChatId)]?.lastReadId || activeChat?.myLastReadMessageId || 0);
        if (currentUnread <= 0 || Number(latestIncomingMessageId) <= persistedReadId) {
            return;
        }
        markChatRead(activeChatId, latestIncomingMessageId);
    }, [activeChatId, latestIncomingMessageId, markChatRead, activeChat, selfWatermarks, normalizeId]);

    return {
        conversations, allUsers, activeChat, messages, watermarks, msgPage, hasMore, isLoadingMessages, activeChatId, currentUserId,
        setActiveChat, setMessages, fetchMessages, markChatRead, bumpConversation, queueAck, toggleMute, openDirectChat, onGroupCreated, onWatermarkUpdate, fetchChats, fetchUsers, upsertConversation, upsertUser
    };
}
