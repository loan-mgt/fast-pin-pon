# Protection et Robustesse de la Solution

Ce document décrit les mesures prises pour garantir la sécurité, la fiabilité et la résilience du système **Fast Pin Pon**.

## 1. Sécurité (Protection)

### 1.1 Authentification et Gestion des Identités (IAM)
*   **Keycloak Centralisé** : Aucune gestion d'utilisateur "maison". Tout passe par le standard OpenID Connect (OIDC).
*   **Mots de Passe** : Stockés hashés et salés dans la base de données de Keycloak, jamais en clair.
*   **Tokens JWT** : Les échanges API sont sécurisés par des "Access Tokens" à courte durée de vie, signés numériquement (RS256).

### 1.2 Isolation Réseau
*   **Docker Networks** :
    *   Le réseau `internal` isole les services backend (Engine, Simulation, DB) d'Internet.
    *   Seul le Frontend (port 8080) et l'API Gateway (via reverse proxy) sont exposés.
*   **Base de Données** : Non exposée publiquement. Accessible uniquement par les conteneurs autorisés.

### 1.3 Sécurité des Échanges Radio (IoT)
*   **Problème** : Les ondes radio sont publiques et interceptables.
*   **Solution** :
    *   **Chiffrement Pré-partagé** : Utilisation d'un XOR avec clé tournante (PSK) pour rendre les payloads illisibles sans la clé.
    *   **Anti-Rejeu (Replay Attack)** : Chaque paquet contient une Séquence (SEQ) incrémentale. Tout paquet avec SEQ <= dernier reçu est rejeté.
    *   **Intégrité** : Signature CRC + Salt pour valider que le message n'a pas été altéré en transit.

## 2. Robustesse et Fiabilité

### 2.1 Résilience aux Pannes (Self-Healing)
*   **Policies Docker** : Tous les conteneurs ont une politique `restart: unless-stopped` ou `on-failure`. En cas de crash (ex: OutOfMemory), Docker les redémarre automatiquement.
*   **Healthchecks** : Chaque service critique (DB, API) expose une route `/healthz`. Les services dépendants (ex: Engine) attendent que leurs dépendances soient `healthy` avant de démarrer.

### 2.2 Gestion de la Charge
*   **Architecture Asynchrone** : Le moteur de décision ne bloque pas l'API. Il travaille en tâche de fond et met à jour la base de données de manière asynchrone.
*   **Scalabilité** : L'API est "Stateless", permettant de lancer plusieurs instances en parallèle derrière un Load Balancer sur les gros déploiements.

### 2.3 Mode Dégradé
*   **Perte de Connexion Radio** : Si une unité perd le contact, elle garde sa dernière position connue ("Last Seen"). Le système signale l'unité comme "Hors Réseau" mais ne plante pas.
*   **Panne du Moteur d'Assignation** : Si le moteur de décision tombe, les opérateurs peuvent toujours assigner *manuellement* les unités aux incidents via l'interface, garantissant la continuité opérationnelle.
