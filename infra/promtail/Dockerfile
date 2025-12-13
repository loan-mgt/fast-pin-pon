FROM grafana/promtail:3.0.0

COPY --chmod=400 --chown=10001:10001 promtail-config.yml /etc/promtail/config.yml

USER 10001