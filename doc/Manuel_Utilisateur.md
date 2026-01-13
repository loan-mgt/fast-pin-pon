# Manuel Utilisateur - Fast Pin Pon

## Vue Générale

Fast Pin Pon est une application de gestion et dispatch d'interventions d'urgence. Elle permet de visualiser en temps réel les incidents, les unités de secours, et de gérer les assignations.

## Interface Principale

### La Carte Interactive

La carte est le cœur de l'application. Elle affiche trois types d'éléments :

#### Incidents (Marqueurs en losange)
Les incidents sont représentés par des icônes en forme de losange. Leur **couleur dépend de la sévérité** (pas du statut) :
- **Jaune** — Sévérité faible (1-2)
- **Orange** — Sévérité moyenne (3)
- **Rouge** — Sévérité élevée (4-5)

Chaque type d'incident a une icône distinctive :
- 🔥 **Incendie urbain** — Flamme stylisée
- 🏭 **Incendie industriel** — Usine avec flamme
- 🚗 **Accident** — Impact/collision
- 🏥 **Secours médical** — Tracé ECG
- 🌊 **Sauvetage aquatique** — Bouée
- ☢️ **Risque chimique (HAZMAT)** — Masque
- ❓ **Autre** — Points de suspension

Sous un incident, des **badges d'unités** peuvent apparaître pour montrer les véhicules actuellement sur place.

Un badge **"Manuel"** (orange) indique qu'un incident est géré manuellement (non simulé).

#### Unités (Marqueurs circulaires)
Les unités sont représentées par des cercles avec une icône indiquant leur type. Leur **couleur dépend de leur statut** :
- **Vert** — `Disponible` : prête pour une nouvelle mission
- **Jaune** — `En route` : se déplaçant vers un incident
- **Bleu** — `Sur place` : en intervention sur un incident
- **Rouge** — `Indisponible` : temporairement hors service
- **Gris** — `Hors ligne` : non connectée au système

Types d'unités disponibles :
| Code | Nom | Description |
|------|-----|-------------|
| FPT | Fourgon Pompe Tonne | Véhicule d'incendie principal |
| FPTL | Fourgon Pompe Tonne Léger | Véhicule d'incendie léger |
| VSAV | Véhicule de Secours aux Victimes | Ambulance des pompiers |
| VER | Véhicule d'Extraction et Relevage | Désincarcération |
| VIA | Véhicule d'Intervention Aquatique | Sauvetage nautique |
| VIM | Véhicule d'Intervention en Milieux | Environnements spéciaux |
| VLHR | Véhicule Léger Hors Route | Terrains difficiles |
| EPA | Échelle Pivotante Automatique | Grande échelle |

#### Casernes (Marqueurs carrés violets)
Les casernes sont représentées par des carrés violets avec une icône de maison. Elles indiquent les bases d'où partent les unités.

### Panneau des Unités (à droite)
Affiche la liste des unités avec leur statut actuel et leur indicatif radio.

### Panneau des Événements (à droite)
Liste les incidents actifs, triés par date. Cliquer sur un incident ouvre le panneau de détails.

### Bandeau d'Activité (en haut)
Affiche les dernières actions du système en temps réel : assignations, changements de statut, etc.

---

## Gestion des Incidents (Superviseurs)

### Déclarer un Incident

#### Depuis la carte (clic droit)
1. Zoomez sur la carte à l'emplacement souhaité
2. **Clic droit** sur la carte → Le formulaire de création s'ouvre
3. Remplissez :
   - **Type d'incident** : Incendie, Accident, Secours médical, etc.
   - **Titre** : Description courte
   - **Sévérité** : 1 (Faible) à 5 (Critique)
4. Cliquez sur **Créer**

L'incident apparaît immédiatement sur la carte avec la couleur correspondant à sa sévérité.

#### Depuis la barre de navigation
1. Cliquez sur le bouton **"+ Incident"** dans la barre de navigation
2. Remplissez le formulaire avec une **adresse** (recherche géocodée)
3. Validez

### Consulter les Détails d'un Incident
Cliquez sur un incident sur la carte ou dans le panneau latéral. Le panneau de détails affiche :
- Titre et description
- Sévérité (Faible, Moyen, Élevé)
- Statut de l'intervention
- Date de signalement
- Adresse
- Liste des unités assignées avec leur statut

### Assigner des Unités

1. Ouvrez le panneau de détails d'un incident
2. Cliquez sur le bouton **+** à côté de "Unités assignées"
3. Le dialogue d'assignation s'ouvre avec deux options :

   **Assignation Automatique :**
   - Cliquez sur **"Auto-assigner"**
   - Le système sélectionne automatiquement les unités les plus adaptées (distance, capacités)

   **Assignation Manuelle :**
   - Parcourez la liste des unités disponibles
   - Cliquez sur une unité pour voir ses détails (distance, type)
   - Cliquez sur **"Assigner"** pour l'ajouter à l'intervention

4. L'unité passe automatiquement en statut **"En route"**

### Libérer une Unité
Dans le panneau de détails de l'incident :
1. Repérez l'unité à libérer dans la liste
2. Cliquez sur le bouton **✕** (croix rouge)
3. Confirmez la libération

L'unité redevient **Disponible**.

### Clore un Incident
1. Ouvrez le panneau de détails
2. Cliquez sur l'icône **poubelle** en haut à droite
3. Confirmez la clôture

L'incident passe en statut **"Terminé"** et disparaît de la vue principale.

---

## Tableau de Bord (Dashboard)

Accessible via **"Dashboard"** dans la barre de navigation (superviseurs uniquement).

### Filtrage par Caserne
Utilisez le menu déroulant pour filtrer les unités par caserne.

### Statistiques Affichées
- Liste des unités avec leur statut actuel
- Caserne d'appartenance
- Dernière activité

---

## Historique

Accessible via **"History"** dans la barre de navigation.

Affiche l'historique des interventions passées avec :
- Titre de l'incident
- Type d'événement
- Date de création
- Statut final (Terminé, Annulé)

Utiliser les filtres pour rechercher des interventions spécifiques.

---

## Monitoring (Supervision)

Accessible via **"Monitoring"** dans la barre de navigation.

### Services Surveillés
- **Base de Données** : État de la connexion PostgreSQL
- **Simulation** : État du moteur de simulation
- **Moteur Engine** : État du service d'assignation intelligente

### Réseau Micro:bit
- **Statut** : Actif/Inactif selon la réception de messages
- **Dernier message** : Temps écoulé depuis le dernier signal

### Métriques Système
- Nombre d'unités actives
- Nombre d'incidents en cours

---

## Modes de Fonctionnement

L'application peut fonctionner en deux modes, affichés dans le monitoring :

- **Simulation Automatique (Demo)** : Les unités sont simulées et se déplacent automatiquement
- **Hybride / Hardware** : Les unités sont connectées via des appareils Micro:bit physiques

---

## Raccourcis et Astuces

| Action | Comment |
|--------|---------|
| Créer un incident rapidement | Clic droit sur la carte |
| Centrer sur un incident | Bouton 📍 dans le panneau de détails |
| Voir les unités sur place | Les badges sous l'icône de l'incident |
| Rafraîchir les données | Bouton de rafraîchissement ou attendre l'auto-refresh |
| Localiser une unité assignée | Bouton 📍 vert à côté de l'unité |

---

## Rôles et Permissions

| Fonctionnalité | Opérateur | Superviseur | Admin |
|----------------|-----------|-------------|-------|
| Voir la carte | ✓ | ✓ | ✓ |
| Créer un incident | ✗ | ✓ | ✓ |
| Assigner des unités | ✗ | ✓ | ✓ |
| Clore un incident | ✗ | ✓ | ✓ |
| Accéder au Dashboard | ✗ | ✓ | ✓ |
| Ajouter une unité | ✗ | ✗ | ✓ |
| Accéder aux paramètres | ✗ | ✗ | ✓ |
