-- +migrate Up
-- Assign microbit_id to all existing units that don't have one
-- Uses sequential numbering: MB001, MB002, ...

WITH ranked_units AS (
    SELECT 
        id,
        ROW_NUMBER() OVER (ORDER BY created_at) + 
            COALESCE((SELECT MAX(CAST(SUBSTRING(microbit_id FROM 3) AS INTEGER)) 
                      FROM units WHERE microbit_id ~ '^MB[0-9]{3}$'), 0) AS new_num
    FROM units
    WHERE microbit_id IS NULL
)
UPDATE units
SET microbit_id = 'MB' || LPAD(ranked_units.new_num::TEXT, 3, '0'),
    updated_at = NOW()
FROM ranked_units
WHERE units.id = ranked_units.id;

-- +migrate Down
-- Remove auto-assigned microbit_ids (only those following MB pattern)
-- Note: This is destructive and should be used with caution
UPDATE units SET microbit_id = NULL WHERE microbit_id ~ '^MB[0-9]{3}$';
