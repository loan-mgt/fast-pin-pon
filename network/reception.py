"""
micro:bit - Récepteur des données d'unités.

Ce code :
1. Reçoit les données par radio depuis l'émetteur
2. Affiche le statut sur les LEDs
3. Imprime les données sur le port série (pour debug)

Format reçu: UNIT:call_sign,lat,lon,status
Statuts: AVL (available), ERT (en_route), ONS (on_site), MNT (maintenance), OFF (offline)

Affichage:
- Icône du statut pendant 800ms pour chaque unité reçue
- Ensuite : nombre d'unités disponibles (AVL)
- 0 si aucune unité disponible
"""
from microbit import Image, display, sleep, running_time
import radio

# Configuration radio (doit correspondre à l'émetteur)
radio.config(channel=7, length=64, power=7, queue=10)
radio.on()

# Images pour les différents statuts
STATUS_IMAGES = {
    "AVL": Image.HAPPY,      # Disponible : content
    "ERT": Image.ARROW_E,    # En route : flèche
    "ONS": Image.TARGET,     # Sur site : cible
    "MNT": Image.CONFUSED,   # Maintenance : confus
    "OFF": Image.ASLEEP,     # Hors ligne : endormi
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
    except (ValueError, IndexError, AttributeError):
        pass
    return None

def show_status(status):
    """Affiche l'image correspondant au statut."""
    image = STATUS_IMAGES.get(status, Image.SQUARE)
    display.show(image)

# Compteur d'unités disponibles (AVL)
available_count = 0
last_status_time = 0
STATUS_DISPLAY_DURATION = 800  # Afficher l'icône pendant 800ms

# Afficher 0 au démarrage
display.show("0")

while True:
    packet = radio.receive()
    current_time = running_time()
    
    if packet:
        # Parser le message
        unit_data = parse_unit_message(packet)
        if unit_data:
            call_sign, lat, lon, status = unit_data
            
            # Compter les unités disponibles
            if status == "AVL":
                available_count += 1
            
            # Afficher le statut
            show_status(status)
            last_status_time = current_time
            
            # Afficher sur le port série pour debug
            print("RX:" + call_sign + "," + str(lat) + "," + str(lon) + "," + status)
        else:
            # Message spécial END = fin du cycle
            if packet == "END":
                # Afficher le nombre d'unités disponibles
                if available_count < 10:
                    display.show(str(available_count))
                else:
                    display.show(Image.HAPPY)
                print("Available: " + str(available_count))
                # Reset pour le prochain cycle
                available_count = 0
            else:
                print("RX(raw):" + packet)
                display.show(Image.HEART)
                last_status_time = current_time
    else:
        # Afficher le compteur après la durée d'affichage du statut
        if current_time - last_status_time > STATUS_DISPLAY_DURATION:
            if available_count < 10:
                display.show(str(available_count))
            else:
                display.show(Image.HAPPY)
    
    sleep(50)
