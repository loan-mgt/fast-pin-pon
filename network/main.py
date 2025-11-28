from microbit import *

# État : False = Libre, True = En intervention
en_intervention = False
last_toggle = running_time()
led_on = True

while True:
    if button_a.was_pressed():
        en_intervention = not en_intervention
        # Réinitialiser l'état d'affichage lors du changement
        led_on = True
        last_toggle = running_time()

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
            
    sleep(100)
