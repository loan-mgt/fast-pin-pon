"""
micro:bit - Récepteur/Relais vers le PC avec protocole sécurisé.

Ce code :
1. Reçoit les données sécurisées par radio depuis les unités (unit.py)
2. Vérifie l'intégrité (CRC) et l'authenticité (signature)
3. Détecte les doublons via le numéro de séquence
4. Envoie un ACK à l'émetteur
5. Affiche le statut sur les LEDs
6. Transmet les données au PC via le port série (USB)

Protocole de communication:
- Format reçu: SEQ|DATA|CRC|SIG
- Vérification du CRC8 pour l'intégrité
- Vérification de la signature pour l'authenticité
- Envoi d'ACK après réception valide
- Rejet des doublons (même séquence déjà reçue)

Statuts: AVL (available), ERT (en_route), OFF (offline)
"""
from microbit import Image, display, sleep
import radio

# Configuration radio (doit correspondre à l'émetteur)
radio.config(channel=7, length=64, power=7, queue=10)
radio.on()

# CLÉ SECRÈTE PARTAGÉE (doit être identique sur unit.py et relay.py)
SECRET_KEY = "FPP2024"

# Images pour les différents statuts
STATUS_IMAGES = {
    "AVL": Image.HAPPY,
    "ERT": Image.ARROW_E,
    "OFF": Image.ASLEEP,
}

# Historique des séquences reçues par unité (pour détecter les doublons)
last_sequences = {}

# Dernier statut reçu
current_status = None


def compute_crc8(data):
    """Calcule un CRC8 simple pour l'intégrité."""
    crc = 0
    for char in data:
        crc = (crc + ord(char)) & 0xFF
        crc = ((crc << 1) | (crc >> 7)) & 0xFF
    return crc


def compute_signature(data, seq):
    """Calcule une signature simple pour l'authenticité."""
    combined = SECRET_KEY + data + str(seq)
    sig = 0
    for i, char in enumerate(combined):
        sig = (sig + ord(char) * (i + 1)) & 0xFFFF
    return sig


def parse_secure_message(packet):
    """Parse et vérifie un message sécurisé. Retourne (seq, data, error) ou None."""
    try:
        parts = packet.split("|")
        if len(parts) != 4:
            return (None, None, "FORMAT")
        
        seq = int(parts[0])
        data = parts[1]
        received_crc = int(parts[2])
        received_sig = int(parts[3])
        
        # Vérifier l'intégrité (CRC)
        computed_crc = compute_crc8(data)
        if computed_crc != received_crc:
            return (None, None, "CRC")
        
        # Vérifier l'authenticité (signature)
        computed_sig = compute_signature(data, seq)
        if computed_sig != received_sig:
            return (None, None, "SIG")
        
        return (seq, data, None)
    except (ValueError, IndexError, AttributeError):
        pass
    return (None, None, "PARSE")


def parse_unit_data(data):
    """Parse les données d'unité. Retourne (call_sign, lat, lon, status) ou None."""
    try:
        if not data.startswith("UNIT:"):
            return None
        content = data[5:]
        parts = content.split(",")
        if len(parts) >= 4:
            return (parts[0], float(parts[1]), float(parts[2]), parts[3])
    except (ValueError, IndexError, AttributeError):
        pass
    return None


def is_duplicate(call_sign, seq):
    """Vérifie si c'est un doublon (même séquence déjà reçue)."""
    if call_sign in last_sequences:
        if last_sequences[call_sign] == seq:
            return True
    last_sequences[call_sign] = seq
    return False


def send_ack(seq):
    """Envoie un accusé de réception."""
    radio.send("ACK:{}".format(seq))


def show_status(status):
    """Affiche l'image correspondant au statut."""
    image = STATUS_IMAGES.get(status, Image.SQUARE)
    display.show(image)


# Afficher au démarrage (en attente)
display.show(Image.ARROW_W)

while True:
    packet = radio.receive()

    if packet:
        # Ignorer les ACK (destinés aux unités)
        if packet.startswith("ACK:"):
            continue
        
        # Parser le message sécurisé
        seq, data, error = parse_secure_message(packet)
        
        if error:
            # Message rejeté - afficher la raison
            print("REJECT:{}:{}".format(error, packet[:30]))
            continue
        
        # Parser les données d'unité
        unit_data = parse_unit_data(data)
        if unit_data:
            call_sign, lat, lon, status = unit_data
            
            # Envoyer ACK
            send_ack(seq)
            
            # Vérifier si c'est un doublon
            if is_duplicate(call_sign, seq):
                print("REJECT:DUP:{}".format(call_sign))
                continue
            
            # Nouveau message valide
            if status != current_status:
                show_status(status)
                current_status = status
            
            # Transmettre au PC (format original pour compatibilité)
            print(data)

    sleep(50)
