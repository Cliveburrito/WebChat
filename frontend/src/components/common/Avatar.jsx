import PresenceDot from "../presence/PresenceDot.jsx";
import "./Avatar.css";

export default function Avatar({ name, isOnline, size = 45 }) {
    const initial = name ? name.charAt(0).toUpperCase() : "?";

    return (
        <div className="avatar-container" style={{ width: size, height: size }}>
            <div className="avatar-circle">
                {initial}
            </div>
            {isOnline !== undefined && (
                <PresenceDot online={isOnline} className="avatar-dot" />
            )}
        </div>
    );
}