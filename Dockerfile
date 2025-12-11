FROM grafana/grafana:12.4.0-20082909961

USER root

COPY --chown=472:0 infra/grafana/provisioning/datasources/ /etc/grafana/provisioning/datasources/
COPY --chown=472:0 infra/grafana/provisioning/dashboards/ /etc/grafana/provisioning/dashboards/
COPY --chown=472:0 infra/grafana/dashboards/ /var/lib/grafana/dashboards/

USER 472

EXPOSE 3000