FROM prom/prometheus:v3.9.0-rc.0

COPY prometheus.yml /etc/prometheus/prometheus.yml

USER nobody

# Enable remote write receiver for k6 metrics ingestion
CMD ["--config.file=/etc/prometheus/prometheus.yml", "--web.enable-remote-write-receiver"]

EXPOSE 9090
