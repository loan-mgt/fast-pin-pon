# micro:bit Relay - UART to Radio (unidirectionnel)
# PC1: Reçoit données du bridge_emitter via UART, transmet par radio au unit
from microbit import display, Image, sleep, uart
import radio

radio.config(channel=7, length=128, power=7)
radio.on()

# Startup
display.show(Image.YES)
sleep(500)
display.show(Image.ARROW_W)

# Initialize UART for USB serial
uart.init(baudrate=115200, tx=None, rx=None)
sleep(500)

raw_buffer = b""

while True:
    # Read UART data and forward to radio
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
    
    sleep(5)
