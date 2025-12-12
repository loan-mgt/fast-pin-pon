FROM quay.io/keycloak/keycloak:26.4

ARG GRAFANA_URL="https://dash.fast-pin-pon.4loop.org"

COPY --chown=keycloak:keycloak realm-export.json /opt/keycloak/data/import/00.master-admin.json

COPY --chown=keycloak:keycloak realm-export-sdmis.json /opt/keycloak/data/import/sdmis-realm-realm.json

RUN sed -i "s@__GRAFANA_URL__@${GRAFANA_URL}@g" /opt/keycloak/data/import/sdmis-realm-realm.json
