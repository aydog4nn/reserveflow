#!/bin/sh
set -eu
export PGCONNECT_TIMEOUT=10

: "${PGHOST:?PGHOST gerekli}"
: "${PGUSER:?PGUSER gerekli}"
: "${PGPASSWORD:?Yonetici parolasi gerekli}"
: "${INVENTORY_DB_PASSWORD:?Inventory parolasi gerekli}"
: "${ORDER_DB_PASSWORD:?Order parolasi gerekli}"
: "${PAYMENT_DB_PASSWORD:?Payment parolasi gerekli}"

for service in inventory order payment; do
    case "$service" in
        inventory) SERVICE_DB_PASSWORD=$INVENTORY_DB_PASSWORD ;;
        order) SERVICE_DB_PASSWORD=$ORDER_DB_PASSWORD ;;
        payment) SERVICE_DB_PASSWORD=$PAYMENT_DB_PASSWORD ;;
    esac
    export SERVICE_DB_PASSWORD
    psql -X --no-password --dbname=postgres --set=ON_ERROR_STOP=1 \
        --set=database="${service}_db" --set=owner="${service}_app" \
        --file=/provision/provision-role.sql
done

unset SERVICE_DB_PASSWORD

for service in inventory order payment; do
    psql -X --no-password --dbname=postgres --set=ON_ERROR_STOP=1 \
        --set=database="${service}_db" --set=owner="${service}_app" \
        --file=/provision/database-access.sql
    psql -X --no-password --dbname="${service}_db" --set=ON_ERROR_STOP=1 \
        --set=owner="${service}_app" --file=/provision/schema-ownership.sql
done

printf '%s\n' 'Servis veritabanlari ve erisim yetkileri hazir.'
