#!/bin/sh
set -eu
export PGCONNECT_TIMEOUT=10

for service in inventory order payment; do
    case "$service" in
        inventory) PGPASSWORD=$INVENTORY_DB_PASSWORD ;;
        order) PGPASSWORD=$ORDER_DB_PASSWORD ;;
        payment) PGPASSWORD=$PAYMENT_DB_PASSWORD ;;
    esac
    export PGPASSWORD
    own_db="${service}_db"
    role="${service}_app"
    identity=$(psql -X --no-password --username="$role" --dbname="$own_db" -At \
        --set=ON_ERROR_STOP=1 --command="SELECT current_user || ':' || current_database()")
    test "$identity" = "$role:$own_db"
    privileges=$(psql -X --no-password --username="$role" --dbname="$own_db" -At \
        --set=ON_ERROR_STOP=1 \
        --command="SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls FROM pg_roles WHERE rolname = current_user")
    test "$privileges" = f
    printf '%s\n' "$role -> $own_db: OK; yonetici yetkisi yok"

    for other in inventory order payment; do
        test "$other" != "$service" || continue
        allowed=$(psql -X --no-password --username="$role" --dbname="$own_db" -At \
            --set=ON_ERROR_STOP=1 \
            --command="SELECT has_database_privilege(current_user, '${other}_db', 'CONNECT')")
        test "$allowed" = f
        if psql -X --no-password --username="$role" --dbname="${other}_db" \
            --set=ON_ERROR_STOP=1 --command="SELECT 1" >/dev/null 2>&1; then
            printf '%s\n' "HATA: $role baska servisin veritabanina baglanabildi." >&2
            exit 1
        fi
        printf '%s\n' "$role -> ${other}_db: REDDEDILDI"
    done
done
