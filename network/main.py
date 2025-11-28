from microbit import *
import random

# État : False = Libre, True = En intervention
en_intervention = False
last_toggle = running_time()
led_on = True

# Timer pour l'envoi GPS
last_gps_sent = running_time()
GPS_INTERVAL = 2000  # Envoi toutes les 2 secondes

def get_random_gps_lyon():
    """
    Génère des coordonnées GPS aléatoires dans la zone de Lyon.
    Latitude : 45.70 à 45.82
    Longitude : 4.76 à 4.93
    """
    lat = 45.70 + random.random() * (45.82 - 45.70)
    lon = 4.76 + random.random() * (4.93 - 4.76)
    return lat, lon

def send_gps_data():
    """
    Simule l'envoi de données GPS via le port série (USB).
    Format: "GPS:lat,lon,status"
    """
    lat, lon = get_random_gps_lyon()
    status = "BUSY" if en_intervention else "FREE"
    # Format CSV simple pour faciliter le parsing côté réception
    print("GPS:{:.6f},{:.6f},{}".format(lat, lon, status))

while True:
    # Gestion du bouton A pour changer l'état d'intervention
    if button_a.was_pressed():
        en_intervention = not en_intervention
        # Réinitialiser l'état d'affichage lors du changement
        led_on = True
        last_toggle = running_time()

    # Gestion de l'affichage LED
    if not en_intervention:
        # Mode "Libre" (Vert) : On allume tout l'écran de manière fixe
        display.show(Image.SQUARE)
    else:
        # Mode "En intervention" (Rouge) : On fait clignoter tout l'écran
        if running_time() - last_toggle > 500:
            led_on = not led_on
            last_toggle = running_time()
        
        if led_on:
            display.show(Image.SQUARE)
        else:
            display.clear()
    
    # Envoi périodique des coordonnées GPS (en continu)
    if running_time() - last_gps_sent > GPS_INTERVAL:
        send_gps_data()
        last_gps_sent = running_time()
            
    sleep(100)
