"""
micro:bit - Simulateur d'unité (véhicule d'urgence).

Ce code simule une unité de pompiers/ambulance :
1. Génère des coordonnées GPS aléatoires dans Lyon au démarrage
2. Gère les changements de statut via les boutons
3. Envoie les données par radio au récepteur

Contrôles:
- Bouton A (appui court) : Bascule entre Disponible (AVL) et En route (ERT)
- Bouton A (appui long 2s) : Bascule vers/depuis Hors ligne (OFF)

Affichage:
- Square fixe : Disponible (AVL)
- Square clignotant : En route (ERT)
- Écran éteint : Hors ligne (OFF)

Format envoyé: UNIT:call_sign,lat,lon,status
"""
from microbit import display, Image, sleep, running_time, button_a
import radio
import random

# Configuration radio
radio.config(channel=7, length=64, power=7, queue=10)
radio.on()

# Identifiant unique de l'unité (basé sur un ID aléatoire)
UNIT_ID = "U" + str(random.randint(100, 999))

# Générer des coordonnées GPS aléatoires dans Lyon (une seule fois)
# Latitude: 45.700-45.800, Longitude: 4.780-4.900
GPS_LAT = 45.700 + random.random() * 0.100
GPS_LON = 4.780 + random.random() * 0.120

# États possibles
STATUS_AVL = "AVL"  # Disponible
STATUS_ERT = "ERT"  # En route
STATUS_OFF = "OFF"  # Hors ligne

# État actuel
current_status = STATUS_AVL
last_status_sent = None

# Pour le clignotement
last_blink_time = running_time()
led_on = True
BLINK_INTERVAL = 300  # ms

# Pour détecter l'appui long
button_press_start = 0
LONG_PRESS_DURATION = 2000  # 2 secondes

# Intervalle d'envoi régulier
last_send_time = 0
SEND_INTERVAL = 5000  # 5 secondes


def build_message():
    """Construit le message à envoyer."""
    return "UNIT:{},{:.6f},{:.6f},{}".format(UNIT_ID, GPS_LAT, GPS_LON, current_status)


def send_status():
    """Envoie le statut actuel par radio."""
    global last_status_sent, last_send_time
    message = build_message()
    radio.send(message)
    last_status_sent = current_status
    last_send_time = running_time()


def update_display():
    """Met à jour l'affichage selon le statut."""
    global led_on, last_blink_time
    
    current_time = running_time()
    
    if current_status == STATUS_AVL:
        # Disponible : square fixe
        display.show(Image.SQUARE)
    elif current_status == STATUS_ERT:
        # En route : square clignotant
        if current_time - last_blink_time > BLINK_INTERVAL:
            led_on = not led_on
            last_blink_time = current_time
        if led_on:
            display.show(Image.SQUARE)
        else:
            display.clear()
    elif current_status == STATUS_OFF:
        # Hors ligne : écran éteint
        display.clear()


def handle_button():
    """Gère les appuis sur le bouton A."""
    global current_status, button_press_start
    
    if button_a.is_pressed():
        if button_press_start == 0:
            button_press_start = running_time()
    else:
        if button_press_start > 0:
            press_duration = running_time() - button_press_start
            button_press_start = 0
            
            if press_duration >= LONG_PRESS_DURATION:
                # Appui long : basculer vers/depuis OFF
                if current_status == STATUS_OFF:
                    current_status = STATUS_AVL
                else:
                    current_status = STATUS_OFF
                send_status()  # Envoyer immédiatement
            else:
                # Appui court : basculer entre AVL et ERT (sauf si OFF)
                if current_status != STATUS_OFF:
                    if current_status == STATUS_AVL:
                        current_status = STATUS_ERT
                    else:
                        current_status = STATUS_AVL
                    send_status()  # Envoyer immédiatement


# Afficher le statut initial et envoyer
display.scroll(UNIT_ID, delay=80)
sleep(500)
send_status()

while True:
    # Gérer les boutons
    handle_button()
    
    # Mettre à jour l'affichage
    update_display()
    
    # Envoi périodique (toutes les 5 secondes)
    if running_time() - last_send_time > SEND_INTERVAL:
        send_status()
    
    sleep(50)
