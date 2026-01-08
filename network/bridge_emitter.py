#!/usr/bin/env python3
"""
Bridge Émetteur - Envoie les données du simulateur au relay micro:bit

PC1: Ce script récupère GPS+statut du simulateur et les envoie via série au relay.

Usage:
    SERIAL_PORT=/dev/tty.usbmodemXXXX python3 bridge_emitter.py

Variables d'environnement:
    SERIAL_PORT: Port série vers le micro:bit relay
    SIMULATOR_URL: URL du simulateur (défaut: http://localhost:8090)
    BAUD_RATE: Vitesse du port série (défaut: 115200)
    SIM_POLL_INTERVAL: Intervalle de polling du simulateur (défaut: 1s)
"""

import os
import time
import serial
import serial.tools.list_ports
import requests
from typing import Optional, Dict, List

# Configuration
SIMULATOR_DEFAULT_URL = "http://localhost:8090"
out_seq = 0


def get_env(key: str, default: str) -> str:
    return os.environ.get(key, default)


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


def fetch_simulator_tick(sim_url: str, tick_count: int) -> Optional[List[Dict]]:
    import datetime
    start = time.time()
    print(f"[DEBUG {datetime.datetime.now().strftime('%H:%M:%S')}] Appel simulateur: {sim_url}/tick...")
    try:
        response = requests.get(f"{sim_url.rstrip('/')}/tick", params={"count": tick_count}, timeout=30)
        elapsed = time.time() - start
        print(f"[DEBUG] Réponse simulateur en {elapsed:.2f}s - status={response.status_code}")
        response.raise_for_status()
        data = response.json()
        print(f"[DEBUG] Données reçues: {len(data)} unités")
        return data
    except requests.Timeout as e:
        elapsed = time.time() - start
        print(f"[DEBUG] TIMEOUT après {elapsed:.2f}s: {e}")
        return None
    except requests.ConnectionError as e:
        elapsed = time.time() - start
        print(f"[DEBUG] CONNEXION REFUSÉE après {elapsed:.2f}s: {e}")
        return None
    except requests.RequestException as e:
        elapsed = time.time() - start
        print(f"[DEBUG] Erreur simulateur après {elapsed:.2f}s: {e}")
        return None


def send_gps_command(ser: serial.Serial, microbit_id: str, lat: float, lon: float) -> None:
    cmd = f"GPS:{microbit_id},{lat:.6f},{lon:.6f}"
    packet = build_packet(cmd)
    ser.write((packet + "\n").encode("utf-8"))
    print(f"[EMIT] {cmd}")


def send_status_command(ser: serial.Serial, microbit_id: str, status: str) -> None:
    cmd = f"STA:{microbit_id},{status}"
    packet = build_packet(cmd)
    ser.write((packet + "\n").encode("utf-8"))
    print(f"[EMIT] {cmd}")


def load_microbit_mapping(api_url: str) -> Dict[str, str]:
    """Load mapping of unit_id -> microbit_id from API"""
    try:
        response = requests.get(f"{api_url.rstrip('/')}/v1/units", timeout=8)
        response.raise_for_status()
        mapping = {}
        for unit in response.json():
            microbit_id = unit.get("microbit_id")
            unit_id = unit.get("id")
            if microbit_id and unit_id:
                mapping[unit_id] = microbit_id
        return mapping
    except requests.RequestException:
        return {}


def main() -> None:
    serial_port = get_env("SERIAL_PORT", "")
    baud_rate = int(get_env("BAUD_RATE", "115200"))
    simulator_url = get_env("SIMULATOR_URL", SIMULATOR_DEFAULT_URL)
    api_url = get_env("API_URL", "http://localhost:8081")
    poll_interval = float(get_env("SIM_POLL_INTERVAL", "5"))  # 5s default for prod API

    print("=" * 50)
    print("  BRIDGE ÉMETTEUR (PC1)")
    print("=" * 50)
    print(f"[CONFIG] SIMULATOR_URL: {simulator_url}")
    print(f"[CONFIG] API_URL: {api_url}")
    print(f"[CONFIG] BAUD_RATE: {baud_rate}")
    print(f"[CONFIG] SIM_POLL_INTERVAL: {poll_interval}s")

    if not serial_port:
        serial_port = find_microbit_port()
    if not serial_port:
        print("[ERREUR] Aucun port série trouvé. Définissez SERIAL_PORT.")
        return

    print(f"[CONFIG] SERIAL_PORT: {serial_port}")

    try:
        ser = serial.Serial(serial_port, baud_rate, timeout=0.1)
        print(f"[INFO] Connecté au micro:bit relay sur {serial_port}")
        time.sleep(1)
    except serial.SerialException as e:
        print(f"[ERREUR] Connexion: {e}")
        return

    # Load microbit mapping from API
    print("[INFO] Chargement du mapping unités -> microbits...")
    unit_to_microbit = load_microbit_mapping(api_url)
    print(f"[INFO] {len(unit_to_microbit)} unités avec microbit assigné")
    
    print("\n[INFO] Bridge émetteur prêt. Envoi des données du simulateur...")
    print("-" * 60)

    # State for rotation
    microbit_ids: List[str] = list(set(unit_to_microbit.values()))
    microbit_latest: Dict[str, Dict] = {}
    rotation_idx = 0
    last_poll = 0.0
    
    # Debug: show mapping
    print("[DEBUG] Mapping unit_id -> microbit_id:")
    for uid, mid in list(unit_to_microbit.items())[:5]:
        print(f"  {uid} -> {mid}")
    if len(unit_to_microbit) > 5:
        print(f"  ... et {len(unit_to_microbit) - 5} autres")

    try:
        while True:
            now = time.time()

            # Fetch new data from simulator
            if now - last_poll >= poll_interval:
                states = fetch_simulator_tick(simulator_url, 1)
                last_poll = now
                
                if states is None:
                    print("[DEBUG] Simulateur ne répond pas ou erreur")
                elif len(states) == 0:
                    print("[DEBUG] Simulateur a retourné 0 états")
                else:
                    # Debug: show first few states
                    if rotation_idx == 0:  # Only print once per rotation
                        print(f"[DEBUG] Simulateur: {len(states)} états reçus")
                        for s in states[:3]:
                            uid = s.get("unitId") or s.get("unit_id")
                            mb = unit_to_microbit.get(uid, "NON MAPPÉ")
                            print(f"  unit_id={uid} -> microbit={mb}")
                    
                    for state in states:
                        unit_id = state.get("unitId") or state.get("unit_id")
                        if not unit_id:
                            continue
                        
                        microbit_id = unit_to_microbit.get(unit_id)
                        if not microbit_id:
                            continue

                        lat = state.get("lat") or state.get("latitude")
                        lon = state.get("lon") or state.get("longitude")
                        if lat is None or lon is None:
                            continue

                        microbit_latest[microbit_id] = {
                            "lat": float(lat),
                            "lon": float(lon),
                            "status": state.get("status")
                        }
                        
                        if microbit_id not in microbit_ids:
                            microbit_ids.append(microbit_id)
                    
                    # Send ALL microbits immediately after receiving data
                    print(f"[INFO] Envoi de {len(microbit_ids)} microbits...")
                    for microbit_id in microbit_ids:
                        data = microbit_latest.get(microbit_id)
                        if data:
                            send_gps_command(ser, microbit_id, data["lat"], data["lon"])
                            if data.get("status"):
                                send_status_command(ser, microbit_id, data["status"])
                            time.sleep(0.2)  # 200ms between each microbit
                    print("[INFO] Envoi terminé")

            time.sleep(0.1)

    except KeyboardInterrupt:
        print("\n[INFO] Arrêt du bridge émetteur")
    finally:
        ser.close()
        print("[INFO] Connexion fermée")


if __name__ == "__main__":
    main()
