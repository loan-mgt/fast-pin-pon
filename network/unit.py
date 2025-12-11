"""
micro:bit - Simulateur d'unité (véhicule d'urgence).

Ce code simule une unité de pompiers/ambulance :
1. Génère des coordonnées GPS aléatoires dans Lyon au démarrage
2. Gère les changements de statut via les boutons
3. Envoie les données par radio au récepteur avec protocole sécurisé

Protocole de communication:
- Numéro de séquence pour détecter les pertes/doublons
- Checksum CRC8 pour l'intégrité des données
- Clé partagée pour l'authenticité (signature HMAC simplifiée)
- Retransmission si pas d'ACK reçu

Format: SEQ|DATA|CHECKSUM|SIGNATURE
- SEQ: Numéro de séquence (0-255)
- DATA: UNIT:call_sign,lat,lon,status
- CHECKSUM: CRC8 des données
- SIGNATURE: Hash simple avec clé partagée

Contrôles:
- Bouton A (appui court) : Bascule entre Disponible (AVL) et En route (ERT)
- Bouton A (appui long 2s) : Bascule vers/depuis Hors ligne (OFF)
"""
from microbit import display, Image, sleep, running_time, button_a
import radio

# Configuration radio
radio.config(channel=7, length=64, power=7, queue=10)
radio.on()

# CLÉ SECRÈTE PARTAGÉE (doit être identique sur unit.py et relay.py)
SECRET_KEY = "FPP2024"

# IDENTIFIANT FIXE - Change cette valeur pour chaque micro:bit
UNIT_ID = "VSAV01"

# Coordonnées GPS fixes dans Lyon
GPS_LAT = 45.750000
GPS_LON = 4.850000

# États possibles
STATUS_AVL = "AVL"
STATUS_ERT = "ERT"
STATUS_OFF = "OFF"

# Variables globales
current_status = STATUS_AVL
last_send_time = 0
last_blink_time = 0
led_on = True
button_press_start = 0
sequence_number = 0
waiting_for_ack = False
last_message = ""
retry_count = 0
last_retry_time = 0

# Constantes
SEND_INTERVAL = 5000
BLINK_INTERVAL = 300
LONG_PRESS_DURATION = 2000
ACK_TIMEOUT = 500
MAX_RETRIES = 3
MSG_FORMAT = "UNIT:{},{:.6f},{:.6f},{}"


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


def build_secure_message(data):
    """Construit un message sécurisé avec séquence, checksum et signature."""
    global sequence_number
    seq = sequence_number
    crc = compute_crc8(data)
    sig = compute_signature(data, seq)
    # Format: SEQ|DATA|CRC|SIG
    message = "{}|{}|{}|{}".format(seq, data, crc, sig)
    sequence_number = (sequence_number + 1) & 0xFF
    return message, seq


def send_secure(data):
    """Envoie un message sécurisé et attend l'ACK."""
    global waiting_for_ack, last_message, retry_count, last_retry_time
    message, seq = build_secure_message(data)
    radio.send(message)
    last_message = message
    waiting_for_ack = True
    retry_count = 0
    last_retry_time = running_time()


def check_ack():
    """Vérifie si un ACK a été reçu."""
    global waiting_for_ack, retry_count, last_retry_time
    
    if not waiting_for_ack:
        return
    
    # Vérifier les messages reçus
    packet = radio.receive()
    if packet and packet.startswith("ACK:"):
        waiting_for_ack = False
        return
    
    # Timeout - retransmettre si nécessaire
    now = running_time()
    if now - last_retry_time > ACK_TIMEOUT:
        if retry_count < MAX_RETRIES:
            radio.send(last_message)
            retry_count += 1
            last_retry_time = now
        else:
            # Abandon après MAX_RETRIES
            waiting_for_ack = False


# Afficher l'ID au démarrage
display.scroll(UNIT_ID, delay=80)
sleep(500)

# Envoyer le statut initial
data = MSG_FORMAT.format(UNIT_ID, GPS_LAT, GPS_LON, current_status)
send_secure(data)
last_send_time = running_time()

# Boucle principale
while True:
    now = running_time()
    
    # Vérifier les ACK
    check_ack()
    
    # Gestion du bouton A
    if button_a.is_pressed():
        if button_press_start == 0:
            button_press_start = now
    else:
        if button_press_start > 0:
            duration = now - button_press_start
            button_press_start = 0
            
            if duration >= LONG_PRESS_DURATION:
                # Appui long : bascule OFF
                if current_status == STATUS_OFF:
                    current_status = STATUS_AVL
                else:
                    current_status = STATUS_OFF
                data = MSG_FORMAT.format(UNIT_ID, GPS_LAT, GPS_LON, current_status)
                send_secure(data)
                last_send_time = now
            else:
                # Appui court : bascule AVL/ERT (sauf si OFF)
                if current_status != STATUS_OFF:
                    if current_status == STATUS_AVL:
                        current_status = STATUS_ERT
                    else:
                        current_status = STATUS_AVL
                    data = MSG_FORMAT.format(UNIT_ID, GPS_LAT, GPS_LON, current_status)
                    send_secure(data)
                    last_send_time = now
    
    # Affichage selon le statut
    if current_status == STATUS_AVL:
        display.show(Image.SQUARE)
    elif current_status == STATUS_ERT:
        if now - last_blink_time > BLINK_INTERVAL:
            led_on = not led_on
            last_blink_time = now
        if led_on:
            display.show(Image.SQUARE)
        else:
            display.clear()
    else:
        display.clear()
    
    # Envoi périodique
    if now - last_send_time > SEND_INTERVAL:
        data = MSG_FORMAT.format(UNIT_ID, GPS_LAT, GPS_LON, current_status)
        send_secure(data)
        last_send_time = now
    
    sleep(50)
