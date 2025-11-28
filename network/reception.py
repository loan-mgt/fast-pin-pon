from microbit import *
import radio

radio.config(channel=7, length=64, power=7, queue=10)
radio.on()

while True:
    packet = radio.receive()
    if packet:
        # Exemple de payload : "GPS:45.752341,4.876512,BUSY"
        display.show(Image.HEART)
        print(packet)
    else:
        display.clear()
    sleep(50)