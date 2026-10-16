# Keycloak

Keycloak holds two kinds of things, and each has its own owner:

| What | Owner | How it gets there |
|---|---|---|
| Realm **configuration**: realm settings, clients, roles, service-account permissions | this folder (config as code) | `realm/*.json`, imported by Keycloak on start (`--import-realm`, see `docker-compose.yml`) |
| **Users** | the application | initial admins: `InitialDataBootstrap` (`src/main/resources/bootstrap/initial-data.yml`); everyone else: the user API |

Users are never imported from JSON: the application keys `app_user` by the Keycloak user id, so
only the code path that creates the account may write the row. That is what keeps the two in sync
without hard-coded UUIDs.

## Local setup

1. `docker compose up -d keycloak` - a fresh Keycloak imports `realm/homeenergyregistry-realm.json`.
   An existing realm is left untouched (import skips it); drop the `postgres_keycloak_data` volume
   to re-import.
2. Start the application with the bootstrap on:
   `APP_BOOTSTRAP_ENABLED=true KEYCLOAK_ADMIN_CLIENT_SECRET=local-homeenergyregistry-admin-secret`.
   It creates the start areas and the admins listed in `initial-data.yml`; they log in with
   `KEYCLOAK_INITIAL_PASSWORD` and must change it on first login.

## Changing the realm configuration

Change it in the Keycloak admin console, then run `./export-realm.sh` and commit the updated
`realm/*.json`. The export contains no human users and replaces client secrets with
`local-<clientId>-secret`, so the file is safe to commit and only fit for local use.

Higher environments should not use `--import-realm` (it never updates an existing realm); apply the
same JSON with a declarative tool such as [keycloak-config-cli](https://github.com/adorsys/keycloak-config-cli),
with real secrets injected from the environment.
