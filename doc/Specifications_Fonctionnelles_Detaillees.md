# Spécifications Fonctionnelles Détaillées (SFD)

## 1. Introduction
Ce document détaille les fonctionnalités attendues du système **Fast Pin Pon**, une solution de gestion et de suivi des incidents pour le SDMIS. Il décrit les acteurs, les cas d'usage et les exigences fonctionnelles.

## 2. Acteurs du Système

| Acteur | Rôle |
| :--- | :--- |
| **Opérateur QG (Classic)** | Visualise la carte en temps réel, suit les incidents et les unités. |
| **Superviseur (Supérieur)** | En plus des droits Opérateur, il peut créer des incidents et assigner manuellement des unités. |
| **Administrateur (IT)** | Gestion complète du système, incluant la flotte de véhicules, la suppression forcée d'incidents, et l'accès aux logs systèmes. |
| **Unité Terrain** | Véhicule physique ou simulé qui reçoit des ordres d'intervention et transmet sa position/statut. |
| **Système Automatisé** | Comprend le moteur de décision (Engine) et le simulateur d'incidents (IncidentCreation). |

## 3. Cas d'Usage Principaux

### 3.1 Gestion des Incidents
*   **Création d'Incident** (Acteur: Superviseur, Admin, Système)
    *   Le système permet de déclarer un incident (Incendie, Accident, etc.) à une position donnée.
    *   L'incident est caractérisé par un type, une sévérité et une localisation.
*   **Suivi du Cycle de Vie**
    *   États possibles : `PENDING` (En attente), `ASSIGNED` (Unités en route), `RESOLVED` (Terminé).
    *   La clôture est automatique une fois l'intervention terminée par les unités sur place.

### 3.2 Gestion des Ressources (Unités)
*   **Visualisation Temps Réel** (Tous les acteurs)
    *   Affichage sur carte des positions des véhicules.
    *   Indication du statut par code couleur (Disponible, En Intervention, Indisponible).
*   **Assignation** (Acteur: Moteur de Décision ou Superviseur)
    *   *Automatique* : Le moteur calcule l'unité la plus pertinente (temps de trajet, capacité) et l'assigne.
    *   *Manuelle* : Un superviseur peut forcer l'assignation d'une unité spécifique.

### 3.3 Communication Terrain (IoT)
*   **Transmission de Position** : Les unités transmettent leur position GPS via un pont radio (Micro:bit) ou 4G simulée.
*   **Mise à jour de Statut** : Les unités peuvent changer leur statut (ex: "Sur les lieux", "Disponible") via l'interface matérielle.

## 4. Exigences Fonctionnelles

### F1. Cartographie et Supervision
*   Le système doit afficher une carte interactive (MapTiler/Leaflet).
*   La carte doit se rafraîchir automatiquement (ou via WebSocket) pour afficher les mouvements des unités.
*   Les incidents doivent être représentés par des icônes distinctes selon leur type.

### F2. Moteur de Décision (Dispatch)
*   Le système doit calculer périodiquement les besoins en ressources pour les incidents en attente.
*   L'algorithme doit privilégier la rapidité d'intervention tout en conservant une couverture opérationnelle globale.

### F3. Administration et Sécurité
*   L'accès doit être sécurisé par authentification (SSO Keycloak).
*   Les actions critiques (suppression, modification de flotte) doivent être réservées aux administrateurs.
*   Toutes les actions doivent être journalisées.

### F4. Simulation
*   Un mode simulation doit permettre de générer du trafic et des incidents aléatoires pour tester la robustesse du système et la formation des opérateurs.
