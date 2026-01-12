#!/usr/bin/env python3
"""
Bridge Récepteur - Reçoit les données du unit micro:bit et met à jour l'API

PC2: Ce script lit les données série du unit et applique les changements à l'API.

Usage:
    SERIAL_PORT=/dev/tty.usbmodemXXXX python3 bridge_receiver.py

Variables d'environnement:
    SERIAL_PORT: Port série vers le micro:bit unit
    API_URL: URL de l'API (défaut: http://localhost:8081)
    BAUD_RATE: Vitesse du port série (défaut: 115200)
"""

import os
import time
import serial
import serial.tools.list_ports
import requests
from datetime import datetime, timezone
from typing import Optional, Dict, Tuple


# Configuration
API_DEFAULT_URL = "http://localhost:8081"

STATUS_MAP = {
    "AVL": "available",
    "UWY": "under_way",
    "ONS": "on_site",
    "UNA": "unavailable",
    "OFF": "offline",
}


def get_env(key: str, default: str) -> str:
    return os.environ.get(key, default)


def find_microbit_port() -> Optional[str]:
    ports = serial.tools.list_ports.comports()
    for port in ports:
        if any(x in port.device.lower() for x in ["ttyacm", "usbmodem"]):
            return port.device
        if "microbit" in port.description.lower():
            return port.device
    return None


def normalize_status(status: str) -> str:
    if not status:
        return "available"
    upper = status.strip().upper()
    if upper in STATUS_MAP:
        return STATUS_MAP[upper]
    return status.lower()


def extract_payload(message: str) -> str:
    """Extract payload from packet format: seq|data|crc|sign
    
    Returns the data portion (e.g. 'GPS:MB001,45.76,4.89')
    If message doesn't match packet format, returns original message.
    """
    parts = message.split("|")
    if len(parts) >= 2:
        # Format is: seq|data|crc|sign - we want parts[1]
        return parts[1]
    return message


def parse_gps_message(message: str) -> Optional[Tuple[str, float, float]]:
    """Parse GPS:microbit_id,lat,lon"""
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
    except (ValueError, IndexError):
        pass
    return None


def parse_sta_message(message: str) -> Optional[Tuple[str, str]]:
    """Parse STA:microbit_id,status"""
    try:
        if not message.startswith("STA:"):
            return None
        data = message[4:]
        parts = data.split(",")
        if len(parts) >= 2:
            microbit_id = parts[0].strip()
            status = parts[1].strip()
            return microbit_id, status
    except (ValueError, IndexError):
        pass
    return None


def parse_mbit_message(message: str) -> Optional[Tuple[str, str]]:
    """Parse MBIT:microbit_id,status_code"""
    try:
        if not message.startswith("MBIT:"):
            return None
        data = message[5:]
        parts = data.split(",")
        if len(parts) >= 2:
            microbit_id = parts[0].strip()
            status_code = parts[1].strip()
            return microbit_id, status_code
    except (ValueError, IndexError):
        pass
    return None


def load_microbit_cache(api_url: str) -> Tuple[Dict[str, str], Dict[str, str]]:
    """Load mapping microbit_id -> unit_id"""
    try:
        response = requests.get(f"{api_url.rstrip('/')}/v1/units", timeout=8)
        response.raise_for_status()
        microbit_to_unit = {}
        unit_to_microbit = {}
        for unit in response.json():
            microbit_id = unit.get("microbit_id")
            unit_id = unit.get("id")
            if microbit_id and unit_id:
                microbit_to_unit[microbit_id] = unit_id
                unit_to_microbit[unit_id] = microbit_id
        return microbit_to_unit, unit_to_microbit
    except requests.RequestException:
        return {}, {}


def update_unit_location(api_url: str, unit_id: str, lat: float, lon: float) -> bool:
    try:
        url = f"{api_url.rstrip('/')}/v1/units/{unit_id}/location"
        payload = {
            "latitude": lat,
            "longitude": lon,
            "recorded_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        }
        response = requests.patch(url, json=payload, timeout=5)
        return response.status_code < 400
    except requests.RequestException:
        return False


def update_unit_status(api_url: str, unit_id: str, status: str) -> bool:
    try:
        new_status = normalize_status(status)
        url = f"{api_url.rstrip('/')}/v1/units/{unit_id}/status"
        response = requests.patch(url, json={"status": new_status}, timeout=5)
        return response.status_code < 400
    except requests.RequestException:
        print(f"[ERROR] Failed to update status for {unit_id}")
        return False


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


def print_config(api_url: str, baud_rate: int) -> None:
    """Print configuration information."""
    print("=" * 50)
    print("  BRIDGE RÉCEPTEUR (PC2)")
    print("=" * 50)
    print(f"[CONFIG] API_URL: {api_url}")
    print(f"[CONFIG] BAUD_RATE: {baud_rate}")


def setup_serial(serial_port: str, baud_rate: int) -> Optional[serial.Serial]:
    """Set up and return serial connection."""
    if not serial_port:
        serial_port = find_microbit_port()
    if not serial_port:
        print("[ERREUR] Aucun port série trouvé. Définissez SERIAL_PORT.")
        return None

    print(f"[CONFIG] SERIAL_PORT: {serial_port}")

    try:
        ser = serial.Serial(serial_port, baud_rate, timeout=0.1)
        print(f"[INFO] Connecté au micro:bit unit sur {serial_port}")
        time.sleep(1)
        return ser
    except serial.SerialException as e:
        print(f"[ERREUR] Connexion: {e}")
        return None


def handle_gps_message(line: str, microbit_to_unit: Dict[str, str], api_url: str) -> bool:
    """Handle GPS message and return True if handled."""
    gps_data = parse_gps_message(line)
    if gps_data:
        microbit_id, lat, lon = gps_data
        unit_id = microbit_to_unit.get(microbit_id)
        if unit_id and update_unit_location(api_url, unit_id, lat, lon):
            print(f"[API] Location updated: {unit_id}")
        return True
    return False


def handle_sta_message(line: str, microbit_to_unit: Dict[str, str], 
                       last_statuses: Dict[str, str], api_url: str) -> bool:
    """Handle STA message and return True if handled."""
    sta_data = parse_sta_message(line)
    if sta_data:
        microbit_id, status = sta_data
        unit_id = microbit_to_unit.get(microbit_id)
        if unit_id and last_statuses.get(microbit_id) != status:
            if update_unit_status(api_url, unit_id, status):
                last_statuses[microbit_id] = status
                print(f"[API] Status updated: {unit_id} -> {status}")
        return True
    return False


def handle_mbit_message(line: str, microbit_to_unit: Dict[str, str],
                        last_statuses: Dict[str, str], api_url: str) -> bool:
    """Handle MBIT message and return True if handled."""
    mbit_data = parse_mbit_message(line)
    if mbit_data:
        microbit_id, status_code = mbit_data
        unit_id = microbit_to_unit.get(microbit_id)
        if unit_id and last_statuses.get(microbit_id) != status_code:
            if update_unit_status(api_url, unit_id, status_code):
                last_statuses[microbit_id] = status_code
                print(f"[API] MBIT status updated: {unit_id} -> {status_code}")
        return True
    return False


def process_line(line: str, microbit_to_unit: Dict[str, str],
                 last_statuses: Dict[str, str], api_url: str) -> None:
    """Process a single received line."""
    print(f"[RECV] {line}")
    
    # Extract payload from packet format (seq|data|crc|sign)
    payload = extract_payload(line)
    
    if handle_gps_message(payload, microbit_to_unit, api_url):
        return
    if handle_sta_message(payload, microbit_to_unit, last_statuses, api_url):
        return
    handle_mbit_message(payload, microbit_to_unit, last_statuses, api_url)


def run_main_loop(ser: serial.Serial, api_url: str, 
                  microbit_to_unit: Dict[str, str]) -> None:
    """Run the main receiving loop."""
    serial_buffer = ""
    last_statuses: Dict[str, str] = {}
    last_mapping_refresh = time.time()

    while True:
        # Refresh mapping periodically
        if time.time() - last_mapping_refresh > 60:
            microbit_to_unit.clear()
            new_mapping, _ = load_microbit_cache(api_url)
            microbit_to_unit.update(new_mapping)
            last_mapping_refresh = time.time()

        # Read serial data
        if ser.in_waiting > 0:
            data = ser.read(ser.in_waiting)
            serial_buffer += data.decode('utf-8', errors='ignore')

        # Process complete lines
        while '\n' in serial_buffer:
            line, serial_buffer = serial_buffer.split('\n', 1)
            line = line.strip()
            if line:
                process_line(line, microbit_to_unit, last_statuses, api_url)

        time.sleep(0.01)


def main() -> None:
    serial_port = get_env("SERIAL_PORT", "")
    baud_rate = int(get_env("BAUD_RATE", "115200"))
    api_url = get_env("API_URL", API_DEFAULT_URL)

    print_config(api_url, baud_rate)

    ser = setup_serial(serial_port, baud_rate)
    if ser is None:
        return

    wait_for_api(api_url)

    print("[INFO] Chargement du mapping microbits -> unités...")
    microbit_to_unit, _ = load_microbit_cache(api_url)
    print(f"[INFO] {len(microbit_to_unit)} microbits mappés")

    print("\n[INFO] Bridge récepteur prêt. En écoute...")
    print("-" * 60)

    try:
        run_main_loop(ser, api_url, microbit_to_unit)
    except KeyboardInterrupt:
        print("\n[INFO] Arrêt du bridge récepteur")
    finally:
        ser.close()
        print("[INFO] Connexion fermée")


if __name__ == "__main__":
    main()
