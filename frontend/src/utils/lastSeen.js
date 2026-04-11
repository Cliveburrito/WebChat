export function formatLastSeen(lastSeenAt) {
    if (!lastSeenAt) return "";

    const seenAt = new Date(lastSeenAt);
    if (Number.isNaN(seenAt.getTime())) return "";

    const diffMs = Date.now() - seenAt.getTime();
    if (diffMs < 60_000) return "Last seen just now";

    const diffMinutes = Math.floor(diffMs / 60_000);
    if (diffMinutes < 60) {
        return `Last seen ${diffMinutes}m ago`;
    }

    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) {
        return `Last seen ${diffHours}h ago`;
    }

    const diffDays = Math.floor(diffHours / 24);
    if (diffDays === 1) return "Last seen yesterday";
    if (diffDays < 7) return `Last seen ${diffDays}d ago`;

    return `Last seen ${seenAt.toLocaleDateString([], { month: "short", day: "numeric" })}`;
}
