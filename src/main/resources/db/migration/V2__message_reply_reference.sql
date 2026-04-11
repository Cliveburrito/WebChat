ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS reply_to_message_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_messages_reply_to'
    ) THEN
        ALTER TABLE messages
            ADD CONSTRAINT fk_messages_reply_to
            FOREIGN KEY (reply_to_message_id)
            REFERENCES messages (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_messages_reply_to ON messages (reply_to_message_id);
