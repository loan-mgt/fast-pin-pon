**0\. Global**

**En tant que** centre de commandement (HQ)  
 **Je veux** recevoir en temps réel les informations provenant du réseau radio, des véhicules, des unités et des incidents  
 **Afin de** coordonner efficacement les interventions, allouer les ressources adéquates et suivre l’évolution des opérations sur le terrain.

### **1\. Transmission radio**

**En tant qu’** opérateur réseau  
 **Je veux** que les microbits transmettent de manière fiable des messages (positions GPS, statuts, fin d’intervention…)  
 **Afin de** garantir une synchronisation cohérente avec le système central même en cas de pertes radio.

### **2\. Intégrité et sécurité**

**En tant qu’** architecte sécurité  
 **Je veux** que les messages radio soient authentifiés, vérifiés et chiffrés  
 **Afin de** protéger l’intégrité et la confidentialité des données échangées.

### **3\. Protocoles de communication**

**En tant qu’** développeur embarqué  
 **Je veux** disposer d’un protocole clair gérant les messages périodiques et à la demande  
 **Afin de** standardiser la communication entre les équipements et le datacenter.

### **4\. Gestion des incidents**

**En tant qu’** opérateur au HQ  
 **Je veux** déclarer, suivre et clore un incident  
 **Afin de** maintenir une vision claire et à jour de toutes les situations en cours.

### **5\. Proposition automatique de ressources**

**En tant qu’** opérateur  
 **Je veux** recevoir des suggestions automatiques d’unités à déployer  
 **Afin de** réduire le temps décisionnel et optimiser les interventions.

### **6\. Coordination des interventions**

**En tant qu’** chef de salle  
 **Je veux** déclencher une intervention et suivre son cycle de vie (création → départ → arrivée → fin)  
 **Afin de** gérer efficacement les équipes sur le terrain.

### **7\. Sécurisation de l’accès**

**En tant qu’** administrateur  
 **Je veux** que l’accès au backend soit protégé via Keycloak et conforme aux standards OWASP  
 **Afin de** garantir un accès sécurisé et limité aux utilisateurs autorisés.

### **8\. Temps réel**

**En tant qu’** opérateur  
 **Je veux** voir les données (positions, statuts) s'actualiser en temps réel via REST et WebSocket  
 **Afin de** réagir immédiatement aux évolutions sur le terrain.

### **9\. Simulation d’incidents**

**En tant que** testeur ou développeur  
 **Je veux** générer des incidents simulés avec type, position, sévérité et évolution temporelle  
 **Afin de** tester le comportement du système complet.

### **10\. Simulation de véhicules**

**En tant qu’** ingénieur simulation  
 **Je veux** simuler des véhicules, leurs déplacements et leurs statuts  
 **Afin de** reproduire le fonctionnement des unités réelles.

### **11\. Statut des unités**

**En tant qu’**unité sur le terrain  
 **Je veux** pouvoir déclarer mon statut  
 **Afin d’**indiquer ma disponibilité