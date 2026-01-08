# micro:bit Unit Controller - Unidirectionnel
# PC2: Reçoit données radio du relay, envoie via UART au bridge_receiver
from microbit import display, Image, sleep, running_time, button_a, button_b
import radio

# Radio configuration - MUST match relay.py
radio.config(channel=7, length=128, power=7)
radio.on()

# Constants
SECRET_KEY = "FPP2024"
MICROBIT_ID = "MB001"
CODES = ["AVL", "UWY", "ONS", "UNA", "OFF"]

# State
status_idx = 0
last_active = 0
last_send = 0
cooldown_until = 0


def crc8(d):
    c = 0
    for ch in d:
        c = (c + ord(ch)) & 0xFF
        c = ((c << 1) | (c >> 7)) & 0xFF
    return c


def sign(d, s):
    x = SECRET_KEY + d + str(s)
    r = 0
    for i, ch in enumerate(x):
        r = (r + ord(ch) * (i + 1)) & 0xFFFF
    return r


def get_img(i):
    if i == 0:
        return Image.SQUARE
    if i == 1:
        return Image.ARROW_E
    if i == 2:
        return Image.DIAMOND
    if i == 3:
        return Image.NO
    return None


def show_status():
    if status_idx == 4:
        display.clear()
    else:
        img = get_img(status_idx)
        if img:
            display.show(img)


def send_mbit_status():
    """Send current status to bridge_receiver via UART (print)"""
    global last_send
    msg = "MBIT:{},{}".format(MICROBIT_ID, CODES[status_idx])
    print(msg)
    last_send = running_time()


def set_status(new_idx):
    global status_idx, last_active
    if new_idx == status_idx:
        return
    if status_idx != 4:
        last_active = status_idx
    status_idx = new_idx
    show_status()
    send_mbit_status()


# Startup
display.scroll(MICROBIT_ID, delay=80)
sleep(300)
show_status()
send_mbit_status()

while True:
    now = running_time()
    
    # === PART 1: Receive radio messages from relay ===
    incoming = radio.receive()
    while incoming:
        # Try to parse signed packet
        try:
            parts = incoming.split("|")
            if len(parts) == 4:
                seq = int(parts[0])
                data = parts[1]
                crc_val = int(parts[2])
                sig_val = int(parts[3])
                
                # Verify signature
                if crc8(data) == crc_val and sign(data, seq) == sig_val:
                    # Handle GPS message - forward to UART
                    if data.startswith("GPS:"):
                        print(data)
                        display.show(Image.ARROW_W)
                    
                    # Handle STA message - forward to UART
                    elif data.startswith("STA:"):
                        print(data)
                        
                        # Apply status change locally if it's for THIS microbit
                        payload = data[4:].split(",")
                        if len(payload) >= 2:
                            target_id = payload[0].strip()
                            if target_id == MICROBIT_ID and now >= cooldown_until:
                                new_status = payload[1].strip().upper()
                                for i, code in enumerate(CODES):
                                    if new_status.startswith(code):
                                        if i != status_idx:
                                            set_status(i)
                                        break
        except:
            pass
        
        incoming = radio.receive()
    
    # === PART 2: Button handling ===
    # A+B long press -> toggle OFF
    if button_a.is_pressed() and button_b.is_pressed():
        start = running_time()
        while button_a.is_pressed() and button_b.is_pressed():
            sleep(10)
        if running_time() - start >= 1200:
            if status_idx == 4:
                set_status(last_active)
            else:
                set_status(4)
        while button_a.is_pressed() or button_b.is_pressed():
            sleep(10)
        button_a.was_pressed()
        button_b.was_pressed()
        cooldown_until = running_time() + 300
        continue
    
    # Ignore during cooldown
    if now < cooldown_until:
        sleep(50)
        continue
    
    # Button A: set unavailable
    if button_a.was_pressed() and status_idx in (0, 1, 2):
        set_status(3)
    
    # Button B: cycle through statuses
    if button_b.was_pressed():
        if status_idx == 0:
            set_status(1)
        elif status_idx == 1:
            set_status(2)
        elif status_idx == 2:
            set_status(0)
        elif status_idx == 3:
            set_status(0)
    
    # === PART 3: Periodic heartbeat ===
    if now - last_send > 5000:
        send_mbit_status()
    
    sleep(50)
