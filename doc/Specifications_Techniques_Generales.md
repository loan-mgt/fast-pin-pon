# Spécifications Techniques Générales (STG)

## 1. Architecture Globale
Le projet **Fast Pin Pon** repose sur une architecture **Microservices** conteneurisée, orchestrée par Docker Compose.

### Diagramme d'Architecture Simplifié
*(Voir README.md pour les diagrammes Mermaid détaillés)*

*   **Frontend** (React): Interface utilisateur SPA.
*   **API Gateway / Backend** (Go): Point d'entrée unique, gestion de la logique métier et accès BDD.
*   **Engine** (Java): Service de calcul d'optimisation (Dispatch).
*   **Simulation** (Java): Moteur physique simulant le déplacement des véhicules.
*   **Database** (PostgreSQL/PostGIS): Persistance des données relationnelles et géospatiales.
*   **IoT Bridge** (Python): Passerelle pour les communications radio physiques (Micro:bit).

## 2. Technologies et Protocoles

### 2.1 Backend & Services
*   **Langages** :
    *   **Go (Golang)** : API principale (Haute performance, concurrence).
    *   **Java (JDK 21)** : Moteurs de Simulation et de Décision (Robustesse, écosystème).
    *   **Python** : Scripts de pont radio (Facilité d'interfaçage série/USB).
*   **Base de Données** : PostgreSQL 16 avec extension **PostGIS** pour les calculs spatiaux et **pgRouting** pour le calcul d'itinéraires.

### 2.2 Frontend
*   **Framework** : React (Vite).
*   **Cartographie** : Leaflet / MapLibre GL avec tuiles vectorielles (MapTiler).
*   **Communication** : REST API (Axios).

### 2.3 Protocoles de Communication
*   **Inter-services** : HTTP/1.1 (REST).
*   **Temps Réel** : Les clients sondent l'API (Polling) ou Server-Sent Events (selon implémentation).
*   **Radio (IoT)** : Protocole propriétaire sur 2.4GHz via Micro:bit.
    *   Format : `SEQ|ENCRYPTED_DATA|CRC|SIGN`
    *   Chiffrement : XOR avec clé partagée.
    *   Intégrité : CRC8 + Signature salée.

## 3. Sécurité
*   **Authentification (IAM)** : Centralisée via **Keycloak** (OpenID Connect / OAuth2).
*   **Rôles (RBAC)** : Gestion fine des permissions (`admin`, `standard`, `supervisor`).
*   **Chiffrement** :
    *   Communications Web : HTTPS (recommandé en prod).
    *   Communications Radio : Obfuscation/Chiffrement léger pour éviter le spoofing simple.

## 4. Modèle de Données (Schéma BDD Simplifié)

### Entités Principales
*   `units` : Véhicules (ID, Position, Statut, Type).
*   `incidents` : urgences (ID, Localisation, Type, Sévérité, Statut).
*   `interventions` : Lien entre Unit et Incident (Heure départ, arrivée, fin).
*   `unit_telemetry` : Historique des positions (Traces GPS).

## 5. Infrastructure et Déploiement
*   **Conteneurisation** : Images Docker optimisées (Distroless pour Java, Alpine pour Go).
*   **Orchestration** : Docker Compose pour le dev et la prod (profils distincts).
*   **Monitoring** : Stack Prometheus / Grafana / Loki pour les métriques et logs centralisés.
