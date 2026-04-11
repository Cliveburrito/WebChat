import { useState } from "react";

const REACTION_EMOJIS = ["👍", "❤️", "😂", "😮", "😢", "🔥"];

export default function ReactionPicker({ onReact }) {
    const [isOpen, setIsOpen] = useState(false);

    return (
        <div className="reaction-picker-shell">
            <button
                type="button"
                className="message-side-action"
                onClick={() => setIsOpen((open) => !open)}
                aria-label="React to message"
                title="React"
            >
                <span aria-hidden="true">☺</span>
            </button>
            {isOpen && (
                <div className="reaction-picker">
                    {REACTION_EMOJIS.map((emoji) => (
                        <button
                            key={emoji}
                            type="button"
                            className="reaction-picker-option"
                            onClick={() => {
                                setIsOpen(false);
                                onReact?.(emoji);
                            }}
                        >
                            {emoji}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}
