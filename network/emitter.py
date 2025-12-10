"""
micro:bit - Émetteur avec réception de données depuis le PC.

Ce code :
1. Reçoit les données d'unités via USB (depuis bridge.py)
2. Les retransmet par radio aux autres micro:bits
3. Affiche le statut sur les LEDs

Format reçu: UNIT:call_sign,lat,lon,status
"""
from microbit import *
import radio

# Configuration radio
radio.config(channel=7, length=64, power=7, queue=10)
radio.on()

# Compteur pour affichage
units_count = 0
last_display_update = running_time()

def display_status(count):
    """Affiche le nombre d'unités sur les LEDs."""
    if count == 0:
        display.show(Image.SAD)
    elif count < 10:
        display.show(str(count))
    else:
        display.show(Image.HAPPY)

# Buffer pour les données série
serial_buffer = ""

while True:
    # Lire les données du port série (USB) via stdin
    try:
        incoming = uart.any()
        if incoming:
            data = uart.read(incoming)
            if data:
                text = str(data, 'utf-8')
                serial_buffer += text
                
                # Traiter les lignes complètes
                while '\n' in serial_buffer:
                    line, serial_buffer = serial_buffer.split('\n', 1)
                    line = line.strip()
                    
                    if line.startswith("UNIT:"):
                        # Retransmettre par radio
                        radio.send(line)
                        units_count += 1
                        display.show(Image.ARROW_E)
                        sleep(50)
    except:
        pass
    
    # Mise à jour de l'affichage toutes les 3 secondes
    if running_time() - last_display_update > 3000:
        display_status(units_count)
        units_count = 0
        last_display_update = running_time()
    
    sleep(10)
