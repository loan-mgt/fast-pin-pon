#!/bin/sh
set -e

# This script runs during the initial database creation (initdb)
# It restores the pre-calculated routing data.

export PGUSER="$POSTGRES_USER"
DUMP_BINARY="/routing_data.dump"

if [ -f "$DUMP_BINARY" ]; then
    echo "Restoring Lyon routing data (custom format) from $DUMP_BINARY into $POSTGRES_DB..."
    pg_restore --dbname="$POSTGRES_DB" --no-owner --role="$POSTGRES_USER" "$DUMP_BINARY"
    echo "Routing data restoration complete."
else
    echo "No routing data dump found, skipping automatic import."
fi
