# Manuel de Déploiement

## 1. Prérequis

Avant de commencer l'installation, assurez-vous de disposer des outils suivants sur votre machine hôte (ou serveur) :

*   **Docker Desktop** (ou Docker Engine + Docker Compose Plugin) v24+
*   **Git**
*   **Python 3.8+** (Uniquement pour le pont radio Micro:bit)
*   **Navigateur Web Moderne** (Chrome, Firefox, Edge)

## 2. Installation de la Stack Logicielle

### Étape 1 : Récupération du Code
Clonez le dépôt Git du projet :
```bash
git clone https://github.com/loan-mgt/fast-pin-pon.git
cd fast-pin-pon
```

### Étape 2 : Configuration de l'Environnement
Copiez le fichier d'exemple pour créer votre configuration locale :
```bash
cp .env.example .env
```
> [!IMPORTANT]
> Editez le fichier `.env` et modifiez les valeurs par défaut (mots de passe, clés secrètes) indiquées par `CHANGE_ME`.

### Étape 3 : Lancement des Services
Démarrez l'ensemble de l'infrastructure en mode développement (avec logs détaillés et rechargement à chaud) :

```bash
docker compose -f docker-compose.dev.yml up -d --build
```
*Pour la production, utilisez : `docker compose -f docker-compose.prod.yml up -d`*

### Étape 4 : Vérification
Attendez quelques instants que les services s'initialisent (notamment la base de données et Keycloak).
Accédez ensuite à l'interface via : **http://localhost:8080**

## 3. Installation du Matériel (Pont Radio)

Cette étape n'est nécessaire que si vous utilisez les boîtiers physiques Micro:bit.

### Flashez les Micro:bits
1.  Connectez la 1ère carte (Unité) et flashez `network/unit.py` (via https://python.microbit.org/).
2.  Connectez la 2ème carte (Relais) et flashez `network/relay.py`.

### Lancer le Pont Logiciel
Sur la machine connectée aux Micro:bits :
```bash
cd network
pip install -r requirements.txt
python bridge_receiver.py
```
*(Référez-vous au README.md Section Network pour les détails des ports série)*
