const PATHS = {
    bell: (
        <>
            <path d="M18 16v-5a6 6 0 0 0-12 0v5" />
            <path d="M4 16h16" />
            <path d="M10 20h4" />
        </>
    ),
    bellOff: (
        <>
            <path d="M18 14v-3a6 6 0 0 0-8.7-5.3" />
            <path d="M6 11v5" />
            <path d="M4 16h12" />
            <path d="M10 20h4" />
            <path d="M3 3l18 18" />
        </>
    ),
    directory: (
        <>
            <path d="M8 7h8" />
            <path d="M8 12h8" />
            <path d="M8 17h5" />
            <path d="M5 3h14v18H5z" />
        </>
    ),
    eyeOff: (
        <>
            <path d="M3 3l18 18" />
            <path d="M10.6 10.6a2 2 0 0 0 2.8 2.8" />
            <path d="M9.5 5.3A9.2 9.2 0 0 1 12 5c5 0 8 5 8 7a8.4 8.4 0 0 1-1.7 2.7" />
            <path d="M6.6 6.6C4.9 7.9 4 10.1 4 12c0 2 3 7 8 7 1.2 0 2.3-.3 3.2-.8" />
        </>
    ),
    info: (
        <>
            <circle cx="12" cy="12" r="9" />
            <path d="M12 11v5" />
            <path d="M12 8h.01" />
        </>
    ),
    link: (
        <>
            <path d="M10 13a5 5 0 0 0 7.1 0l2-2a5 5 0 0 0-7.1-7.1l-1 1" />
            <path d="M14 11a5 5 0 0 0-7.1 0l-2 2A5 5 0 0 0 12 20l1-1" />
        </>
    ),
    media: (
        <>
            <path d="M4 5h16v14H4z" />
            <path d="M8 13l2.5-2.5L16 16" />
            <path d="M14 10h.01" />
        </>
    ),
    menu: (
        <>
            <path d="M4 7h16" />
            <path d="M4 12h16" />
            <path d="M4 17h16" />
        </>
    ),
    moon: <path d="M20 15.5A8 8 0 0 1 8.5 4 8.5 8.5 0 1 0 20 15.5z" />,
    search: (
        <>
            <circle cx="11" cy="11" r="6" />
            <path d="M16 16l4 4" />
        </>
    ),
    send: (
        <>
            <path d="M4 12l16-8-4 16-3-6-6-2z" />
            <path d="M13 14l7-10" />
        </>
    ),
    sun: (
        <>
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2v2" />
            <path d="M12 20v2" />
            <path d="M4.9 4.9l1.4 1.4" />
            <path d="M17.7 17.7l1.4 1.4" />
            <path d="M2 12h2" />
            <path d="M20 12h2" />
            <path d="M4.9 19.1l1.4-1.4" />
            <path d="M17.7 6.3l1.4-1.4" />
        </>
    ),
    users: (
        <>
            <path d="M16 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2" />
            <circle cx="9.5" cy="7" r="4" />
            <path d="M22 21v-2a4 4 0 0 0-3-3.9" />
            <path d="M16 3.1a4 4 0 0 1 0 7.8" />
        </>
    ),
};

export default function Icon({ name, size = 18, strokeWidth = 2, className = "", title }) {
    return (
        <svg
            className={`icon ${className}`.trim()}
            width={size}
            height={size}
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={strokeWidth}
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden={title ? undefined : "true"}
            role={title ? "img" : undefined}
        >
            {title && <title>{title}</title>}
            {PATHS[name] || null}
        </svg>
    );
}
