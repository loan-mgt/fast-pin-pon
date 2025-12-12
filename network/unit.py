# micro:bit Unit Controller - Always active
from microbit import display, Image, sleep, running_time, button_a, button_b
import radio

radio.config(channel=7, length=64, power=7)
radio.on()

SECRET_KEY = "FPP2024"
MICROBIT_ID = "MB001"

status_idx = 0
seq_num = 0
last_send = 0
last_blink = 0
led_on = True
btn_a_start = 0
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

while True:
    now = running_time()
    check_ack()

    if button_a.is_pressed():
        if btn_a_start == 0:
            btn_a_start = now
    else:
        if btn_a_start > 0:
            dur = now - btn_a_start
            btn_a_start = 0
            if dur >= 2000:
                status_idx = 0 if status_idx == 4 else 4
                send_msg()
            elif status_idx < 4:
                status_idx = (status_idx + 1) % 4
                send_msg()

    if button_b.was_pressed():
        if status_idx < 4:
            status_idx = (status_idx - 1) % 4
            send_msg()

    show()

    if now - last_send > 5000:
        send_msg()

    sleep(50)
