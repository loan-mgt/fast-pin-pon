FROM grafana/loki:3.6

COPY --chmod=400 --chown=loki:loki loki-config.yml /etc/loki/local-config.yml

USER loki