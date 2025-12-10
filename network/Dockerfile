FROM python:3.11-slim

WORKDIR /app

# Installer les dépendances
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copier le script
COPY bridge.py .

# Variables d'environnement par défaut
ENV API_URL=https://api.fast-pin-pon.4loop.org
ENV SERIAL_PORT=""
ENV INTERVAL=5
ENV BAUD_RATE=115200

CMD ["python", "-u", "bridge.py"]
