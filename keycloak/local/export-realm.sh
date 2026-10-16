#!/usr/bin/env bash
#
# Export of realm from Keycloak to file for --import-realm.
#
#   bash:        KC_CLIENT_SECRET='<secret>' ./export-realm.sh > realm-homeerapp.json
#   PowerShell:  $env:KC_CLIENT_SECRET='<secret>'; bash ./export-realm.sh > realm-homeerapp.json
#
set -euo pipefail

KC_URL="${KC_URL:-https://localhost:8081}"
KC_REALM="${KC_REALM:-homeerapp}"

TOKEN=$(curl -sf "$KC_URL/realms/$KC_REALM/protocol/openid-connect/token" \
  -d grant_type=client_credentials \
  -d "client_id=${KC_CLIENT_ID:-homeerapp-backend-admin}" \
  -d "client_secret=$KC_CLIENT_SECRET" | jq -r .access_token)

api() { curl -sf -X "${2:-GET}" -H "Authorization: Bearer $TOKEN" "$KC_URL/admin/realms/$KC_REALM$1"; }

# realm + clients + roles + groups
REALM=$(api "/partial-export?exportClients=true&exportGroupsAndRoles=true" POST)

# pass = username
USERS=$(api "/users?briefRepresentation=false&max=1000" | jq -c '.[]' | while read -r u; do
  api "/users/$(jq -r .id <<<"$u")/role-mappings" | jq --argjson u "$u" '
    ($u | {username, email, firstName, lastName, enabled, emailVerified})
    + { realmRoles:  [(.realmMappings // [])[].name],
        clientRoles: ((.clientMappings // {}) | map_values([.mappings[].name])),
        credentials: [{type: "password", value: $u.username, temporary: false}] }'
done | jq -s .)

jq --argjson users "$USERS" '
    .sslRequired = "none"
  | .users = $users
  | .clients |= map(.secret = "local-" + .clientId + "-secret")
' <<<"$REALM"
