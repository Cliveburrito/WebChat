// ==========================================
// USER & AUTHENTICATION DOMAIN
// ==========================================
export namespace AuthDTO {
    export interface LoginRequest {
        username: string;
        password?: string; // Optional if you clear it immediately after sending
    }

    export interface RegisterRequest {
        username: string;
        email: string;
        password?: string;
    }
}

export namespace UserDTO {
    export interface Response {
        id: number;
        username: string;
        email: string;
        displayName: string | null;
        bio: string | null;
        avatarUrl: string | null;
    }
}

// ==========================================
// CONVERSATION DOMAIN
// ==========================================
export namespace ConversationDTO {
    export interface Response {
        conversationId: number;
        displayName: string;
        avatarUrl: string | null;
        lastContent: string | null;
        unreadCount: number;
        lastMessageAt: string | null; // ISO-8601 string
        lastMessageId: number | null;
        lastSenderId: number | null;
        isGroup: boolean;
        muted: boolean;
    }

    // Maps to your ChatListRow interface
    export interface ListRow extends Response {
        sidebarSnippet?: string; // Derived from the default Java method
    }

    export interface CreateDirectRequest {
        id1: number;
        id2: number;
    }

    export interface CreateGroupRequest {
        groupName: string;
        memberIds: number[];
    }
}

// ==========================================
// ATTACHMENT DOMAIN
// ==========================================
export namespace AttachmentDTO {
    export interface Info {
        id: number;
        storageName: string;
        originalName: string;
        contentType: string;
        fileCategory: 'IMAGE' | 'VIDEO' | 'AUDIO' | 'FILE';
        fileSize: number;
        thumbnailUrl: string | null;
        uploadedBy: string | null;
        messageId: number | null;
        conversationId: number | null;
        sentAt: string | null;
    }

    export interface LinkedEvent {
        messageId: number;
        conversationId: number;
        attachments: Info[];
    }

    export interface LinkTask {
        messageId: number;
        conversationId: number;
        storageNames: string[];
        originalNames: string[];
    }
}

// ==========================================
// MESSAGE DOMAIN
// ==========================================
export namespace MessageDTO {
    export interface Request {
        content: string;
        tempId: string;
    }

    export interface Response {
        id: number;
        content: string;
        createdAt: string;
        senderUsername: string;
        conversationId: number;
        attachments: AttachmentDTO.Info[];
    }

    export interface Event {
        tempId: string;
        content: string;
        userId: number;
        conversationId: number;
        sentAt: string;
    }

    export interface Confirmation {
        tempId: string;
        realId: number;
        status: string;
    }

    export interface AckRequest {
        conversationId: number;
        messageId: number;
        type: 'READ' | 'DELIVERED';
    }

    export interface Watermark {
        conversationId: number;
        userId: number;
        messageId: number;
        type: 'READ' | 'DELIVERED';
    }
}

// ==========================================
// COMMON / SYSTEM DOMAIN
// ==========================================
export namespace CommonDTO {
    export interface ErrorResponse {
        status: number;
        message: string;
        timestamp: number;
    }
}
