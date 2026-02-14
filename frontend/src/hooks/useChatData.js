import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { apiJson } from "../api/apiJson";

export function useChatData({ token, currentUser }) {
    const [conversations, setConversations] = useState([]);
    const [allUsers, setAllUsers] = useState([]);
    const [activeChat, setActiveChat] = useState(null);
    const [messages, setMessages] = useState([]);
    const [msgPage, setMsgPage] = useState(0);
    const [hasMore, setHasMore] = useState(true);
    const [currentUserId, setCurrentUserId] = useState(null);

    const activeChatRef = useRef(null);
    useEffect(() => {
        activeChatRef.current = activeChat;
    }, [activeChat]);

    const normalizeId = (x) => String(x ?? "");

    // ✅ keeps same behavior; used by openDirectChat and anywhere else
    const fetchChats = useCallback(async () => {
        if (!token) return;
        try {
            const data = await apiJson("/api/chats/my", { token });
            setConversations(Array.isArray(data) ? data : []);
        } catch (err) {
            console.error("Fetch chats failed:", err);
        }
    }, [token]);

    // ✅ keeps same behavior; used by openDirectChat if needed
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

    const fetchMessages = useCallback(
        async (chatId, page = 0) => {
            if (!token || !chatId) return;
            try {
                const data = await apiJson(`/api/chats/${chatId}/messages?page=${page}&size=20`, { token });
                const raw = Array.isArray(data) ? data : data?.content || [];
                const sorted = raw
                    .slice()
                    .sort((a, b) => new Date(a.sentAt || a.createdAt) - new Date(b.sentAt || b.createdAt));

                if (page === 0) setMessages(sorted);
                else setMessages((prev) => [...sorted, ...prev]);

                setHasMore(raw.length === 20);
                setMsgPage(page);
            } catch (err) {
                console.error("Fetch messages failed:", err);
            }
        },
        [token]
    );

    const markChatRead = useCallback(
        async (chatId) => {
            if (!token || !chatId) return;
            const key = normalizeId(chatId);

            setConversations((prev) =>
                prev.map((c) =>
                    normalizeId(c.conversationId || c.id) === key && (c.unreadCount > 0 || c.unread_count > 0)
                        ? { ...c, unreadCount: 0, unread_count: 0 }
                        : c
                )
            );

            try {
                await apiJson(`/api/chats/${chatId}/read`, { token, method: "POST" });
            } catch (err) {
                console.warn("markChatRead failed (ignored):", err);
            }
        },
        [token]
    );

    const bumpConversation = useCallback((conversationId, { content, createdAt, isIncoming }) => {
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
                    ? Number(old.unreadCount || 0) + 1
                    : isActive
                        ? 0
                        : old.unreadCount,
            };

            const rest = prev.filter((_, i) => i !== idx);
            return [updated, ...rest];
        });
    }, []);

    const openDirectChat = useCallback(
        async (targetUserId) => {
            if (!token || !targetUserId || !currentUserId) {
                console.error("Cannot open chat: Missing IDs", { token: !!token, targetUserId, currentUserId });
                return;
            }

            try {
                // Το Backend τώρα επιστρέφει το ConversationResponse DTO
                const chatDTO = await apiJson("/api/chats/direct", {
                    token,
                    method: "POST",
                    body: {
                        id1: currentUserId,
                        id2: targetUserId
                    }
                });

                if (chatDTO) {
                    console.log("Direct Chat Opened (DTO):", chatDTO);

                    // 1. Update List Locally (Fast)
                    setConversations(prev => {
                        const chatId = chatDTO.conversationId || chatDTO.id;
                        // Avoid duplicates
                        const exists = prev.find(c => normalizeId(c.conversationId || c.id) === normalizeId(chatId));
                        if (exists) return prev;
                        // Add new chat to top
                        return [chatDTO, ...prev];
                    });

                    // 2. Open Chat Window
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
                prev.map((c) =>
                    normalizeId(c.conversationId || c.id) === normalizeId(chatId) ? { ...c, muted: next } : c
                )
            );

            if (normalizeId(activeChat?.id || activeChat?.conversationId) === normalizeId(chatId)) {
                setActiveChat((prev) => ({ ...prev, muted: next }));
            }

            try {
                await fetch(`/api/chats/${chatId}/mute?status=${next}`, {
                    method: "PATCH",
                    headers: { Authorization: `Bearer ${token}` },
                });
            } catch (err) {
                console.warn("toggleMute failed (ignored):", err);
            }
        },
        [token, activeChat]
    );

    // ✅ Initial load (NO calling setState-indirect callbacks -> no lint warning)
    useEffect(() => {
        if (!token) return;

        let alive = true;

        (async () => {
            try {
                const chats = await apiJson("/api/chats/my", { token });
                if (alive) setConversations(Array.isArray(chats) ? chats : []);
            } catch (err) {
                console.error("Initial fetch chats failed:", err);
            }

            try {
                const users = await apiJson("/api/users/getall", { token });
                if (!alive) return;

                const list = Array.isArray(users) ? users : [];
                setAllUsers(list);

                const me = list.find((u) => u.username === currentUser);
                if (me) setCurrentUserId(me.id);
            } catch (err) {
                console.error("Initial fetch users failed:", err);
            }
        })();

        return () => {
            alive = false;
        };
    }, [token, currentUser]);

    const currentChatId = activeChat?.conversationId || activeChat?.id;

    useEffect(() => {
        if (!currentChatId || !token) return;

        const timer = setTimeout(() => {
            fetchMessages(currentChatId, 0);
            markChatRead(currentChatId);
        }, 0);

        return () => clearTimeout(timer);
    }, [currentChatId, token, fetchMessages, markChatRead]);

    const activeChatId = useMemo(() => activeChat?.conversationId || activeChat?.id, [activeChat]);

    return {
        conversations,
        allUsers,
        activeChat,
        messages,
        msgPage,
        hasMore,
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

        // keep exports if you were using them elsewhere
        fetchChats,
        fetchUsers,
    };
}
