import { useEffect, useMemo, useState } from "react";
import { instrumentedFetch } from "../../api/apiJson";
import "./AttachmentPreviewModal.css";

async function fetchAttachmentObjectUrl(item, token) {
    if (item?.localUrl) {
        return { objectUrl: item.localUrl, revokeOnCleanup: false };
    }

    if (!item?.storageName) {
        throw new Error("Attachment has no storage name");
    }

    const response = await instrumentedFetch(`/api/files/download/${encodeURIComponent(item.storageName)}`, {
        headers: { Authorization: `Bearer ${token}` },
    });

    if (!response.ok) {
        throw new Error(`Preview fetch failed for ${item.storageName}`);
    }

    const blob = await response.blob();
    return { objectUrl: URL.createObjectURL(blob), revokeOnCleanup: true };
}

export default function AttachmentPreviewModal({
    open,
    attachments = [],
    initialIndex = 0,
    token,
    onClose,
}) {
    const previewableAttachments = useMemo(
        () => attachments.filter((item) => item?.contentType?.startsWith("image/") || item?.contentType?.startsWith("video/") || item?.localUrl),
        [attachments]
    );
    const [index, setIndex] = useState(initialIndex);
    const [previewUrl, setPreviewUrl] = useState("");
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!open) return;
        setIndex(initialIndex);
    }, [open, initialIndex]);

    useEffect(() => {
        if (!open) return undefined;

        const current = previewableAttachments[index];
        if (!current) return undefined;

        let cancelled = false;
        let objectUrlToRevoke = null;

        setIsLoading(true);
        setError("");

        fetchAttachmentObjectUrl(current, token)
            .then(({ objectUrl, revokeOnCleanup }) => {
                if (cancelled) {
                    if (revokeOnCleanup) URL.revokeObjectURL(objectUrl);
                    return;
                }
                setPreviewUrl(objectUrl);
                objectUrlToRevoke = revokeOnCleanup ? objectUrl : null;
            })
            .catch((fetchError) => {
                if (!cancelled) {
                    console.error("Preview fetch failed:", fetchError);
                    setPreviewUrl("");
                    setError("Could not load preview.");
                }
            })
            .finally(() => {
                if (!cancelled) setIsLoading(false);
            });

        return () => {
            cancelled = true;
            if (objectUrlToRevoke) URL.revokeObjectURL(objectUrlToRevoke);
        };
    }, [open, index, previewableAttachments, token]);

    useEffect(() => {
        if (!open) return undefined;

        const handleKeyDown = (event) => {
            if (event.key === "Escape") onClose?.();
            if (event.key === "ArrowLeft") setIndex((prev) => Math.max(0, prev - 1));
            if (event.key === "ArrowRight") setIndex((prev) => Math.min(previewableAttachments.length - 1, prev + 1));
        };

        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [open, onClose, previewableAttachments.length]);

    if (!open || previewableAttachments.length === 0) {
        return null;
    }

    const current = previewableAttachments[index];
    const isVideo = current?.contentType?.startsWith("video/");

    return (
        <div className="preview-modal-backdrop" onClick={onClose}>
            <div className="preview-modal" onClick={(event) => event.stopPropagation()}>
                <button className="preview-close" onClick={onClose} aria-label="Close preview">×</button>

                {previewableAttachments.length > 1 && (
                    <>
                        <button
                            className="preview-nav left"
                            onClick={() => setIndex((prev) => Math.max(0, prev - 1))}
                            disabled={index === 0}
                            aria-label="Previous attachment"
                        >
                            ‹
                        </button>
                        <button
                            className="preview-nav right"
                            onClick={() => setIndex((prev) => Math.min(previewableAttachments.length - 1, prev + 1))}
                            disabled={index === previewableAttachments.length - 1}
                            aria-label="Next attachment"
                        >
                            ›
                        </button>
                    </>
                )}

                <div className="preview-stage">
                    {isLoading && <div className="preview-state">Loading preview…</div>}
                    {!isLoading && error && <div className="preview-state error">{error}</div>}
                    {!isLoading && !error && previewUrl && (
                        isVideo ? (
                            <video src={previewUrl} controls autoPlay className="preview-media" />
                        ) : (
                            <img src={previewUrl} alt={current?.originalName || "attachment preview"} className="preview-media" />
                        )
                    )}
                </div>

                <div className="preview-meta">
                    <strong>{current?.originalName || "Attachment"}</strong>
                    <span>{index + 1} / {previewableAttachments.length}</span>
                </div>
            </div>
        </div>
    );
}
