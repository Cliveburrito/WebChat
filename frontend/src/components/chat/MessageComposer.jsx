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
        <>
            {!!selectedFiles.length && (
                <div className="composer-files">
                    {selectedFiles.map((file, idx) => (
                        <div key={`${file.name}-${file.size}-${idx}`} className="composer-file-chip">
                            <span className="composer-file-name">{file.name}</span>
                            <button
                                type="button"
                                className="composer-file-remove"
                                onClick={() => onRemoveFile?.(idx)}
                                title="Remove file"
                            >
                                x
                            </button>
                        </div>
                    ))}
                </div>
            )}

            <div className="input-area">
                <input
                    value={value}
                    onChange={onChange}
                    onKeyDown={onKeyDown}
                    placeholder="Type a message..."
                />
                <label className="file-picker-btn" title="Attach files">
                    +
                    <input type="file" multiple onChange={onFileChange} style={{ display: "none" }} />
                </label>
                <button onClick={onSend} disabled={disabled}>
                    Send
                </button>
            </div>
        </>
    );
}
