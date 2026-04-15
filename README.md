# GuessGame-Java

Projet Java du jeu **Guess Game** réalisé dans le cadre du cours **6GEN723 - Réseaux d’ordinateurs**.

## Description

Guess Game est une application réseau en Java basée sur une architecture **client-serveur**.  
Le serveur gère les connexions, les salles, le démarrage des parties, les secrets, les propositions des joueurs et le mode de jeu contre le serveur.  
Le client permet à un joueur de se connecter, créer ou rejoindre une salle, jouer, consulter l’historique, envoyer des messages P2P et interagir avec les autres joueurs.

Le protocole utilisé suit le format :

```text
GG|TYPE|champ1|champ2|...
```

Exemples :
- `GG|CONNECT|Oussama`
- `GG|CREATE_ROOM|Home|5|5`
- `GG|JOIN_ROOM|Home`
- `GG|GUESS|Home|oussa|ROUGE|BLEU|VERT|JAUNE`

---

## Structure du projet

```text
GuessGame-Java
│
├── client/
│   └── src/
│       ├── ClientApp.java
│       ├── ClientMain.java
│       ├── ConsoleUI.java
│       ├── GGMessage.java
│       ├── MessageParser.java
│       ├── PeerConnection.java
│       └── PeerServer.java
│
├── serveur/
    └── src/
        ├── ClientHandler.java
        ├── GGMessage.java
        ├── MessageParser.java
        ├── PlayerInfo.java
        ├── Room.java
        ├── RoomManager.java
        └── ServerMain.java


```

---

## Contenu des dossiers

### `client/src`
Contient tout le code du client.

#### `ClientMain.java`
Point d’entrée du client.  
Cette classe lit l’adresse IP et le port du serveur, puis lance l’application cliente.

#### `ClientApp.java`
Classe principale du client.  
Elle gère :
- la connexion au serveur
- le menu principal
- la navigation entre les salles
- la préparation de partie
- le mode secret
- le mode guess
- la fin de partie
- le mode contre le serveur
- la communication avec le serveur via TCP

C’est la classe la plus importante côté client.

#### `ConsoleUI.java`
Gère l’affichage des menus et les interactions avec l’utilisateur dans la console.  
Elle centralise les saisies clavier pour garder `ClientApp` plus lisible.

#### `GGMessage.java`
Représente un message du protocole GG.  
Permet d’accéder facilement :
- au préfixe
- au type
- aux champs du message

#### `MessageParser.java`
Parse les chaînes réseau du type :

```text
GG|TYPE|champ1|champ2|...
```

et les transforme en objet `GGMessage`.

#### `PeerConnection.java`
Permet d’envoyer un message P2P vers un autre client.

#### `PeerServer.java`
Petit serveur local lancé côté client pour recevoir des messages P2P.

---

### `serveur/src`
Contient tout le code du serveur.

#### `ServerMain.java`
Point d’entrée du serveur.  
Cette classe :
- démarre le serveur TCP
- crée les salles par défaut
- accepte les connexions entrantes
- lance un `ClientHandler` pour chaque client

#### `ClientHandler.java`
Classe principale côté serveur pour gérer un client connecté.  
Elle traite les commandes reçues, par exemple :
- `CONNECT`
- `CREATE_ROOM`
- `JOIN_ROOM`
- `START_GAME`
- `SECRET_SET`
- `GUESS`
- `GET_GUESSES`
- `GET_ROOM_PLAYERS`
- `KICK_PLAYER`
- `PLAY_SERVER`

Elle contient la logique réseau principale entre client et serveur.

#### `RoomManager.java`
Gère l’ensemble des salles du jeu.  
Permet :
- de créer une salle
- de récupérer une salle
- de lister les salles
- de faire rejoindre ou quitter un joueur

#### `Room.java`
Représente une salle de jeu.  
Elle contient :
- le nom de la salle
- la liste des joueurs
- le nombre maximal de joueurs
- le nombre maximal de tentatives
- l’état de la partie
- le secret
- l’historique des propositions
- le gagnant

#### `PlayerInfo.java`
Représente un joueur connecté côté serveur.  
Contient les informations utiles comme :
- le nom du joueur
- son socket
- son flux de sortie
- la salle courante

#### `GGMessage.java`
Même rôle que côté client : représentation d’un message GG.

#### `MessageParser.java`
Même rôle que côté client : analyse du message brut reçu.

---

## Fonctionnalités principales

- Connexion de plusieurs clients au serveur
- Création et gestion de salles
- Liste des salles disponibles
- Rejoindre / quitter une salle
- Démarrer une partie
- Définir une combinaison secrète
- Envoyer des propositions
- Recevoir un feedback
- Consulter l’historique des propositions
- Expulser un joueur
- Rejouer dans la même salle
- Mode contre le serveur
- Messages P2P entre clients

---

## Compilation et exécution

### Démarrer le serveur

Depuis `serveur/src` :

```bash
javac *.java
java ServerMain
```

Ou avec un port personnalisé :

```bash
java ServerMain 5050
```

### Démarrer un client

Depuis `client/src` :

```bash
javac *.java
java ClientMain 127.0.0.1 5050
```

Exemple sur réseau local :

```bash
java ClientMain 192.168.1.219 5050
```

---

## Notes importantes

- `127.0.0.1` fonctionne seulement si le client et le serveur sont sur le même ordinateur.
- Pour jouer entre plusieurs ordinateurs, il faut utiliser l’adresse IP locale du serveur.
- Le pare-feu Windows ou le réseau Wi-Fi peut bloquer les connexions entre appareils.
- Certaines interfaces réseau virtuelles (VMware, VirtualBox, etc.) peuvent afficher des IP inutiles pour les autres joueurs.

---

## Auteurs
Oussama Maaroufi 
