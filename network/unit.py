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

# Constantes
SEND_INTERVAL = 5000
BLINK_INTERVAL = 300
LONG_PRESS_DURATION = 2000
MSG_FORMAT = "UNIT:{},{:.6f},{:.6f},{}"

# Afficher l'ID au démarrage
display.scroll(UNIT_ID, delay=80)
sleep(500)

# Envoyer le statut initial
msg = MSG_FORMAT.format(UNIT_ID, GPS_LAT, GPS_LON, current_status)
radio.send(msg)
last_send_time = running_time()

# Boucle principale
while True:
    now = running_time()
    
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
                msg = MSG_FORMAT.format(UNIT_ID, GPS_LAT, GPS_LON, current_status)
                radio.send(msg)
                last_send_time = now
            else:
                # Appui court : bascule AVL/ERT (sauf si OFF)
                if current_status != STATUS_OFF:
                    if current_status == STATUS_AVL:
                        current_status = STATUS_ERT
                    else:
                        current_status = STATUS_AVL
                    msg = MSG_FORMAT.format(UNIT_ID, GPS_LAT, GPS_LON, current_status)
                    radio.send(msg)
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
        msg = MSG_FORMAT.format(UNIT_ID, GPS_LAT, GPS_LON, current_status)
        radio.send(msg)
        last_send_time = now
    
    sleep(50)
