"""
micro:bit - Émetteur avec réception de données depuis le PC.

Ce code :
1. Reçoit les données d'unités via USB (depuis bridge.py)
2. Les retransmet par radio aux autres micro:bits
3. Affiche le statut sur les LEDs

Format reçu: UNIT:call_sign,lat,lon,status

Affichage:
- SAD face : aucune unité reçue de l'API
- Chiffre : nombre d'unités dans le système
- Flèche : envoi en cours
"""
from microbit import *
import radio

# Configuration radio
radio.config(channel=7, length=64, power=7, queue=10)
radio.on()

# Nombre total d'unités reçues dans le dernier cycle
total_units = 0
has_received_data = False

# Buffer pour les données série
serial_buffer = ""

# Afficher SAD au démarrage (en attente de données)
display.show(Image.SAD)

while True:
    # Lire les données du port série (USB)
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
                        total_units += 1
                        has_received_data = True
                        # Afficher flèche pendant l'envoi
                        display.show(Image.ARROW_E)
                        sleep(100)
                        
                        # Après l'envoi, afficher le nombre d'unités
                        if total_units < 10:
                            display.show(str(total_units))
                        else:
                            display.show(Image.HAPPY)
                    
                    elif line == "END":
                        # Fin du cycle - envoyer END au récepteur
                        radio.send("END")
                        # Reset pour le prochain cycle
                        total_units = 0
    except:
        pass
    
    # Si on n'a jamais reçu de données, afficher SAD
    if not has_received_data:
        display.show(Image.SAD)
    
    sleep(10)
