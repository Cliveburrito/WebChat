import { useEffect, useRef, useMemo } from "react";

export function useChatTopics({
                                  stompClient,
                                  conversations,
                                  currentUser,
                                  activeChatId,
                                  setMessages,
                                  bumpConversation,
                                  markChatRead,
                                  debug = true,
                              }) {
    const subsRef = useRef(new Map());

    // Χρησιμοποιούμε Refs για να "κλέβουμε" τις τελευταίες τιμές χωρίς να πυροδοτούμε το useEffect
    const callbacksRef = useRef({ bumpConversation, markChatRead, setMessages, activeChatId, currentUser });

    useEffect(() => {
        callbacksRef.current = { bumpConversation, markChatRead, setMessages, activeChatId, currentUser };
    });

    // Δημιουργούμε ένα memoized string από IDs.
    // Το useEffect θα ξανατρέξει ΜΟΝΟ αν προστεθεί ή αφαιρεθεί συνομιλία (π.χ. νέο group).
    const conversationIdsKey = useMemo(() => {
        return (conversations || [])
            .map((c) => String(c.conversationId ?? c.id))
            .sort()
            .join(",");
    }, [conversations.length]); // Τρέχει μόνο όταν αλλάζει ο αριθμός των chats

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
                } catch (e) { return; }

                // 1) Message Confirmation
                if (payload?.tempId && payload?.realId) {
                    callbacksRef.current.setMessages?.((prev) =>
                        prev.map((m) => String(m.id) === String(payload.tempId)
                            ? { ...m, id: payload.realId, status: payload.status || "SENT" }
                            : m
                        )
                    );
                    return;
                }

                // 2) Attachment Linked
                if (payload?.messageId && payload?.attachments) {
                    callbacksRef.current.setMessages?.((prev) =>
                        prev.map((m) => String(m.id) === String(payload.messageId)
                            ? { ...m, attachments: payload.attachments }
                            : m
                        )
                    );
                    return;
                }

                // 3) New Message Event
                if (payload?.senderName) {
                    const convId = String(payload.conversationId ?? id);
                    const isIncoming = payload.senderName !== callbacksRef.current.currentUser;
                    const isActive = String(callbacksRef.current.activeChatId ?? "") === convId;

                    const msgDto = {
                        id: payload.tempId || `evt-${Date.now()}`,
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
                        if (isIncoming) callbacksRef.current.markChatRead?.(convId);
                    }
                }
            });

            subsRef.current.set(id, sub);
            if (debug) console.debug("Subscribed to chat:", id);
        }

        // Cleanup για συνομιλίες που αφαιρέθηκαν
        for (const [id, sub] of subsRef.current.entries()) {
            if (!ids.includes(id)) {
                sub.unsubscribe();
                subsRef.current.delete(id);
            }
        }
    }, [stompClient?.connected, conversationIdsKey]); // ΠΟΤΕ δεν βάζουμε το 'conversations' εδώ
}