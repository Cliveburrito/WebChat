import ChatListItem from "./ChatListItem";

export default function ChatList({ conversations, activeChat, onSelectChat, onlineUsers }) {
    if (!conversations?.length) {
        return <p style={{ padding: "20px", color: "var(--text-secondary)", fontSize: "0.9rem" }}>No chats yet</p>;
    }

    return (
        <>
            {conversations.map((chat) => {
                const cid = chat.conversationId || chat.id;
                return (
                    <ChatListItem
                        key={cid}
                        chat={chat}
                        activeChat={activeChat}
                        onSelectChat={onSelectChat}
                        onlineUsers={onlineUsers}
                    />
                );
            })}
        </>
    );
}
