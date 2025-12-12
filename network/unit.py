# micro:bit Unit Controller - Direct transitions
from microbit import display, Image, sleep, running_time, button_a, button_b
import radio

radio.config(channel=7, length=64, power=7)
radio.on()

SECRET_KEY = "FPP2024"
MICROBIT_ID = "MB001"

# 0:AVL, 1:UWY, 2:ONS, 3:UNA, 4:OFF
status_idx = 0
last_active = 0
seq_num = 0
last_send = 0
last_blink = 0
led_on = True
waiting_ack = False
last_msg = ""
retries = 0
retry_time = 0

CODES = ["AVL", "UWY", "ONS", "UNA", "OFF"]


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


def send_msg():
    global seq_num, waiting_ack, last_msg, retries, retry_time, last_send
    d = "MBIT:{},{}".format(MICROBIT_ID, CODES[status_idx])
    m = "{}|{}|{}|{}".format(seq_num, d, crc8(d), sign(d, seq_num))
    radio.send(m)
    seq_num = (seq_num + 1) & 0xFF
    last_msg = m
    waiting_ack = True
    retries = 0
    retry_time = running_time()
    last_send = running_time()


def check_ack():
    global waiting_ack, retries, retry_time
    if not waiting_ack:
        return
    p = radio.receive()
    if p and p.startswith("ACK:"):
        waiting_ack = False
        return
    now = running_time()
    if now - retry_time > 500:
        if retries < 3:
            radio.send(last_msg)
            retries += 1
            retry_time = now
        else:
            waiting_ack = False


def set_status(new_idx):
    global status_idx, last_active
    if new_idx == status_idx:
        return
    if status_idx != 4:
        last_active = status_idx
    status_idx = new_idx
    send_msg()


def show():
    global led_on, last_blink
    if status_idx == 4:
        display.clear()
    elif status_idx == 1:
        now = running_time()
        if now - last_blink > 300:
            led_on = not led_on
            last_blink = now
        if led_on:
            display.show(get_img(1))
        else:
            display.clear()
    else:
        img = get_img(status_idx)
        if img:
            display.show(img)


display.scroll(MICROBIT_ID, delay=80)
sleep(300)
send_msg()

cooldown_until = 0

while True:
    now = running_time()
    check_ack()

    # A+B long -> toggle OFF
    if button_a.is_pressed() and button_b.is_pressed():
        start = running_time()
        while button_a.is_pressed() and button_b.is_pressed():
            sleep(10)
        if running_time() - start >= 1200:
            if status_idx == 4:
                set_status(last_active)
            else:
                set_status(4)
        # Attendre relâchement complet + cooldown
        while button_a.is_pressed() or button_b.is_pressed():
            sleep(10)
        # Vider les événements was_pressed accumulés
        button_a.was_pressed()
        button_b.was_pressed()
        cooldown_until = running_time() + 300
        show()
        continue

    # Ignorer pendant cooldown
    if now < cooldown_until:
        show()
        sleep(50)
        continue

    # Bouton A: AVL/UWY/ONS -> UNA
    if button_a.was_pressed() and status_idx in (0, 1, 2):
        set_status(3)

    # Bouton B: transitions directes
    if button_b.was_pressed():
        if status_idx == 0:      # AVL -> UWY
            set_status(1)
        elif status_idx == 1:    # UWY -> ONS
            set_status(2)
        elif status_idx == 2:    # ONS -> AVL
            set_status(0)
        elif status_idx == 3:    # UNA -> AVL
            set_status(0)

    show()

    if now - last_send > 5000:
        send_msg()

    sleep(50)
