import { useEffect, useMemo, useRef, useState } from "react";
import Avatar from "../common/Avatar";
import Icon from "../common/Icon";
import { apiJson, instrumentedFetch } from "../../api/apiJson";
import "./ChatDetails.css";

const formatBytes = (value) => {
    const size = Number(value || 0);
    if (!size) return "0 B";
    const units = ["B", "KB", "MB", "GB"];
    let unitIndex = 0;
    let current = size;
    while (current >= 1024 && unitIndex < units.length - 1) {
        current /= 1024;
        unitIndex += 1;
    }
    return `${current.toFixed(current >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
};

const formatTime = (value) => {
    if (!value) return "";
    return new Date(value).toLocaleString([], {
        day: "numeric",
        month: "short",
        hour: "2-digit",
        minute: "2-digit",
    });
};

const LINK_PATTERN = /\b((?:https?:\/\/|www\.)[^\s<]+)/gi;

const normalizeLink = (value) => value.startsWith("http://") || value.startsWith("https://")
    ? value
    : `https://${value}`;

export default function ChatDetails({
    activeChat,
    messages = [],
    token,
    onClose,
    onToggleMute,
    onOpenMessage,
    onOpenPreview,
}) {
    const [activeTab, setActiveTab] = useState("overview");
    const [searchQuery, setSearchQuery] = useState("");
    const [searchResults, setSearchResults] = useState([]);
    const [isSearching, setIsSearching] = useState(false);
    const [searchError, setSearchError] = useState("");
    const [galleryItems, setGalleryItems] = useState([]);
    const [isGalleryLoading, setIsGalleryLoading] = useState(false);
    const [galleryError, setGalleryError] = useState("");
    const [mediaUrls, setMediaUrls] = useState({});
    const [visibleMediaCount, setVisibleMediaCount] = useState(18);
    const mediaSentinelRef = useRef(null);
    const mediaUrlRegistryRef = useRef(new Map());

    const conversationId = activeChat?.conversationId || activeChat?.id;
    const chatName = activeChat?.displayName || activeChat?.name || "Conversation";
    const isGroup = Boolean(activeChat?.isGroup);
    useEffect(() => {
        setActiveTab("overview");
        setSearchQuery("");
        setSearchResults([]);
        setSearchError("");
        setVisibleMediaCount(18);
    }, [conversationId]);

    useEffect(() => {
        if (!conversationId || !token) return;

        let cancelled = false;
        setIsGalleryLoading(true);
        setGalleryError("");

        apiJson(`/api/files/${conversationId}/gallery`, { token })
            .then((data) => {
                if (cancelled) return;
                setGalleryItems(Array.isArray(data) ? data : []);
            })
            .catch((error) => {
                if (cancelled) return;
                console.error("Gallery fetch failed:", error);
                setGalleryItems([]);
                setGalleryError("Could not load shared media.");
            })
            .finally(() => {
                if (!cancelled) setIsGalleryLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, [conversationId, token]);

    const mediaItems = useMemo(
        () => galleryItems.filter((item) => item.fileCategory === "IMAGE" || item.fileCategory === "VIDEO"),
        [galleryItems]
    );
    const visibleMediaItems = useMemo(
        () => mediaItems.slice(0, visibleMediaCount),
        [mediaItems, visibleMediaCount]
    );

    const fileItems = useMemo(
        () => galleryItems.filter((item) => item.fileCategory !== "IMAGE" && item.fileCategory !== "VIDEO"),
        [galleryItems]
    );
    const links = useMemo(() => {
        const seen = new Set();
        const items = [];

        messages.forEach((message) => {
            const content = String(message?.content || "");
            if (!content) return;

            const matches = content.match(LINK_PATTERN) || [];
            matches.forEach((rawLink) => {
                const href = normalizeLink(rawLink);
                if (seen.has(href)) return;
                seen.add(href);
                items.push({
                    href,
                    display: rawLink,
                    senderUsername: message.senderUsername,
                    createdAt: message.createdAt || message.sentAt,
                    messageId: message.id,
                });
            });
        });

        return items.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
    }, [messages]);

    useEffect(() => {
        mediaUrlRegistryRef.current.forEach((url) => URL.revokeObjectURL(url));
        mediaUrlRegistryRef.current.clear();
        setMediaUrls({});

        return () => {
            mediaUrlRegistryRef.current.forEach((url) => URL.revokeObjectURL(url));
            mediaUrlRegistryRef.current.clear();
        };
    }, [conversationId]);

    useEffect(() => {
        if (!token || visibleMediaItems.length === 0) {
            return undefined;
        }

        const itemsToFetch = visibleMediaItems.filter((item) => !mediaUrlRegistryRef.current.has(item.storageName));
        if (!itemsToFetch.length) {
            return undefined;
        }

        let cancelled = false;

        Promise.all(
            itemsToFetch.map(async (item) => {
                const previewStorageName = item.thumbnailUrl || item.storageName;
                const response = await instrumentedFetch(
                    `/api/files/preview/${encodeURIComponent(previewStorageName)}?conversationId=${encodeURIComponent(item.conversationId)}`,
                    {
                        headers: { Authorization: `Bearer ${token}` },
                    }
                );

                if (!response.ok) {
                    throw new Error(`Preview fetch failed for ${item.storageName}`);
                }

                const blob = await response.blob();
                const objectUrl = URL.createObjectURL(blob);
                return [item.storageName, objectUrl];
            })
        )
            .then((entries) => {
                if (cancelled) {
                    entries.forEach(([, url]) => URL.revokeObjectURL(url));
                    return;
                }

                entries.forEach(([storageName, url]) => {
                    mediaUrlRegistryRef.current.set(storageName, url);
                });

                setMediaUrls((prev) => ({
                    ...prev,
                    ...Object.fromEntries(entries),
                }));
            })
            .catch((error) => {
                if (!cancelled) {
                    console.error("Media preview fetch failed:", error);
                }
            });

        return () => {
            cancelled = true;
        };
    }, [visibleMediaItems, token]);

    useEffect(() => {
        if (activeTab !== "media" || visibleMediaCount >= mediaItems.length || !mediaSentinelRef.current) {
            return undefined;
        }

        const observer = new IntersectionObserver((entries) => {
            const [entry] = entries;
            if (!entry?.isIntersecting) return;
            setVisibleMediaCount((prev) => Math.min(prev + 18, mediaItems.length));
        }, {
            root: null,
            rootMargin: "160px",
            threshold: 0.1,
        });

        observer.observe(mediaSentinelRef.current);
        return () => observer.disconnect();
    }, [activeTab, visibleMediaCount, mediaItems.length]);

    const downloadAttachment = async (item) => {
        try {
            const response = await instrumentedFetch(`/api/files/download/${encodeURIComponent(item.storageName)}`, {
                headers: { Authorization: `Bearer ${token}` },
            });

            if (!response.ok) {
                throw new Error(`Download failed for ${item.storageName}`);
            }

            const blob = await response.blob();
            const objectUrl = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.href = objectUrl;
            link.download = item.originalName || "attachment";
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(objectUrl);
        } catch (error) {
            console.error("Attachment download failed:", error);
        }
    };

    const handleSearch = async (event) => {
        event.preventDefault();

        const trimmed = searchQuery.trim();
        if (!trimmed || !conversationId || !token) {
            setSearchResults([]);
            setSearchError("");
            return;
        }

        setIsSearching(true);
        setSearchError("");

        try {
            const data = await apiJson(
                `/api/messages/chat/${conversationId}/search?query=${encodeURIComponent(trimmed)}`,
                { token }
            );
            setSearchResults(Array.isArray(data) ? data : []);
        } catch (error) {
            console.error("Search failed:", error);
            setSearchResults([]);
            setSearchError("Search failed. Try again.");
        } finally {
            setIsSearching(false);
        }
    };

    return (
        <aside className="conversation-details">
            <div className="details-topbar">
                <span className="details-eyebrow">Chat info</span>
                <button className="details-close" onClick={onClose} aria-label="Close chat details">
                    ×
                </button>
            </div>

            <div className="details-profile">
                <Avatar name={chatName} avatarUrl={activeChat?.avatarUrl} size={72} />
                <h2>{chatName}</h2>
                <p>{isGroup ? "Group conversation" : "Direct conversation"}</p>
            </div>

            <div className="details-actions">
                <button
                    className={`details-action ${activeChat?.muted ? "active" : ""}`}
                    onClick={() => onToggleMute?.(conversationId, activeChat?.muted)}
                >
                    <Icon name={activeChat?.muted ? "bellOff" : "bell"} size={18} />
                    <span>{activeChat?.muted ? "Notifications off" : "Notifications on"}</span>
                </button>
                <button className="details-action" onClick={() => setActiveTab("search")}>
                    <Icon name="search" size={18} />
                    <span>Search in chat</span>
                </button>
                <button className="details-action" onClick={() => setActiveTab("media")}>
                    <Icon name="media" size={18} />
                    <span>Media & files</span>
                </button>
                <button className="details-action" onClick={() => setActiveTab("links")}>
                    <Icon name="link" size={18} />
                    <span>Links</span>
                </button>
            </div>

            <div className="details-tabs">
                <button className={activeTab === "overview" ? "active" : ""} onClick={() => setActiveTab("overview")}>
                    Overview
                </button>
                <button className={activeTab === "search" ? "active" : ""} onClick={() => setActiveTab("search")}>
                    Search
                </button>
                <button className={activeTab === "media" ? "active" : ""} onClick={() => setActiveTab("media")}>
                    Media
                </button>
                <button className={activeTab === "links" ? "active" : ""} onClick={() => setActiveTab("links")}>
                    Links
                </button>
            </div>

            <div className="details-content">
                {activeTab === "overview" && (
                    <div className="details-section">
                        <div className="details-card">
                            <span className="details-label">Shared items</span>
                            <strong>{galleryItems.length}</strong>
                            <small>{mediaItems.length} media, {fileItems.length} files</small>
                        </div>

                        <div className="details-card">
                            <span className="details-label">Latest activity</span>
                            <strong>{formatTime(activeChat?.lastMessageAt) || "No messages yet"}</strong>
                            <small>{activeChat?.lastContent || "This conversation is quiet."}</small>
                        </div>

                        <div className="details-summary">
                            <h4>Quick access</h4>
                            <button className="inline-link" onClick={() => setActiveTab("media")}>
                                View shared media and files
                            </button>
                            <button className="inline-link" onClick={() => setActiveTab("search")}>
                                Search message history
                            </button>
                            <button className="inline-link" onClick={() => setActiveTab("links")}>
                                Browse shared links
                            </button>
                        </div>
                    </div>
                )}

                {activeTab === "search" && (
                    <div className="details-section">
                        <form className="search-form" onSubmit={handleSearch}>
                            <input
                                type="search"
                                value={searchQuery}
                                onChange={(event) => setSearchQuery(event.target.value)}
                                placeholder="Search messages in this chat"
                            />
                            <button type="submit" disabled={isSearching}>
                                {isSearching ? "Searching..." : "Search"}
                            </button>
                        </form>

                        {searchError && <p className="details-error">{searchError}</p>}

                        {!searchError && searchResults.length === 0 && searchQuery.trim() && !isSearching && (
                            <p className="details-empty">No messages matched your search.</p>
                        )}

                        {!searchQuery.trim() && <p className="details-empty">Search by keyword or phrase.</p>}

                        <div className="search-results">
                            {searchResults.map((result) => (
                                <button
                                    key={result.id}
                                    className="search-result"
                                    onClick={() => onOpenMessage?.(result)}
                                >
                                    <div className="search-result-top">
                                        <strong>{result.senderUsername}</strong>
                                        <span>{formatTime(result.createdAt || result.sentAt)}</span>
                                    </div>
                                    <p>{result.content || "Attachment"}</p>
                                </button>
                            ))}
                        </div>
                    </div>
                )}

                {activeTab === "media" && (
                    <div className="details-section">
                        {isGalleryLoading && <p className="details-empty">Loading shared media...</p>}
                        {galleryError && <p className="details-error">{galleryError}</p>}

                        {!isGalleryLoading && !galleryError && (
                            <>
                                <div className="media-block">
                                    <div className="section-heading">
                                        <h4>Media</h4>
                                        <span>{mediaItems.length}</span>
                                    </div>
                                    {mediaItems.length === 0 ? (
                                        <p className="details-empty">No shared images or videos yet.</p>
                                    ) : (
                                        <div className="media-grid">
                                            {visibleMediaItems.map((item) => {
                                                const previewUrl = mediaUrls[item.storageName];
                                                const isVideo = item.fileCategory === "VIDEO";

                                                return (
                                                    <button
                                                        key={item.id}
                                                        type="button"
                                                        className="media-tile"
                                                        title={item.originalName}
                                                        onClick={() => onOpenPreview?.(mediaItems, mediaItems.findIndex((candidate) => candidate.id === item.id))}
                                                    >
                                                        {previewUrl ? (
                                                            isVideo ? (
                                                                <video src={previewUrl} muted playsInline />
                                                            ) : (
                                                                <img src={previewUrl} alt={item.originalName} />
                                                            )
                                                        ) : (
                                                            <div className="media-placeholder">Preview</div>
                                                        )}
                                                        <span>{formatTime(item.sentAt)}</span>
                                                    </button>
                                                );
                                            })}
                                        </div>
                                    )}
                                    {visibleMediaCount < mediaItems.length && (
                                        <div ref={mediaSentinelRef} className="media-loading-sentinel">
                                            Loading more previews...
                                        </div>
                                    )}
                                </div>

                                <div className="media-block">
                                    <div className="section-heading">
                                        <h4>Files</h4>
                                        <span>{fileItems.length}</span>
                                    </div>
                                    {fileItems.length === 0 ? (
                                        <p className="details-empty">No shared files yet.</p>
                                    ) : (
                                        <div className="file-list">
                                            {fileItems.map((item) => (
                                                <button
                                                    key={item.id}
                                                    type="button"
                                                    className="file-row"
                                                    onClick={() => downloadAttachment(item)}
                                                >
                                                    <div>
                                                        <strong>{item.originalName}</strong>
                                                        <small>{item.uploadedBy || "Unknown"} • {formatTime(item.sentAt)}</small>
                                                    </div>
                                                    <span>{formatBytes(item.fileSize)}</span>
                                                </button>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            </>
                        )}
                    </div>
                )}

                {activeTab === "links" && (
                    <div className="details-section">
                        <div className="media-block">
                            <div className="section-heading">
                                <h4>Shared links</h4>
                                <span>{links.length}</span>
                            </div>
                            {links.length === 0 ? (
                                <p className="details-empty">No links found in the loaded messages.</p>
                            ) : (
                                <div className="link-list">
                                    {links.map((link) => (
                                        <div key={link.href} className="link-row">
                                            <button className="link-message-jump" onClick={() => onOpenMessage?.({
                                                id: link.messageId,
                                                senderUsername: link.senderUsername,
                                                createdAt: link.createdAt,
                                                content: messages.find((message) => String(message.id) === String(link.messageId))?.content || "",
                                                conversationId,
                                                attachments: messages.find((message) => String(message.id) === String(link.messageId))?.attachments || [],
                                            })}>
                                                Jump
                                            </button>
                                            <div className="link-main">
                                                <a href={link.href} target="_blank" rel="noreferrer">
                                                    {link.display}
                                                </a>
                                                <small>{link.senderUsername} • {formatTime(link.createdAt)}</small>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                )}
            </div>
        </aside>
    );
}
