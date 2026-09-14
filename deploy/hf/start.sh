#!/bin/bash
# TaskMesh all-in-one start: Postgres + server + worker + nginx UI.
# Kafka/Redis stay OFF (noop beans via env below); Postgres is the whole
# state story. Data is ephemeral. Serves UI + API on $LISTEN_PORT (7860).
set -e

export PGDATA=/tmp/pgdata
DB=taskmesh
DBUSER=taskmesh
DBPASS=taskmesh

as_pg() { su postgres -s /bin/bash -c "$*"; }
PGBIN=/usr/lib/postgresql/16/bin

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  as_pg "$PGBIN/initdb -D $PGDATA -E UTF8"
fi
as_pg "$PGBIN/pg_ctl -D $PGDATA -l /tmp/pg.log -o '-k /tmp -p 5432' start"
until as_pg "$PGBIN/pg_isready -h /tmp -p 5432" >/dev/null 2>&1; do sleep 1; done
as_pg "psql -h /tmp -tc \"SELECT 1 FROM pg_roles WHERE rolname='$DBUSER'\" | grep -q 1 || $PGBIN/createuser -h /tmp $DBUSER"
as_pg "psql -h /tmp -tc \"SELECT 1 FROM pg_database WHERE datname='$DB'\" | grep -q 1 || $PGBIN/createdb -h /tmp -O $DBUSER $DB"
as_pg "psql -h /tmp -c \"ALTER USER $DBUSER PASSWORD '$DBPASS'\""

export PORT=8080
export DB_URL="jdbc:postgresql://localhost:5432/$DB"
export DB_USER="$DBUSER"
export DB_PASS="$DBPASS"
export TASKMESH_KAFKA_ENABLED=false
export TASKMESH_REDIS_ENABLED=false
export TASKMESH_QUEUE_THRESHOLD=500
export TASKMESH_STRATEGY=resource

java -Xmx768m -jar /app/server.jar >/tmp/server.log 2>&1 &
until (echo >/dev/tcp/127.0.0.1/8080) >/dev/null 2>&1; do sleep 2; done

export TASKMESH_SERVER_URL=http://localhost:8080
export TASKMESH_WORKER_CPU=2
export TASKMESH_WORKER_MEMORYMB=4096
export TASKMESH_WORKER_FAILURE_RATE=0
java -Xmx512m -jar /app/worker.jar >/tmp/worker.log 2>&1 &

exec nginx -g 'daemon off;'
