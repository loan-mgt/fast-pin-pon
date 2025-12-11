"""
micro:bit - Récepteur/Relais vers le PC.

Ce code :
1. Reçoit les données par radio depuis les unités (unit.py)
2. Affiche le statut sur les LEDs (reste affiché jusqu'au prochain changement)
3. Transmet les données au PC via le port série (USB)

Format reçu/envoyé: UNIT:call_sign,lat,lon,status
Statuts: AVL (available), ERT (en_route), OFF (offline)

Affichage:
- HAPPY : Disponible (AVL)
- Flèche droite : En route (ERT)
- ASLEEP : Hors ligne (OFF)
- Flèche gauche : En attente (aucune donnée reçue)
"""
from microbit import Image, display, sleep
import radio

# Configuration radio (doit correspondre à l'émetteur)
radio.config(channel=7, length=64, power=7, queue=10)
radio.on()

# Images pour les différents statuts
STATUS_IMAGES = {
    "AVL": Image.HAPPY,      # Disponible : content
    "ERT": Image.ARROW_E,    # En route : flèche
    "OFF": Image.ASLEEP,     # Hors ligne : endormi
}

# Dernier statut reçu (pour le garder affiché)
current_status = None


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
    except (ValueError, IndexError, AttributeError):
        pass
    return None


def show_status(status):
    """Affiche l'image correspondant au statut."""
    image = STATUS_IMAGES.get(status, Image.SQUARE)
    display.show(image)


# Afficher au démarrage (en attente de première donnée)
display.show(Image.ARROW_W)

while True:
    packet = radio.receive()

    if packet:
        # Parser le message
        unit_data = parse_unit_message(packet)
        if unit_data:
            call_sign, lat, lon, status = unit_data

            # Mettre à jour l'affichage seulement si le statut change
            if status != current_status:
                show_status(status)
                current_status = status

            # Transmettre au PC via le port série
            print(packet)
        else:
            # Message non reconnu
            print("RAW:" + packet)

    # Le statut reste affiché - pas de retour à l'affichage par défaut

    sleep(50)
