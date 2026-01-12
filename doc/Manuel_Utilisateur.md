# Manuel Utilisateur - Fast Pin Pon

## 1. Accès à l'Application

Connectez-vous à l'application via votre navigateur.
*   **Utilisateur invité** : Accès limité à la vue carte.
*   **Connexion** : Cliquez sur le bouton "Login" en haut à droite pour vous authentifier via Keycloak.
    *   *Login* : `admin` / *Password* : `CHANGE_ME` (ou selon votre configuration).

## 2. Interface Principale

### 2.1 La Carte Interactive
La carte est le cœur de l'application. Elle affiche :
*   **Incidents** : Icônes de feu, accident, etc.
    *   *Rouge* : En attente.
    *   *Orange* : En cours de traitement.
    *   *Vert* : Résolu (disparaît après un délai).
*   **Unités** : Icônes de véhicules.
    *   *Vert* : Disponible.
    *   *Rouge* : En intervention.

### 2.2 Le Tableau de Bord (Dashboard)
Accessible via le menu latéral pour les superviseurs.
*   **Liste des Incidents** : Tableau triable de tous les incidents actifs.
*   **Statistiques** : Graphiques du nombre d'incidents par heure, temps moyen d'intervention.

## 3. Gestion des Incidents (Superviseurs)

### Déclarer un Incident
1.  Zoomez sur la carte à l'endroit désiré.
2.  Clic droit sur la carte -> "Déclarer un Incident".
3.  Remplissez le formulaire :
    *   **Type** : Incendie, Accident, Malaise...
    *   **Sévérité** : 1 (Faible) à 5 (Critique).
4.  Validez. L'incident apparaît immédiatement en rouge.

### Assigner des Unités
1.  Cliquez sur une icône d'incident pour ouvrir le panneau de détails.
2.  **Assignation Auto** : Le système propose automatiquement les véhicules les plus proches. Cliquez sur "Auto Assign".
3.  **Assignation Manuelle** : Sélectionnez manuellement un véhicule dans la liste et cliquez sur "Dispatcher".

## 4. Gestion de la Flotte

### Voir les détails d'un véhicule
Cliquez sur un véhicule sur la carte pour voir :
*   Son ID et Type.
*   Sa vitesse actuelle.
*   Son statut (Statut Micro:bit si connecté).

### Ajouter un Véhicule (Admin)
Dans le panneau d'administration, cliquez sur "Ajouter une Unité", spécifiez ses capacités (Eau, Secours, Désincarcération) et sa base de départ.
