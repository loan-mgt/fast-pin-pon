FROM quay.io/keycloak/keycloak:26.4

COPY --chown=keycloak:keycloak realm-export.json /opt/keycloak/data/import/00.master-admin.json

COPY --chown=keycloak:keycloak realm-export-sdmis.json /opt/keycloak/data/import/sdmis-realm-realm.json

ARG GRAFANA_URL="https://dash.fast-pin-pon.4loop.org"

RUN sed -i "s@__GRAFANA_URL__@${GRAFANA_URL}@g" /opt/keycloak/data/import/sdmis-realm-realm.json

USER keycloak