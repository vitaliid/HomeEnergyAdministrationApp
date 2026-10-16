#!/usr/bin/env bash
#
# Partial import of the initial 17 admins into Keycloak.
# The password is not stored in the JSON, it comes from KEYCLOAK_INITIAL_PASSWORD.
#
#   KC_CLIENT_SECRET='<secret>' KEYCLOAK_INITIAL_PASSWORD='<pass>' ./import-initial-admins.sh
#
set -euo pipefail

KC_URL="${KC_URL:-localhost:8081}"
KC_REALM="${KC_REALM:-homeerapp}"

TOKEN=$(curl -sf "$KC_URL/realms/$KC_REALM/protocol/openid-connect/token" \
  -d grant_type=client_credentials \
  -d "client_id=${KC_CLIENT_ID:-homeerapp-backend-admin}" \
  -d "client_secret=$KC_CLIENT_SECRET" | jq -r .access_token)

jq --arg pass "$KEYCLOAK_INITIAL_PASSWORD" '
    .ifResourceExists = "SKIP"
  | .users |= map(.credentials = [{type: "password", value: $pass, temporary: false}])
' "$(dirname "$0")/initial-admins.json" |
curl -sf -X POST "$KC_URL/admin/realms/$KC_REALM/partialImport" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  --data-binary @-
