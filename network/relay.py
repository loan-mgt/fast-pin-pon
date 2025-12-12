# micro:bit Relay - Receives radio, sends to PC, forwards commands
from microbit import Image, display, sleep
import radio

radio.config(channel=7, length=64, power=7)
radio.on()

SECRET_KEY = "FPP2024"
last_seqs = {}
cur_status = None
pending_cmd = None
cmd_buffer = ""


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


def parse_msg(p):
    try:
        parts = p.split("|")
        if len(parts) != 4:
            return (None, None, "FMT")
        seq = int(parts[0])
        data = parts[1]
        if crc8(data) != int(parts[2]):
            return (None, None, "CRC")
        if sign(data, seq) != int(parts[3]):
            return (None, None, "SIG")
        return (seq, data, None)
    except:
        pass
    return (None, None, "ERR")


def parse_mbit(d):
    try:
        if d.startswith("MBIT:"):
            parts = d[5:].split(",")
            if len(parts) >= 2:
                return (parts[0], parts[1])
    except:
        pass
    return None


def is_dup(mid, seq):
    if mid in last_seqs and last_seqs[mid] == seq:
        return True
    last_seqs[mid] = seq
    return False


def get_img(s):
    if s == "AVL":
        return Image.SQUARE
    if s == "UWY":
        return Image.ARROW_E
    if s == "ONS":
        return Image.DIAMOND
    if s == "UNA":
        return Image.NO
    return None


def show(s):
    global cur_status
    img = get_img(s)
    if img:
        display.show(img)
    else:
        display.clear()
    cur_status = s


display.show(Image.ARROW_W)

while True:
    # Check for radio messages from units
    p = radio.receive()
    if p:
        if p.startswith("ACK:"):
            continue
        seq, data, err = parse_msg(p)
        if err:
            print("REJECT:{}:{}".format(err, p[:20]))
            continue
        mb = parse_mbit(data)
        if mb:
            mid, status = mb
            radio.send("ACK:{}".format(seq))
            if is_dup(mid, seq):
                print("REJECT:DUP:{}".format(mid))
                continue
            if status != cur_status:
                show(status)
            print(data)

    sleep(20)
