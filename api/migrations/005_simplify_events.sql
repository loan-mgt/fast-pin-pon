-- +migrate Up
-- +migrate StatementBegin
BEGIN;

ALTER TABLE events DROP COLUMN status;
DROP TYPE event_status;

ALTER TABLE interventions ADD CONSTRAINT interventions_event_id_key UNIQUE (event_id);

COMMIT;
-- +migrate StatementEnd

-- +migrate Down
-- +migrate StatementBegin
BEGIN;

CREATE TYPE event_status AS ENUM ('open', 'acknowledged', 'contained', 'closed');
ALTER TABLE events ADD COLUMN status event_status NOT NULL DEFAULT 'open';

ALTER TABLE interventions DROP CONSTRAINT interventions_event_id_key;

COMMIT;
-- +migrate StatementEnd
