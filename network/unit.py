# micro:bit Unit - UART to Radio (PC1)
# Reçoit données du bridge_emitter via UART, transmet par radio au relay
from microbit import display, Image, sleep, uart, running_time, button_a, button_b
import radio

# Radio configuration - MUST match relay.py
radio.config(channel=7, length=128, power=7)
radio.on()

# Constants
MICROBIT_ID = "MB001"
CODES = ["AVL", "UWY", "ONS", "UNA", "OFF"]

# State
status_idx = 0
last_active = 0
last_send = 0
cooldown_until = 0

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
    """Send current status via radio to relay"""
    global last_send
    msg = "MBIT:{},{}".format(MICROBIT_ID, CODES[status_idx])
    radio.send(msg)
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
display.show(Image.YES)
sleep(500)

# Initialize UART for USB serial
uart.init(baudrate=115200, tx=None, rx=None)
sleep(500)

display.scroll(MICROBIT_ID, delay=80)
sleep(300)
show_status()
send_mbit_status()

raw_buffer = b""

while True:
    now = running_time()
    
    # === PART 1: Read UART and forward to Radio ===
    try:
        if uart.any():
            display.show(Image.DIAMOND)
            chunk = uart.read(64)
            if chunk:
                raw_buffer += chunk
                
                # Process all complete lines
                while b"\n" in raw_buffer:
                    nl_pos = raw_buffer.find(b"\n")
                    line_bytes = raw_buffer[:nl_pos]
                    raw_buffer = raw_buffer[nl_pos + 1:]
                    
                    # Remove \r if present
                    line_bytes = line_bytes.replace(b"\r", b"")
                    if len(line_bytes) > 5 and len(line_bytes) < 120:
                        try:
                            line = ""
                            for b in line_bytes:
                                if 32 <= b < 127:
                                    line += chr(b)
                            if line and len(line) > 5:
                                radio.send(line)
                                display.show(Image.ARROW_E)
                        except:
                            pass
    except:
        pass
    
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
        sleep(5)
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
    
    sleep(5)
