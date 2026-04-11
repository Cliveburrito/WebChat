import { useEffect, useRef, useMemo } from "react";

export function useChatTopics({
                                  stompClient,
                                  conversations,
                                  allUsers,
                                  currentUser,
                                  currentUserId,
                                  activeChatId,
                                  setMessages,
                                  bumpConversation,
                                  updateConversationMessagePreview,
                                  queueAck,
                                  markChatRead,
                                  upsertConversation,
                                  upsertUser,
                                  onWatermarkUpdate, // <--- Η νέα προσθήκη για τα live ticks
                              debug = false,
                          }) {
    const subsRef = useRef(new Map());
    const pendingIncomingRef = useRef(new Map());

    // Προσθέτουμε το onWatermarkUpdate στο callbacksRef για να το έχουμε φρέσκο
    const callbacksRef = useRef({
        bumpConversation,
        updateConversationMessagePreview,
        queueAck,
        markChatRead,
        setMessages,
        onWatermarkUpdate,
        upsertUser,
        activeChatId,
        currentUser,
        currentUserId,
        allUsers,
        upsertConversation
    });

    useEffect(() => {
        callbacksRef.current = {
            bumpConversation,
            updateConversationMessagePreview,
            queueAck,
            markChatRead,
            setMessages,
            onWatermarkUpdate,
            upsertUser,
            activeChatId,
            currentUser,
            currentUserId,
            allUsers,
            upsertConversation
        };
    });

    const conversationIdsKey = useMemo(() => {
        return (conversations || [])
            .map((c) => String(c.conversationId ?? c.id))
            .sort()
            .join(",");
    }, [conversations]);

    useEffect(() => {
        if (!stompClient?.connected) return;

        const conversationSub = stompClient.subscribe("/user/topic/conversations", (frame) => {
            try {
                const payload = JSON.parse(frame.body);
                callbacksRef.current.upsertConversation?.(payload);
                if (debug) console.debug("Conversation preview received:", payload);
            } catch (e) {
                if (debug) console.debug("Bad conversation preview payload:", e);
            }
        });

        const userDirectorySub = stompClient.subscribe("/topic/users", (frame) => {
            try {
                const payload = JSON.parse(frame.body);
                callbacksRef.current.upsertUser?.(payload);
                if (debug) console.debug("User directory update received:", payload);
            } catch (e) {
                if (debug) console.debug("Bad user directory payload:", e);
            }
        });

        const ids = conversationIdsKey.split(",").filter(Boolean);
        const subs = subsRef.current;

        for (const id of ids) {
            if (subs.has(id)) continue;

            const topic = `/topic/chat/${id}`;
            const sub = stompClient.subscribe(topic, (frame) => {
                let payload;
                try {
                    payload = JSON.parse(frame.body);
                    // eslint-disable-next-line no-unused-vars
                } catch (e) { return; }

                // 1) Message Confirmation (tempId -> realId)
                if (payload?.tempId && payload?.realId) {
                    callbacksRef.current.setMessages?.((prev) =>
                        prev.map((m) => String(m.id) === String(payload.tempId)
                            ? { ...m, id: payload.realId, status: payload.status || "SENT" }
                            : m
                        )
                    );

                    const pendingIncoming = pendingIncomingRef.current.get(String(payload.tempId));
                    if (pendingIncoming) {
                        pendingIncomingRef.current.delete(String(payload.tempId));

                        if (stompClient?.connected) {
                            callbacksRef.current.queueAck?.(pendingIncoming.conversationId, payload.realId, "DELIVERED");

                            if (String(callbacksRef.current.activeChatId ?? "") === String(pendingIncoming.conversationId)) {
                                callbacksRef.current.markChatRead?.(pendingIncoming.conversationId, payload.realId);
                            }
                        }
                    }
                    return;
                }

                // 2) Watermark Update (READ / DELIVERED ticks) - ΝΕΟ!
                // Αυτό έρχεται όταν ο άλλος χρήστης στέλνει Ack
                if (payload?.type === "READ" || payload?.type === "DELIVERED") {
                    if (debug) console.debug("Watermark Update received:", payload);
                    callbacksRef.current.onWatermarkUpdate?.(payload);
                    return;
                }

                // 3) Reaction Update
                if (payload?.messageId && payload?.emoji && payload?.action) {
                    const reactedByCurrentUser = String(payload.userId) === String(callbacksRef.current.currentUserId);
                    callbacksRef.current.setMessages?.((prev) =>
                        prev.map((m) => {
                            if (String(m.id) !== String(payload.messageId)) return m;

                            const existingReactions = Array.isArray(m.reactions) ? m.reactions : [];
                            const existing = existingReactions.find((reaction) => reaction.emoji === payload.emoji);
                            const nextCount = Number(payload.count || 0);

                            if (nextCount <= 0) {
                                return {
                                    ...m,
                                    reactions: existingReactions.filter((reaction) => reaction.emoji !== payload.emoji),
                                };
                            }

                            const nextReaction = {
                                emoji: payload.emoji,
                                count: nextCount,
                                reactedByMe: reactedByCurrentUser ? payload.action === "added" : Boolean(existing?.reactedByMe),
                            };

                            if (!existing) {
                                return { ...m, reactions: [...existingReactions, nextReaction] };
                            }

                            return {
                                ...m,
                                reactions: existingReactions.map((reaction) =>
                                    reaction.emoji === payload.emoji ? nextReaction : reaction
                                ),
                            };
                        })
                    );
                    return;
                }

                // 4) Message lifecycle updates
                if (payload?.type === "MESSAGE_EDITED" || payload?.type === "MESSAGE_DELETED") {
                    callbacksRef.current.setMessages?.((prev) =>
                        prev.map((m) => {
                            if (String(m.id) === String(payload.messageId)) {
                                if (payload.type === "MESSAGE_DELETED") {
                                    return {
                                        ...m,
                                        content: "",
                                        attachments: [],
                                        reactions: [],
                                        deleted: true,
                                        deletedAt: payload.deletedAt || new Date().toISOString(),
                                    };
                                }

                                return {
                                    ...m,
                                    content: payload.content ?? m.content,
                                    editedAt: payload.editedAt || new Date().toISOString(),
                                };
                            }

                            if (String(m.replyToMessageId) === String(payload.messageId)) {
                                return {
                                    ...m,
                                    replyToContent: payload.type === "MESSAGE_DELETED"
                                        ? "Message deleted"
                                        : payload.content ?? m.replyToContent,
                                };
                            }

                            return m;
                        })
                    );
                    callbacksRef.current.updateConversationMessagePreview?.(payload.conversationId ?? id, {
                        messageId: payload.messageId,
                        content: payload.content,
                        editedAt: payload.editedAt,
                        deletedAt: payload.deletedAt,
                    });
                    return;
                }

                // 5) Attachment Linked
                if (payload?.messageId && payload?.attachments) {
                    const attachmentSentAt = payload.attachments?.[0]?.sentAt;
                    callbacksRef.current.setMessages?.((prev) =>
                        prev.map((m) => String(m.id) === String(payload.messageId)
                            ? { ...m, attachments: payload.attachments }
                            : m
                        )
                    );
                    callbacksRef.current.bumpConversation?.(String(payload.conversationId ?? id), {
                        content: "Attachment",
                        createdAt: attachmentSentAt,
                        isIncoming: false,
                        messageId: payload.messageId,
                    });
                    return;
                }

                // 6) New Message Event
                if (payload?.userId && payload?.conversationId) {
                    const convId = String(payload.conversationId ?? id);
                    const senderUsername = callbacksRef.current.allUsers?.find(
                        (user) => String(user.id) === String(payload.userId)
                    )?.username || (String(payload.userId) === String(callbacksRef.current.currentUserId)
                        ? callbacksRef.current.currentUser
                        : "Unknown User");

                    const isIncoming = String(payload.userId) !== String(callbacksRef.current.currentUserId);
                    const isActive = String(callbacksRef.current.activeChatId ?? "") === convId;

                    const msgDto = {
                        // Αν το payload έχει id, το χρησιμοποιούμε (realId), αλλιώς temp
                        id: payload.id || payload.tempId || `evt-${Date.now()}`,
                        conversationId: convId,
                        senderUsername,
                        content: payload.content ?? "",
                        createdAt: payload.sentAt || new Date().toISOString(),
                        status: "SENT",
                        replyToMessageId: payload.replyToMessageId ?? null,
                        replyToSenderUsername: payload.replyToSenderUsername ?? null,
                        replyToContent: payload.replyToContent ?? null,
                        reactions: payload.reactions ?? [],
                    };

                    callbacksRef.current.bumpConversation?.(convId, {
                        content: msgDto.content,
                        createdAt: msgDto.createdAt,
                        isIncoming,
                        messageId: msgDto.id,
                    });
                    if (isIncoming && payload.tempId) {
                        pendingIncomingRef.current.set(String(payload.tempId), {
                            conversationId: convId,
                        });
                    }

                    if (isActive) {
                        callbacksRef.current.setMessages?.((prev) => {
                            if (prev.some((m) => String(m.id) === String(msgDto.id))) return prev;
                            let enrichedMsg = msgDto;
                            if (msgDto.replyToMessageId && !msgDto.replyToContent) {
                                const reply = prev.find((m) => String(m.id) === String(msgDto.replyToMessageId));
                                if (reply) {
                                    enrichedMsg = {
                                        ...msgDto,
                                        replyToSenderUsername: reply.senderUsername,
                                        replyToContent: reply.content || reply.attachments?.[0]?.originalName || "Attachment",
                                    };
                                }
                            }
                            return [...prev, enrichedMsg];
                        });

                        if (isIncoming && msgDto.id && !String(msgDto.id).startsWith("temp-") && !String(msgDto.id).startsWith("evt-")) {
                            callbacksRef.current.markChatRead?.(convId, msgDto.id);
                        }
                    }
                }
            });

            subs.set(id, sub);
            if (debug) console.debug("Subscribed to chat:", id);
        }

        for (const [id, sub] of subs.entries()) {
            if (!ids.includes(id)) {
                sub.unsubscribe();
                subs.delete(id);
            }
        }

        return () => {
            conversationSub.unsubscribe();
            userDirectorySub.unsubscribe();
            for (const [, sub] of subs.entries()) {
                sub.unsubscribe();
            }
            subs.clear();
        };
    }, [stompClient, conversationIdsKey, debug]);
}
