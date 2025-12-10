"""
micro:bit - Récepteur des données d'unités.

Ce code :
1. Reçoit les données par radio depuis l'émetteur
2. Affiche le statut sur les LEDs
3. Imprime les données sur le port série (pour debug)

Format reçu: UNIT:call_sign,lat,lon,status
Statuts: AVL (available), ERT (en_route), ONS (on_site), MNT (maintenance), OFF (offline)
"""
from microbit import Image, display, sleep
import radio

# Configuration radio (doit correspondre à l'émetteur)
radio.config(channel=7, length=64, power=7, queue=10)
radio.on()

# Images pour les différents statuts
STATUS_IMAGES = {
    "AVL": Image.HAPPY,      # Disponible = content
    "ERT": Image.ARROW_E,    # En route = flèche
    "ONS": Image.TARGET,     # Sur site = cible
    "MNT": Image.CONFUSED,   # Maintenance = confus
    "OFF": Image.ASLEEP,     # Hors ligne = endormi
}

def parse_unit_message(message):
    """Parse un message d'unité. Retourne (call_sign, lat, lon, status) ou None."""
    try:
        if not message.startswith("UNIT:"):
            return None
        data = message[5:]  # Enlever "UNIT:"
        parts = data.split(",")
        if len(parts) >= 4:
            call_sign = parts[0]
            lat = float(parts[1])
            lon = float(parts[2])
            status = parts[3]
            return (call_sign, lat, lon, status)
    except:
        pass
    return None

def show_status(status):
    """Affiche l'image correspondant au statut."""
    image = STATUS_IMAGES.get(status, Image.SQUARE)
    display.show(image)

# Compteur d'unités reçues
received_count = 0

while True:
    packet = radio.receive()
    if packet:
        # Parser le message
        unit_data = parse_unit_message(packet)
        if unit_data:
            call_sign, lat, lon, status = unit_data
            # Afficher le statut
            show_status(status)
            # Afficher sur le port série pour debug
            print("RX:" + call_sign + "," + str(lat) + "," + str(lon) + "," + status)
            received_count += 1
        else:
            # Message non reconnu (ancien format GPS), afficher quand même
            print("RX:" + packet)
            display.show(Image.HEART)
    else:
        # Pas de message, afficher le compteur ou éteindre
        if received_count > 0:
            display.show(str(min(received_count, 9)))
        else:
            display.clear()
    
    sleep(50)