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
from typing import Optional, Dict, List, Tuple

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
        print(f"[DEBUG] TIMEOUT après {time.time() - start:.2f}s: {e}")
        return None
    except requests.ConnectionError as e:
        print(f"[DEBUG] CONNEXION REFUSÉE après {time.time() - start:.2f}s: {e}")
        return None
    except requests.RequestException as e:
        print(f"[DEBUG] Erreur simulateur après {time.time() - start:.2f}s: {e}")
        return None


def send_gps_command(ser: serial.Serial, microbit_id: str, lat: float, lon: float, call_sign: str = "") -> None:
    cmd = f"GPS:{microbit_id},{lat:.6f},{lon:.6f}"
    packet = build_packet(cmd)
    ser.write((packet + "\n").encode("utf-8"))
    display_name = call_sign if call_sign else microbit_id
    print(f"[EMIT] GPS:{display_name},{lat:.6f},{lon:.6f}")


def send_status_command(ser: serial.Serial, microbit_id: str, status: str, call_sign: str = "") -> None:
    cmd = f"STA:{microbit_id},{status}"
    packet = build_packet(cmd)
    ser.write((packet + "\n").encode("utf-8"))
    display_name = call_sign if call_sign else microbit_id
    print(f"[EMIT] STA:{display_name},{status}")


def load_microbit_mapping(api_url: str) -> Tuple[Dict[str, str], Dict[str, str]]:
    """Load mapping of unit_id -> microbit_id and unit_id -> call_sign from API"""
    try:
        response = requests.get(f"{api_url.rstrip('/')}/v1/units", timeout=8)
        response.raise_for_status()
        unit_to_microbit = {}
        unit_to_callsign = {}
        for unit in response.json():
            microbit_id = unit.get("microbit_id")
            unit_id = unit.get("id")
            call_sign = unit.get("call_sign", "")
            if microbit_id and unit_id:
                unit_to_microbit[unit_id] = microbit_id
                unit_to_callsign[unit_id] = call_sign
        return unit_to_microbit, unit_to_callsign
    except requests.RequestException:
        return {}, {}


def print_config(simulator_url: str, api_url: str, baud_rate: int, poll_interval: float) -> None:
    """Print configuration information."""
    print("=" * 50)
    print("  BRIDGE ÉMETTEUR (PC1)")
    print("=" * 50)
    print(f"[CONFIG] SIMULATOR_URL: {simulator_url}")
    print(f"[CONFIG] API_URL: {api_url}")
    print(f"[CONFIG] BAUD_RATE: {baud_rate}")
    print(f"[CONFIG] SIM_POLL_INTERVAL: {poll_interval}s")


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
        print(f"[INFO] Connecté au micro:bit relay sur {serial_port}")
        time.sleep(1)
        return ser
    except serial.SerialException as e:
        print(f"[ERREUR] Connexion: {e}")
        return None


def print_mapping_debug(unit_to_microbit: Dict[str, str]) -> None:
    """Print mapping debug information."""
    print("[DEBUG] Mapping unit_id -> microbit_id:")
    for uid, mid in list(unit_to_microbit.items())[:5]:
        print(f"  {uid} -> {mid}")
    if len(unit_to_microbit) > 5:
        print(f"  ... et {len(unit_to_microbit) - 5} autres")


def process_state(state: Dict, unit_to_microbit: Dict[str, str], 
                  microbit_latest: Dict[str, Dict], microbit_ids: List[str]) -> None:
    """Process a single state from the simulator."""
    unit_id = state.get("unitId") or state.get("unit_id")
    if not unit_id:
        return
    
    microbit_id = unit_to_microbit.get(unit_id)
    if not microbit_id:
        return

    lat = state.get("lat") or state.get("latitude")
    lon = state.get("lon") or state.get("longitude")
    if lat is None or lon is None:
        return

    microbit_latest[microbit_id] = {
        "lat": float(lat),
        "lon": float(lon),
        "status": state.get("status"),
        "call_sign": state.get("callSign") or state.get("call_sign", "")
    }
    
    if microbit_id not in microbit_ids:
        microbit_ids.append(microbit_id)


def send_all_microbits(ser: serial.Serial, microbit_ids: List[str], 
                       microbit_latest: Dict[str, Dict]) -> None:
    """Send data to all microbits."""
    print(f"[INFO] Envoi de {len(microbit_ids)} microbits...")
    for microbit_id in microbit_ids:
        data = microbit_latest.get(microbit_id)
        if data:
            call_sign = data.get("call_sign", "")
            send_gps_command(ser, microbit_id, data["lat"], data["lon"], call_sign)
            if data.get("status"):
                send_status_command(ser, microbit_id, data["status"], call_sign)
            time.sleep(0.2)
    print("[INFO] Envoi terminé")


def debug_log_states(states: List[Dict], unit_to_microbit: Dict[str, str]) -> None:
    """Log the first few states for debugging."""
    print(f"[DEBUG] Simulateur: {len(states)} états reçus")
    for s in states[:3]:
        uid = s.get("unitId") or s.get("unit_id")
        mb = unit_to_microbit.get(uid, "NON MAPPÉ")
        print(f"  unit_id={uid} -> microbit={mb}")


def handle_poll(ser: serial.Serial, simulator_url: str, unit_to_microbit: Dict[str, str], 
                microbit_latest: Dict[str, Dict], microbit_ids: List[str], 
                rotation_idx: int) -> int:
    """Handle a single poll cycle from the simulator."""
    states = fetch_simulator_tick(simulator_url, 1)
    if states is None:
        print("[DEBUG] Simulateur ne répond pas ou erreur")
        return rotation_idx
    
    if not states:
        print("[DEBUG] Simulateur a retourné 0 états")
        return rotation_idx

    if rotation_idx == 0:
        debug_log_states(states, unit_to_microbit)
    
    for state in states:
        process_state(state, unit_to_microbit, microbit_latest, microbit_ids)
    
    send_all_microbits(ser, microbit_ids, microbit_latest)
    return (rotation_idx + 1) % 10


def run_main_loop(ser: serial.Serial, simulator_url: str, poll_interval: float,
                  unit_to_microbit: Dict[str, str], api_url: str) -> None:
    """Run the main polling loop."""
    microbit_ids: List[str] = list(set(unit_to_microbit.values()))
    microbit_latest: Dict[str, Dict] = {}
    rotation_idx = 0
    last_poll = 0.0
    last_mapping_refresh = time.time()
    mapping_refresh_interval = 60  # Refresh mapping every 60 seconds

    while True:
        now = time.time()
        
        # Refresh mapping periodically to pick up newly assigned microbits
        if now - last_mapping_refresh > mapping_refresh_interval:
            print("[INFO] Rafraîchissement du mapping unités -> microbits...")
            new_mapping, _ = load_microbit_mapping(api_url)
            if len(new_mapping) != len(unit_to_microbit):
                print(f"[INFO] Mapping mis à jour: {len(new_mapping)} unités avec microbit assigné")
                print_mapping_debug(new_mapping)
            unit_to_microbit.clear()
            unit_to_microbit.update(new_mapping)
            microbit_ids.clear()
            microbit_ids.extend(list(set(new_mapping.values())))
            last_mapping_refresh = now
        
        if now - last_poll >= poll_interval:
            rotation_idx = handle_poll(ser, simulator_url, unit_to_microbit, 
                                       microbit_latest, microbit_ids, rotation_idx)
            last_poll = now

        time.sleep(0.1)


def main() -> None:
    serial_port = get_env("SERIAL_PORT", "")
    baud_rate = int(get_env("BAUD_RATE", "115200"))
    simulator_url = get_env("SIMULATOR_URL", SIMULATOR_DEFAULT_URL)
    api_url = get_env("API_URL", "http://localhost:8081")
    poll_interval = float(get_env("SIM_POLL_INTERVAL", "5"))

    print_config(simulator_url, api_url, baud_rate, poll_interval)

    ser = setup_serial(serial_port, baud_rate)
    if ser is None:
        return

    print("[INFO] Chargement du mapping unités -> microbits...")
    unit_to_microbit, _ = load_microbit_mapping(api_url)
    print(f"[INFO] {len(unit_to_microbit)} unités avec microbit assigné")
    
    print("\n[INFO] Bridge émetteur prêt. Envoi des données du simulateur...")
    print("-" * 60)

    print_mapping_debug(unit_to_microbit)

    try:
        run_main_loop(ser, simulator_url, poll_interval, unit_to_microbit, api_url)
    except KeyboardInterrupt:
        print("\n[INFO] Arrêt du bridge émetteur")
    finally:
        ser.close()
        print("[INFO] Connexion fermée")


if __name__ == "__main__":
    main()
