DELETE FROM message_reactions r
WHERE EXISTS (
    SELECT 1
    FROM message_reactions keep
    WHERE keep.message_id = r.message_id
      AND keep.user_id = r.user_id
      AND (
          keep.created_at > r.created_at
          OR (keep.created_at = r.created_at AND keep.id > r.id)
      )
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_message_reactions_message_user'
    ) THEN
        ALTER TABLE message_reactions
            ADD CONSTRAINT uk_message_reactions_message_user
            UNIQUE (message_id, user_id);
    END IF;
END $$;
