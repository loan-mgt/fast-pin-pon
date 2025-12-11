#!/usr/bin/env python3
"""
Bridge entre le micro:bit (relay) et l'API fast-pin-pon.

Ce script :
1. Reçoit les données des unités via le port série (USB) depuis le micro:bit relay
2. Affiche les données sur la console
3. Met à jour l'API avec les nouvelles données (statut et position)

Usage:
    python3 bridge.py

Variables d'environnement:
    API_URL: URL de l'API (défaut: https://api.fast-pin-pon.4loop.org)
    SERIAL_PORT: Port série du micro:bit (auto-détecté si non spécifié)
    BAUD_RATE: Vitesse du port série (défaut: 115200)
"""

import os
import time
import serial
import serial.tools.list_ports
import requests
from typing import Optional, Dict, Tuple


def get_env(key: str, default: str) -> str:
    """Récupère une variable d'environnement."""
    return os.environ.get(key, default)


API_DEFAULT_URL = "https://api.fast-pin-pon.4loop.org"

# Cache des unités connues (call_sign -> unit_id)
known_units: Dict[str, str] = {}

# Mapping des statuts micro:bit -> API
STATUS_MAP = {
    "AVL": "available",
    "ERT": "en_route",
    "ONS": "on_site",
    "MNT": "maintenance",
    "OFF": "offline"
}


def find_microbit_port() -> Optional[str]:
    """Trouve automatiquement le port série du micro:bit."""
    ports = serial.tools.list_ports.comports()
    for port in ports:
        if any(x in port.device.lower() for x in ["ttyacm", "usbmodem"]):
            return port.device
        if "microbit" in port.description.lower():
            return port.device
    return None


def parse_unit_message(message: str) -> Optional[Tuple[str, float, float, str]]:
    """Parse un message d'unité. Retourne (call_sign, lat, lon, status) ou None."""
    try:
        if not message.startswith("UNIT:"):
            return None
        data = message[5:]  # Enlever "UNIT:"
        parts = data.split(",")
        if len(parts) >= 4:
            call_sign = parts[0]
            lat = float(parts[1])
            lon = float(parts[2])
            status = parts[3].strip()
            return (call_sign, lat, lon, status)
    except (ValueError, IndexError, AttributeError):
        pass
    return None


def get_or_create_unit(api_url: str, call_sign: str, lat: float, lon: float,
                       status: str) -> Optional[str]:
    """Récupère ou crée une unité dans l'API. Retourne l'ID de l'unité."""
    # Vérifier le cache
    if call_sign in known_units:
        return known_units[call_sign]

    # Chercher l'unité existante
    try:
        response = requests.get(f"{api_url}/v1/units", timeout=5)
        response.raise_for_status()
        units = response.json()

        for unit in units:
            if unit.get("call_sign") == call_sign:
                unit_id = unit.get("id")
                known_units[call_sign] = unit_id
                print(f"[INFO] Unité existante trouvée: {call_sign} -> {unit_id}")
                return unit_id
    except requests.RequestException as e:
        print(f"[ERREUR] Impossible de récupérer les unités: {e}")
        return None

    # Créer une nouvelle unité
    return create_new_unit(api_url, call_sign, lat, lon, status)


def create_new_unit(api_url: str, call_sign: str, lat: float, lon: float,
                    status: str) -> Optional[str]:
    """Crée une nouvelle unité dans l'API."""
    try:
        api_status = STATUS_MAP.get(status, "available")
        payload = {
            "call_sign": call_sign,
            "unit_type_code": "VSAV",  # Type par défaut
            "home_base": "Simulation micro:bit",
            "status": api_status,
            "latitude": lat,
            "longitude": lon
        }
        response = requests.post(f"{api_url}/v1/units", json=payload, timeout=5)
        response.raise_for_status()
        unit = response.json()
        unit_id = unit.get("id")
        known_units[call_sign] = unit_id
        print(f"[INFO] Nouvelle unité créée: {call_sign} -> {unit_id}")
        return unit_id
    except requests.RequestException as e:
        print(f"[ERREUR] Impossible de créer l'unité: {e}")
        return None


def update_unit_status(api_url: str, unit_id: str, status: str) -> bool:
    """Met à jour le statut d'une unité dans l'API."""
    try:
        api_status = STATUS_MAP.get(status, "available")
        payload = {"status": api_status}
        response = requests.patch(
            f"{api_url}/v1/units/{unit_id}/status",
            json=payload,
            timeout=5
        )
        response.raise_for_status()
        return True
    except requests.RequestException as e:
        print(f"[ERREUR] Impossible de mettre à jour le statut: {e}")
        return False


def update_unit_location(api_url: str, unit_id: str, lat: float, lon: float) -> bool:
    """Met à jour la position d'une unité dans l'API."""
    try:
        payload = {"latitude": lat, "longitude": lon}
        response = requests.patch(
            f"{api_url}/v1/units/{unit_id}/location",
            json=payload,
            timeout=5
        )
        response.raise_for_status()
        return True
    except requests.RequestException as e:
        print(f"[ERREUR] Impossible de mettre à jour la position: {e}")
        return False


def process_unit_data(api_url: str, call_sign: str, lat: float, lon: float,
                      status: str, last_statuses: Dict[str, str]) -> None:
    """Traite les données d'une unité et met à jour l'API si nécessaire."""
    # Obtenir ou créer l'unité
    unit_id = get_or_create_unit(api_url, call_sign, lat, lon, status)
    if not unit_id:
        return

    # Vérifier si le statut a changé
    if call_sign in last_statuses and last_statuses[call_sign] == status:
        # Pas de changement, juste afficher
        print(f"[REÇU] {call_sign} @ {lat:.4f},{lon:.4f} [{status}]")
    else:
        # Statut changé, mettre à jour l'API
        old_status = last_statuses.get(call_sign, "?")
        print(f"[CHANGEMENT] {call_sign}: {old_status} -> {status}")
        if update_unit_status(api_url, unit_id, status):
            print(f"[API] Statut mis à jour: {STATUS_MAP.get(status, status)}")
        last_statuses[call_sign] = status

    # Mettre à jour la position
    update_unit_location(api_url, unit_id, lat, lon)


def print_config(api_url: str, baud_rate: int) -> None:
    """Affiche la configuration du bridge."""
    print("=" * 50)
    print("  BRIDGE micro:bit -> API")
    print("=" * 50)
    print(f"[CONFIG] API_URL: {api_url}")
    print(f"[CONFIG] BAUD_RATE: {baud_rate}")


def get_serial_port(serial_port: str) -> Optional[str]:
    """Trouve et valide le port série."""
    if not serial_port:
        serial_port = find_microbit_port()

    if not serial_port:
        print("[ERREUR] Aucun micro:bit détecté.")
        print("[INFO] Ports disponibles:")
        for port in serial.tools.list_ports.comports():
            print(f"  - {port.device}: {port.description}")
        return None

    print(f"[CONFIG] SERIAL_PORT: {serial_port}")
    return serial_port


def wait_for_api(api_url: str) -> None:
    """Attend que l'API soit disponible."""
    print("\n[INFO] Attente de l'API...")
    while True:
        try:
            response = requests.get(f"{api_url}/healthz", timeout=2)
            if response.status_code == 200:
                print("[INFO] API disponible!")
                return
        except requests.RequestException:
            pass
        time.sleep(2)


def connect_serial(serial_port: str, baud_rate: int) -> Optional[serial.Serial]:
    """Établit la connexion au port série."""
    if not serial_port:
        return None

    try:
        ser = serial.Serial(serial_port, baud_rate, timeout=0.1)
        print(f"[INFO] Connexion au micro:bit établie sur {serial_port}")
        time.sleep(2)  # Attendre que le micro:bit soit prêt
        return ser
    except serial.SerialException as e:
        print(f"[ERREUR] Impossible de se connecter: {e}")
        return None


def read_serial_data(ser: serial.Serial, serial_buffer: str) -> str:
    """Lit les données du port série et les ajoute au buffer."""
    try:
        if ser.in_waiting > 0:
            data = ser.read(ser.in_waiting)
            serial_buffer += data.decode('utf-8', errors='ignore')
    except serial.SerialException as e:
        print(f"[ERREUR] Erreur de lecture: {e}")
    return serial_buffer


def process_serial_buffer(api_url: str, serial_buffer: str,
                          last_statuses: Dict[str, str]) -> str:
    """Traite le buffer série et retourne le reste non traité."""
    while '\n' in serial_buffer:
        line, serial_buffer = serial_buffer.split('\n', 1)
        line = line.strip()

        if line:
            # Afficher les rejets de sécurité
            if line.startswith("REJECT:"):
                parts = line.split(":", 2)
                if len(parts) >= 2:
                    reason = parts[1]
                    details = parts[2] if len(parts) > 2 else ""
                    print(f"[SÉCURITÉ] Message rejeté - Raison: {reason} {details}")
                continue
            
            unit_data = parse_unit_message(line)
            if unit_data:
                call_sign, lat, lon, status = unit_data
                process_unit_data(api_url, call_sign, lat, lon, status, last_statuses)

    return serial_buffer


def run_bridge_loop(api_url: str, ser: serial.Serial) -> None:
    """Boucle principale du bridge - lit les données du micro:bit."""
    print("\n[INFO] En attente de données du micro:bit...")
    print("-" * 50)

    serial_buffer = ""
    last_statuses: Dict[str, str] = {}

    try:
        while True:
            serial_buffer = read_serial_data(ser, serial_buffer)
            serial_buffer = process_serial_buffer(api_url, serial_buffer, last_statuses)
            time.sleep(0.01)

    except KeyboardInterrupt:
        print("\n[INFO] Arrêt du bridge")


def main() -> None:
    """Point d'entrée principal du bridge."""
    # Configuration via variables d'environnement
    api_url = get_env("API_URL", API_DEFAULT_URL)
    serial_port = get_env("SERIAL_PORT", "")
    baud_rate = int(get_env("BAUD_RATE", "115200"))

    print_config(api_url, baud_rate)
    serial_port = get_serial_port(serial_port)

    if not serial_port:
        print("[ERREUR] Impossible de continuer sans port série.")
        return

    wait_for_api(api_url)
    ser = connect_serial(serial_port, baud_rate)

    if not ser:
        print("[ERREUR] Impossible de se connecter au micro:bit.")
        return

    try:
        run_bridge_loop(api_url, ser)
    finally:
        ser.close()
        print("[INFO] Connexion fermée.")


if __name__ == "__main__":
    main()
