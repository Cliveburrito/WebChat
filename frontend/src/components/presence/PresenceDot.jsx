export default function PresenceDot({ online, size = 10, className = "" }) {
    return (
        <span
            className={`status-dot ${online ? "online" : "offline"} ${className}`}
            style={{ width: size, height: size }}
        />
    );
}
