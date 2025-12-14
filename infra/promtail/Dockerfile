FROM grafana/promtail:3.0.0

COPY --chmod=400 --chown=10001:10001 promtail-config.yml /etc/promtail/config.yml

RUN mkdir -p /var/log/promtail \
    && chown 10001:10001 /var/log/promtail \
    && mkdir -p /opt/logs \
    && chmod 777 -R /opt/logs

USER 10001