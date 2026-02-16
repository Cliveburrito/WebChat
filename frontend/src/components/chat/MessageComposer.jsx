import "./MessageComposer.css";

export default function MessageComposer({
                                            value,
                                            onChange,
                                            onKeyDown,
                                            selectedFiles = [],
                                            onFileChange,
                                            onRemoveFile,
                                            onSend,
                                            disabled,
                                        }) {
    return (
        <div className="composer-container">
            {!!selectedFiles.length && (
                <div className="composer-files">
                    {selectedFiles.map((file, idx) => (
                        <div key={`${file.name}-${idx}`} className="composer-file-chip">
                            <span className="composer-file-name">{file.name}</span>
                            <button type="button" onClick={() => onRemoveFile?.(idx)}>✕</button>
                        </div>
                    ))}
                </div>
            )}

            <div className="input-row">
                {/* Κρυφό input και label που λειτουργεί ως button */}
                <input
                    type="file"
                    id="attach-file"
                    multiple
                    onChange={onFileChange}
                    style={{ display: "none" }}
                />
                <label htmlFor="attach-file" className="icon-btn attach-btn" title="Attach files">
                    +
                </label>

                <textarea
                    className="main-input"
                    rows="1"
                    value={value}
                    onChange={onChange}
                    onKeyDown={onKeyDown}
                    placeholder="Type a message..."
                />

                <button
                    className={`send-btn ${disabled ? 'disabled' : ''}`}
                    onClick={onSend}
                    disabled={disabled}
                >
                    ➤
                </button>
            </div>
        </div>
    );
}