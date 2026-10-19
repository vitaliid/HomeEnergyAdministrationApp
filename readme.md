Database and start data:
- Liquibase changesets (src/main/resources/db/changelog/changes) hold structure only.
- Reference data (building zones) is upserted from CSV: db/changelog/reference-data.
- Start data (top-level areas, initial admins incl. their Keycloak accounts) is created by the
  application on start when APP_BOOTSTRAP_ENABLED=true; data in src/main/resources/bootstrap/initial-data.yml.
- Keycloak realm configuration: see keycloak/README.md.


Run configuration:
If error to run liquibase add this to VM options
-Duser.timezone=Europe/Kyiv


Greylog:
http://localhost:9009/
with username 'admin' and password 'lgvZGkxLRC'.

SMTP server
test-keycloak
ybpc euth ungd mhet


