import ChatListItem from "../sidebar/ChatListItem.jsx";

export default function ChatList({
                                     conversations,
                                     activeChat,
                                     onSelectChat,
                                     onlineUsers,
                                     watermarks,   // <--- ΝΕΟ PROP
                                     currentUserId // <--- ΝΕΟ PROP
                                 }) {
    if (!conversations?.length) {
        return <p style={{ padding: "20px", color: "var(--text-secondary)", fontSize: "0.9rem" }}>No chats yet</p>;
    }

    return (
        <div className="sidebar-list">
            {conversations.map((chat) => {
                const cid = chat.conversationId || chat.id;
                return (
                    <ChatListItem
                        key={cid}
                        chat={chat}
                        activeChat={activeChat}
                        onSelectChat={onSelectChat}
                        onlineUsers={onlineUsers}
                        watermarks={watermarks}     // <--- Pass down
                        currentUserId={currentUserId} // <--- Pass down
                    />
                );
            })}
        </div>
    );
}