#!/usr/bin/env python3
"""
Bridge entre le micro:bit (relay) et l'API fast-pin-pon.

Usage:
    python3 bridge.py

Variables d'environnement:
    API_URL: URL de l'API (défaut: https://api.fast-pin-pon.4loop.org)
    SERIAL_PORT: Port série du micro:bit (auto-détecté si non spécifié)
    BAUD_RATE: Vitesse du port série (défaut: 115200)
"""

import os
import random
import time
import serial
import serial.tools.list_ports
import requests
from typing import Optional, Dict, Tuple


def get_env(key: str, default: str) -> str:
    return os.environ.get(key, default)


# Default to prod API; override with API_URL for dev/local
API_DEFAULT_URL = "http://localhost:8081"

STATUS_MAP = {
    "AVL": "available",
    "UWY": "under_way",
    "ONS": "on_site",
    "UNA": "unavailable",
    "OFF": "offline"
}

# Bounding box approximative de Lyon (lat, lon)
LYON_BBOX = {
    "lat_min": 45.70,
    "lat_max": 45.82,
    "lon_min": 4.78,
    "lon_max": 4.95,
}

# Séquence pour commandes sortantes (0-255 wrap)
out_seq = 0


def crc8(data: str) -> int:
    c = 0
    for ch in data:
        c = (c + ord(ch)) & 0xFF
        c = ((c << 1) | (c >> 7)) & 0xFF
    return c


def sign(data: str, seq: int) -> int:
    x = "FPP2024" + data + str(seq)
    r = 0
    for i, ch in enumerate(x):
        r = (r + ord(ch) * (i + 1)) & 0xFFFF
    return r


def build_packet(data: str) -> str:
    global out_seq
    packet = f"{out_seq}|{data}|{crc8(data)}|{sign(data, out_seq)}"
    out_seq = (out_seq + 1) & 0xFF
    return packet


def find_microbit_port() -> Optional[str]:
    ports = serial.tools.list_ports.comports()
    for port in ports:
        if any(x in port.device.lower() for x in ["ttyacm", "usbmodem"]):
            return port.device
        if "microbit" in port.description.lower():
            return port.device
    return None


def parse_microbit_message(message: str) -> Optional[Tuple[str, str]]:
    try:
        if not message.startswith("MBIT:"):
            return None
        data = message[5:]
        parts = data.split(",")
        if len(parts) >= 2:
            return (parts[0].strip(), parts[1].strip())
    except (ValueError, IndexError, AttributeError):
        pass
    return None


def parse_gps_message(message: str) -> Optional[Tuple[str, float, float]]:
    try:
        if not message.startswith("GPS:"):
            return None
        data = message[4:]
        parts = data.split(",")
        if len(parts) >= 3:
            microbit_id = parts[0].strip()
            lat = float(parts[1])
            lon = float(parts[2])
            return microbit_id, lat, lon
    except (ValueError, IndexError, AttributeError):
        pass
    return None


def find_unit_by_microbit(api_url: str, microbit_id: str) -> Optional[Tuple[str, str]]:
    try:
        base = api_url.rstrip("/")
        response = requests.get(f"{base}/v1/units/by-microbit/{microbit_id}", timeout=5)
        if response.status_code == 404:
            return None
        response.raise_for_status()
        unit = response.json()
        unit_id = unit.get("id")
        call_sign = unit.get("call_sign")
        print(f"[INFO] Micro:bit {microbit_id} -> Unité {call_sign}")
        return (unit_id, call_sign)
    except requests.RequestException as e:
        print(f"[ERREUR] API: {e}")
        return None


def update_unit_status(api_url: str, unit_id: str, status: str) -> bool:
    try:
        api_status = STATUS_MAP.get(status, "available")
        base = api_url.rstrip("/")
        url = f"{base}/v1/units/{unit_id}/status"
        response = requests.patch(
            url,
            json={"status": api_status},
            timeout=5
        )
        if response.status_code >= 400:
            body = response.text if response.text else ""
            print(f"[ERREUR] API status update {response.status_code}: {body}")
            response.raise_for_status()
        return True
    except requests.RequestException as e:
        print(f"[ERREUR] Mise à jour: {e}")
        return False


def process_microbit_data(api_url: str, microbit_id: str, status: str,
                          last_statuses: Dict[str, str]) -> None:
    print(f"[MBIT] {microbit_id} -> {status}")
    unit_info = find_unit_by_microbit(api_url, microbit_id)
    if not unit_info:
        print(f"[WARN] Micro:bit {microbit_id} non assigné")
        return

    unit_id, call_sign = unit_info
    api_status = STATUS_MAP.get(status, status)

    if microbit_id in last_statuses and last_statuses[microbit_id] == status:
        print(f"[REÇU] {call_sign} [{api_status}]")
    else:
        old = last_statuses.get(microbit_id, "?")
        old_api = STATUS_MAP.get(old, old)
        print(f"[CHANGEMENT] {call_sign}: {old_api} -> {api_status}")
        if update_unit_status(api_url, unit_id, status):
            print(f"[API] Statut mis à jour: {api_status}")
        last_statuses[microbit_id] = status


def handle_security_reject(line: str) -> None:
    parts = line.split(":", 2)
    if len(parts) >= 2:
        reason = parts[1]
        details = parts[2] if len(parts) > 2 else ""
        print(f"[SÉCURITÉ] Rejeté: {reason} {details}")


def process_line(api_url: str, line: str, last_statuses: Dict[str, str]) -> None:
    if line.startswith("REJECT:"):
        handle_security_reject(line)
        return

    mbit_data = parse_microbit_message(line)
    if mbit_data:
        microbit_id, status = mbit_data
        process_microbit_data(api_url, microbit_id, status, last_statuses)
        return

    gps_data = parse_gps_message(line)
    if gps_data:
        microbit_id, lat, lon = gps_data
        print(f"[GPS] {microbit_id}: {lat:.6f}, {lon:.6f}")


def wait_for_api(api_url: str) -> None:
    print("[INFO] Attente de l'API...")
    while True:
        try:
            response = requests.get(f"{api_url}/healthz", timeout=2)
            if response.status_code == 200:
                print("[INFO] API disponible!")
                return
        except requests.RequestException:
            pass
        time.sleep(2)


def main() -> None:
    api_url = get_env("API_URL", API_DEFAULT_URL)
    serial_port = get_env("SERIAL_PORT", "")
    baud_rate = int(get_env("BAUD_RATE", "115200"))
    gps_interval = float(get_env("GPS_INTERVAL", "5"))  # secondes
    target_microbit = get_env("GPS_MICROBIT", "MB001")

    print("=" * 50)
    print("  BRIDGE micro:bit -> API")
    print("=" * 50)
    print(f"[CONFIG] API_URL: {api_url}")
    print(f"[CONFIG] BAUD_RATE: {baud_rate}")
    print(f"[CONFIG] GPS every {gps_interval}s for {target_microbit}")

    if not serial_port:
        serial_port = find_microbit_port()

    if not serial_port:
        print("[ERREUR] Aucun micro:bit détecté")
        for port in serial.tools.list_ports.comports():
            print(f"  - {port.device}: {port.description}")
        return

    print(f"[CONFIG] SERIAL_PORT: {serial_port}")

    wait_for_api(api_url)

    try:
        ser = serial.Serial(serial_port, baud_rate, timeout=0.1)
        print(f"[INFO] Connecté au micro:bit sur {serial_port}")
        time.sleep(2)
    except serial.SerialException as e:
        print(f"[ERREUR] Connexion: {e}")
        return

    print("\n[INFO] En attente de données...")
    print("-" * 50)

    serial_buffer = ""
    last_statuses: Dict[str, str] = {}

    last_gps = 0.0

    try:
        run_loop(api_url, ser, serial_buffer, last_statuses, gps_interval, target_microbit)
    except KeyboardInterrupt:
        print("\n[INFO] Arrêt du bridge")
    finally:
        ser.close()
        print("[INFO] Connexion fermée")


if __name__ == "__main__":
    main()


def run_loop(api_url: str, ser: serial.Serial, serial_buffer: str, last_statuses: Dict[str, str],
             gps_interval: float, target_microbit: str) -> None:
    last_gps = 0.0
    while True:
        now = time.time()
        if now - last_gps >= gps_interval:
            lat = random.uniform(LYON_BBOX["lat_min"], LYON_BBOX["lat_max"])
            lon = random.uniform(LYON_BBOX["lon_min"], LYON_BBOX["lon_max"])
            cmd = f"GPSCMD:{target_microbit},{lat:.6f},{lon:.6f}"
            packet = build_packet(cmd)
            ser.write((packet + "\n").encode("utf-8"))
            print(f"[SEND GPS] {cmd}\n")
            last_gps = now

        if ser.in_waiting > 0:
            data = ser.read(ser.in_waiting)
            serial_buffer += data.decode('utf-8', errors='ignore')

        while '\n' in serial_buffer:
            line, serial_buffer = serial_buffer.split('\n', 1)
            line = line.strip()
            if line:
                process_line(api_url, line, last_statuses)

        time.sleep(0.01)
