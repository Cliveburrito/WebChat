import { useEffect, useRef, useMemo } from "react";

export function useChatTopics({
                                  stompClient,
                                  conversations,
                                  currentUser,
                                  activeChatId,
                                  setMessages,
                                  bumpConversation,
                                  markChatRead,
                                  onWatermarkUpdate, // <--- Η νέα προσθήκη για τα live ticks
                                  debug = true,
                              }) {
    const subsRef = useRef(new Map());

    // Προσθέτουμε το onWatermarkUpdate στο callbacksRef για να το έχουμε φρέσκο
    const callbacksRef = useRef({
        bumpConversation,
        markChatRead,
        setMessages,
        onWatermarkUpdate,
        activeChatId,
        currentUser
    });

    useEffect(() => {
        callbacksRef.current = {
            bumpConversation,
            markChatRead,
            setMessages,
            onWatermarkUpdate,
            activeChatId,
            currentUser
        };
    });

    // eslint-disable-next-line react-hooks/preserve-manual-memoization
    const conversationIdsKey = useMemo(() => {
        return (conversations || [])
            .map((c) => String(c.conversationId ?? c.id))
            .sort()
            .join(",");
    }, [conversations.length]);

    useEffect(() => {
        if (!stompClient?.connected) return;

        const ids = conversationIdsKey.split(",").filter(Boolean);

        for (const id of ids) {
            if (subsRef.current.has(id)) continue;

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
                    return;
                }

                // 2) Watermark Update (READ / DELIVERED ticks) - ΝΕΟ!
                // Αυτό έρχεται όταν ο άλλος χρήστης στέλνει Ack
                if (payload?.type === "READ" || payload?.type === "DELIVERED") {
                    if (debug) console.debug("Watermark Update received:", payload);
                    callbacksRef.current.onWatermarkUpdate?.(payload);
                    return;
                }

                // 3) Attachment Linked
                if (payload?.messageId && payload?.attachments) {
                    callbacksRef.current.setMessages?.((prev) =>
                        prev.map((m) => String(m.id) === String(payload.messageId)
                            ? { ...m, attachments: payload.attachments }
                            : m
                        )
                    );
                    return;
                }

                // 4) New Message Event
                if (payload?.senderName) {
                    const convId = String(payload.conversationId ?? id);
                    const isIncoming = payload.senderName !== callbacksRef.current.currentUser;
                    const isActive = String(callbacksRef.current.activeChatId ?? "") === convId;

                    const msgDto = {
                        // Αν το payload έχει id, το χρησιμοποιούμε (realId), αλλιώς temp
                        id: payload.id || payload.tempId || `evt-${Date.now()}`,
                        conversationId: convId,
                        senderUsername: payload.senderName,
                        content: payload.content ?? "",
                        createdAt: payload.sentAt || new Date().toISOString(),
                        status: "SENT",
                    };

                    callbacksRef.current.bumpConversation?.(convId, {
                        content: msgDto.content,
                        createdAt: msgDto.createdAt,
                        isIncoming,
                    });

                    if (isActive) {
                        callbacksRef.current.setMessages?.((prev) => {
                            if (prev.some((m) => String(m.id) === String(msgDto.id))) return prev;
                            return [...prev, msgDto];
                        });

                        // Αν είμαστε ήδη στο chat, στέλνουμε το σήμα "READ" αμέσως
                        // Πλέον περνάμε και το msgDto.id για να ξέρει ο server το ακριβές watermark
                        if (isIncoming) {
                            callbacksRef.current.markChatRead?.(convId, msgDto.id);
                        }
                    }
                }
            });

            subsRef.current.set(id, sub);
            if (debug) console.debug("Subscribed to chat:", id);
        }

        for (const [id, sub] of subsRef.current.entries()) {
            if (!ids.includes(id)) {
                sub.unsubscribe();
                subsRef.current.delete(id);
            }
        }
    }, [stompClient?.connected, conversationIdsKey]);
}