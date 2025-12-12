# Document de Spécifications Fonctionnelles

## 1. Frontend

### 1.1 Interface Carte

* Afficher les incidents et les unités sur une carte géographique.
* Mettre à jour périodiquement la carte (toutes les 5 secondes via des requêtes HTTP/SEE).
* Supporter les fonctionnalités interactives telles que zoom, déplacement et sélection.

### 1.2 Tableau de Bord

* Gestion du personnel :

  * Créer, modifier et supprimer des unités.
* Afficher des pictogrammes pour :

  * Véhicules
  * Incidents
  * Variations de statut des véhicules et des incidents.

### 1.3 Gestion des Incidents

* Créer un nouvel incident avec les détails pertinents.
* Afficher un tableau des incidents ("Boîte de réception") avec filtres.
* Assignation automatique des unités via appel API.
* Assignation manuelle des unités aux incidents.
* Modifier les détails d’un incident : statut, durée, etc.

### 1.4 Authentification & Journalisation

* Connexion via Keycloak.
* Journalisation des activités du frontend dans Grafana (via Loki) avec métriques.

---

## 2. Backend / API

* Fournir une interface simple pour interagir avec la base de données.
* Exposer des endpoints pour :

  * Création, mise à jour et récupération des incidents.
  * Informations et mises à jour des unités.
  * Requêtes d’assignation et mises à jour des statuts.

---

## 3. Module de Simulation

* Créer des événements/incidents aléatoires.
* Clore les incidents après leur traitement.
* Simuler le déplacement des unités sur la carte.

---

## 4. Module de Prise de Décision

* Recevoir les requêtes d’assignation.
* Interroger l’API pour obtenir les informations des unités.
* Calculer les décisions optimales pour l’assignation des unités.
* Répondre avec les résultats d’assignation.

---

## 5. Base de Données

* PostgreSQL avec extension PostGIS.
* Stocker :

  * Données des incidents
  * Données des unités
  * Positions géographiques
* Supporter les requêtes spatiales pour la cartographie et la prise de décision.

---

## 6. Module Passerelle

* Combinaison d’un PC et d’un microcontrôleur connecté en série.
* Convertir les appels API reçus via WebSocket en commandes pour le microcontrôleur.
* Transmettre les changements d’état des unités du microcontrôleur vers l’API.

---

## 7. Microcontrôleur

* **Fonctionnalités version 1 :**

  * Définir le statut d’une unité via un bouton.
  * Recevoir la position GPS depuis le simulateur.
  * Envoyer la position et le statut mis à jour vers l’API.

---

## 8. Grafana

* Afficher les métriques provenant des différents services.
* Agréger les logs de différents modules.
* Supporter la visualisation de la logique métier.

---

## 9. Redis (Couche Cache)

* Mise en cache optionnelle des données fréquemment mises à jour (ex. coordonnées GPS).
* Réduire la charge sur la base de données PostGIS.
* Garantir des mises à jour rapides tout en évitant la saturation de la base.

---

## 10. Interactions des Modules

1. Frontend → API → Base de données → Module de décision → API → Frontend.
2. Simulateur → API → Base de données → Frontend (pour les mises à jour).
3. Microcontrôleur → Passerelle → API → Base de données → Frontend.
4. Logs de tous les modules → Grafana/Loki.
5. Cache Redis entre les mises à jour frontend/simulateur et la base de données pour réduire la charge.
