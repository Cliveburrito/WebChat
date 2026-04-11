ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP(6) WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_messages_deleted_at ON messages (deleted_at);
