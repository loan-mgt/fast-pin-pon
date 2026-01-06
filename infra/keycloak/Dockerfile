FROM quay.io/keycloak/keycloak:26.4

COPY --chown=keycloak:keycloak realm-export.json /opt/keycloak/data/import/00.master-admin.json
COPY --chown=keycloak:keycloak realm-export-sdmis.json /opt/keycloak/data/import/sdmis-realm-realm.json
COPY --chown=keycloak:keycloak themes /opt/keycloak/themes

ARG GRAFANA_URL="https://dash.fast-pin-pon.4loop.org"
RUN sed -i "s@__GRAFANA_URL__@${GRAFANA_URL}@g" /opt/keycloak/data/import/sdmis-realm-realm.json \
    && chmod -R a-w /opt/keycloak/data/import /opt/keycloak/themes

USER root
RUN mkdir -p /opt/keycloak/data/logs/keycloak \
    && chmod 777 -R /opt/keycloak/data/logs \
    && chown -R keycloak:keycloak /opt/keycloak/data/logs

USER keycloak