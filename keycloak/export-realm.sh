#!/usr/bin/env bash
#
# Exports the realm CONFIGURATION (realm settings, clients, roles, groups, and the role mappings of
# client service accounts) into realm/, which Keycloak imports on start (--import-realm).
#
# Human users are deliberately not exported: they are data, owned by the application. The initial
# admins are created by the application bootstrap (org.example.bootstrap.InitialDataBootstrap),
# everyone else through the API - so the Keycloak id and the app_user row never drift apart.
#
#   bash:        KC_CLIENT_SECRET='<secret>' ./export-realm.sh
#   PowerShell:  $env:KC_CLIENT_SECRET='<secret>'; bash ./export-realm.sh
#
set -euo pipefail

KC_URL="${KC_URL:-http://localhost:9090}"
KC_REALM="${KC_REALM:-homeenergyregistry-realm}"
OUT="${OUT:-$(dirname "$0")/realm/$KC_REALM.json}"

TOKEN=$(curl -sf "$KC_URL/realms/$KC_REALM/protocol/openid-connect/token" \
  -d grant_type=client_credentials \
  -d "client_id=${KC_CLIENT_ID:-homeenergyregistry-admin}" \
  -d "client_secret=$KC_CLIENT_SECRET" | jq -r .access_token)

api() { curl -sf -X "${2:-GET}" -H "Authorization: Bearer $TOKEN" "$KC_URL/admin/realms/$KC_REALM$1"; }

# realm + clients + roles + groups
REALM=$(api "/partial-export?exportClients=true&exportGroupsAndRoles=true" POST)

# partial-export drops service-account users, and with them the roles of the client (e.g. the
# backend's manage-users); without these the imported clients could not call the Admin API.
SERVICE_ACCOUNTS=$(jq -c '.clients[] | select(.serviceAccountsEnabled) | {id, clientId}' <<<"$REALM" |
  while read -r client; do
    account=$(api "/clients/$(jq -r .id <<<"$client")/service-account-user")
    api "/users/$(jq -r .id <<<"$account")/role-mappings" | jq \
      --arg username "$(jq -r .username <<<"$account")" \
      --arg clientId "$(jq -r .clientId <<<"$client")" '
        { username: $username,
          enabled: true,
          serviceAccountClientId: $clientId,
          realmRoles:  [(.realmMappings // [])[].name],
          clientRoles: ((.clientMappings // {}) | map_values([.mappings[].name])) }'
  done | jq -s .)

# Secrets come back masked; local ones are predictable on purpose (local-<clientId>-secret).
jq --argjson users "$SERVICE_ACCOUNTS" '
    .sslRequired = "none"
  | .users = $users
  | .clients |= map(if .publicClient then . else .secret = "local-" + .clientId + "-secret" end)
' <<<"$REALM" > "$OUT"

echo "Realm configuration written to $OUT" >&2
