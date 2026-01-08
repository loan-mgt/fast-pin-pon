# micro:bit Relay - Radio to UART (PC2)
# Reçoit données radio du unit, envoie via UART au bridge_receiver
from microbit import display, Image, sleep, uart
import radio

# Radio configuration - MUST match unit.py
radio.config(channel=7, length=128, power=7)
radio.on()

# Startup
display.show(Image.YES)
sleep(500)
display.show(Image.ARROW_W)

while True:
    # Receive radio messages and forward to UART (print)
    msg = radio.receive()
    while msg:
        display.show(Image.DIAMOND)
        # Forward message to UART
        print(msg)
        display.show(Image.ARROW_E)
        msg = radio.receive()
    
    sleep(5)
