import { useEffect, useState } from "react";
import Avatar from "../common/Avatar";
import "./ProfileSettingsModal.css";

export default function ProfileSettingsModal({ open, profile, onClose, onSaveProfile, onUploadAvatar }) {
    const [displayName, setDisplayName] = useState("");
    const [bio, setBio] = useState("");
    const [error, setError] = useState("");
    const [isSaving, setIsSaving] = useState(false);
    const [isUploading, setIsUploading] = useState(false);

    useEffect(() => {
        if (!open) return;
        setDisplayName(profile?.displayName || "");
        setBio(profile?.bio || "");
        setError("");
    }, [open, profile]);

    if (!open) return null;

    const nameForAvatar = displayName || profile?.username || "You";

    const save = async (event) => {
        event.preventDefault();
        setError("");
        setIsSaving(true);
        try {
            await onSaveProfile?.({ displayName, bio });
            onClose?.();
        } catch (err) {
            setError(err.message || "Profile update failed.");
        } finally {
            setIsSaving(false);
        }
    };

    const upload = async (event) => {
        const file = event.target.files?.[0];
        event.target.value = "";
        if (!file) return;

        setError("");
        setIsUploading(true);
        try {
            await onUploadAvatar?.(file);
        } catch (err) {
            setError(err.message || "Photo upload failed.");
        } finally {
            setIsUploading(false);
        }
    };

    return (
        <div className="profile-modal-backdrop" role="dialog" aria-modal="true" aria-label="Profile settings">
            <form className="profile-modal" onSubmit={save}>
                <div className="profile-modal-header">
                    <div>
                        <span className="profile-eyebrow">Profile</span>
                        <h2>Your info</h2>
                    </div>
                    <button type="button" className="profile-close" onClick={onClose} aria-label="Close profile">
                        x
                    </button>
                </div>

                <div className="profile-avatar-row">
                    <Avatar name={nameForAvatar} avatarUrl={profile?.avatarUrl} size={76} />
                    <label className={`profile-photo-btn ${isUploading ? "disabled" : ""}`}>
                        <input type="file" accept="image/png,image/jpeg,image/webp" onChange={upload} disabled={isUploading} />
                        {isUploading ? "Uploading..." : "Change photo"}
                    </label>
                </div>

                <label className="profile-field">
                    <span>Display name</span>
                    <input
                        value={displayName}
                        onChange={(event) => setDisplayName(event.target.value)}
                        maxLength={80}
                        placeholder={profile?.username || "Your name"}
                    />
                </label>

                <label className="profile-field">
                    <span>About</span>
                    <textarea
                        value={bio}
                        onChange={(event) => setBio(event.target.value)}
                        maxLength={280}
                        rows={4}
                        placeholder="A short status or note"
                    />
                </label>

                {error && <div className="profile-error">{error}</div>}

                <div className="profile-actions">
                    <button type="button" onClick={onClose}>Cancel</button>
                    <button type="submit" className="primary" disabled={isSaving}>
                        {isSaving ? "Saving..." : "Save profile"}
                    </button>
                </div>
            </form>
        </div>
    );
}
