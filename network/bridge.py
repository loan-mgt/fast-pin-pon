#!/usr/bin/env python3
"""
Bridge entre l'API fast-pin-pon et le micro:bit.

Ce script :
1. Récupère les unités depuis l'API REST
2. Envoie les données au micro:bit via le port série (USB)
3. Le micro:bit les retransmet par radio à d'autres micro:bits

Usage:
    python3 bridge.py
    
Variables d'environnement:
    API_URL: URL de l'API (défaut: http://api:8080)
    SERIAL_PORT: Port série du micro:bit (défaut: /dev/ttyACM0)
    INTERVAL: Intervalle en secondes (défaut: 5)
    BAUD_RATE: Vitesse du port série (défaut: 115200)
"""

import os
import time
import serial
import serial.tools.list_ports
import requests
from typing import Optional


def get_env(key: str, default: str) -> str:
    """Récupère une variable d'environnement."""
    return os.environ.get(key, default)


API_DEFAULT_URL = "https://api.fast-pin-pon.4loop.org"


def find_microbit_port() -> Optional[str]:
    """Trouve automatiquement le port série du micro:bit."""
    ports = serial.tools.list_ports.comports()
    for port in ports:
        # Linux: ttyACM0, Mac: usbmodem
        if any(x in port.device.lower() for x in ["ttyacm", "usbmodem"]):
            return port.device
        if "microbit" in port.description.lower():
            return port.device
    return None


def fetch_units(api_url: str) -> list:
    """Récupère la liste des unités depuis l'API."""
    try:
        response = requests.get(f"{api_url}/v1/units", timeout=5)
        response.raise_for_status()
        return response.json()
    except requests.RequestException as e:
        print(f"[ERREUR] Impossible de contacter l'API: {e}")
        return []


def format_unit_for_microbit(unit: dict) -> str:
    """
    Formate les données d'une unité pour envoi au micro:bit.
    Format compact: UNIT:call_sign,lat,lon,status
    """
    call_sign = unit.get("call_sign", "???")[:8]  # Limiter à 8 chars
    location = unit.get("location", {})
    lat = location.get("latitude", 0.0)
    lon = location.get("longitude", 0.0)
    status = unit.get("status", "unknown")
    
    # Statuts abrégés pour économiser de l'espace
    status_map = {
        "available": "AVL",
        "en_route": "ERT",
        "on_site": "ONS",
        "maintenance": "MNT",
        "offline": "OFF"
    }
    status_short = status_map.get(status, "UNK")
    
    return f"UNIT:{call_sign},{lat:.6f},{lon:.6f},{status_short}"


def send_to_microbit(ser: serial.Serial, data: str):
    """Envoie des données au micro:bit via le port série."""
    try:
        message = data + "\n"
        ser.write(message.encode('utf-8'))
        ser.flush()  # S'assurer que les données sont envoyées
        print(f"[ENVOYÉ] {data}")
    except serial.SerialException as e:
        print(f"[ERREUR] Échec d'envoi: {e}")


def wait_for_serial_port(port_path: str, timeout: int = 60) -> bool:
    """Attend que le port série soit disponible."""
    print(f"[INFO] Attente du port série {port_path}...")
    start = time.time()
    while time.time() - start < timeout:
        if os.path.exists(port_path):
            return True
        time.sleep(1)
    return False


def main():
    # Configuration via variables d'environnement
    api_url = get_env("API_URL", API_DEFAULT_URL)
    serial_port = get_env("SERIAL_PORT", "")
    interval = int(get_env("INTERVAL", "5"))
    baud_rate = int(get_env("BAUD_RATE", "115200"))

    print("=" * 50)
    print("  BRIDGE API <-> micro:bit")
    print("=" * 50)
    print(f"[CONFIG] API_URL: {api_url}")
    print(f"[CONFIG] INTERVAL: {interval}s")
    print(f"[CONFIG] BAUD_RATE: {baud_rate}")

    # Trouver le port série
    if not serial_port:
        serial_port = find_microbit_port()
    
    if not serial_port:
        print("[ERREUR] Aucun micro:bit détecté.")
        print("[INFO] Ports disponibles:")
        for port in serial.tools.list_ports.comports():
            print(f"  - {port.device}: {port.description}")
        print("\n[INFO] Mode simulation activé (pas d'envoi série)")
        serial_port = None
    else:
        print(f"[CONFIG] SERIAL_PORT: {serial_port}")

    # Attendre que l'API soit prête
    print("\n[INFO] Attente de l'API...")
    while True:
        try:
            response = requests.get(f"{api_url}/healthz", timeout=2)
            if response.status_code == 200:
                print("[INFO] API disponible!")
                break
        except:
            pass
        time.sleep(2)

    # Connexion au port série (si disponible)
    ser = None
    if serial_port:
        if not wait_for_serial_port(serial_port):
            print(f"[WARN] Port {serial_port} non disponible, mode simulation")
        else:
            try:
                ser = serial.Serial(serial_port, baud_rate, timeout=1)
                print(f"[INFO] Connexion au micro:bit établie sur {serial_port}")
                time.sleep(2)  # Attendre que le micro:bit soit prêt
            except serial.SerialException as e:
                print(f"[ERREUR] Impossible de se connecter: {e}")

    print("\n[INFO] Démarrage de la boucle principale...")
    print("-" * 50)

    try:
        while True:
            # Récupérer les unités depuis l'API
            units = fetch_units(api_url)
            
            if units:
                print(f"\n[INFO] {len(units)} unité(s) récupérée(s)")
                
                for unit in units:
                    message = format_unit_for_microbit(unit)
                    
                    if ser:
                        send_to_microbit(ser, message)
                    else:
                        print(f"[SIMU] {message}")
                    
                    time.sleep(0.5)  # Attendre 500ms entre chaque envoi
                
                # Envoyer END pour signaler la fin du cycle
                if ser:
                    send_to_microbit(ser, "END")
                else:
                    print("[SIMU] END")
            else:
                print("[WARN] Aucune unité récupérée")
            
            time.sleep(interval)
            
    except KeyboardInterrupt:
        print("\n[INFO] Arrêt du bridge")
    finally:
        if ser:
            ser.close()


if __name__ == "__main__":
    main()
