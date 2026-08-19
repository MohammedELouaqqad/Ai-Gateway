# Guide RAG — comprendre l'implémentation, fichier par fichier

Ce doc explique **simplement** à quoi sert chaque fichier du RAG dans ce projet, et comment ils
s'enchaînent. Pas de jargon inutile — l'idée est de pouvoir ouvrir n'importe quel fichier du RAG et
savoir immédiatement "ah oui, c'est LUI qui fait ça".

## Vue d'ensemble — les 2 moitiés du RAG

Le RAG a toujours deux moitiés complètement séparées :

1. **Ingestion** : "j'upload un PDF" → le système le prépare pour plus tard (ça se passe **une
   fois**, en arrière-plan, pas pendant que tu discutes).
2. **Retrieval** : "je pose une question dans le chat" → le système va chercher les bons passages
   du PDF et les donne au modèle (ça se passe **à chaque message**, en direct).

```
INGESTION (une fois, en arrière-plan)
Upload PDF → Extraction texte → Découpage en morceaux → Vectorisation → Stockage dans Qdrant

RETRIEVAL (à chaque message du chat)
Question → Vectorisation → Recherche dans Qdrant → Injection dans le prompt → Réponse du modèle
```

## 1. Ingestion — "je transforme un PDF en quelque chose de cherchable"

### `RagDocumentController.java` + `ConversationRagDocumentController.java`
Les portes d'entrée HTTP. `RagDocumentController` = upload/liste/suppression d'un document.
`ConversationRagDocumentController` = attacher/détacher un document déjà uploadé à une
conversation précise (many-to-many : un document peut servir dans plusieurs conversations).

### `RagDocumentService.java`
Le "chef d'orchestre" de l'upload. Reçoit le fichier, vérifie que c'est bien un PDF (via les
premiers octets du fichier — les "magic bytes" — jamais juste l'extension `.pdf`, trop facile à
falsifier), vérifie la taille, vérifie que l'utilisateur n'a pas dépassé sa limite d'uploads/heure,
sauvegarde le fichier sur disque, et crée une ligne en base avec le statut `PENDING` ("en
attente de traitement"). **Il ne fait PAS l'extraction/découpage/vectorisation lui-même** — ça
prendrait trop de temps pour une requête HTTP, donc il délègue au job planifié ci-dessous.

### `RagIngestionJob.java`
Le "travailleur en arrière-plan". Tourne tout seul toutes les 15 secondes (`@Scheduled`), repère
les documents encore `PENDING`, et fait le vrai travail lourd pour chacun : extraction → découpage
→ vectorisation → envoi à Qdrant → passage du statut à `READY` (ou `FAILED` si ça a raté). C'est
lui qui utilise les 3 fichiers suivants, un par un, dans l'ordre.

### `PdfTextExtractor.java`
Sort le texte brut d'un PDF (via la librairie PDFBox). Protégé par un timeout (30s par défaut) et
un plafond de caractères extraits (2 millions), pour qu'un PDF piégé/énorme ne bloque pas le job
indéfiniment.

### `TextChunker.java`
Découpe le texte extrait en petits morceaux ("chunks") de ~2000 caractères, avec un léger
chevauchement entre morceaux voisins (~300 caractères) — pour qu'une info coupée pile à la
frontière entre deux morceaux reste quand même trouvable dans l'un des deux. Découpage purement
mécanique (compte de caractères), aucune compréhension du contenu.

### `TeiEmbeddingService.java`
Transforme un morceau de texte en vecteur numérique (une liste de ~768 nombres) via un serveur
d'embedding dédié (`embedding-server` dans `docker-compose.yml`, HuggingFace Text Embeddings
Inference — **pas** Ollama, complètement indépendant du provider choisi pour le chat). C'est ce
vecteur qui permet plus tard de faire une recherche "par sens" plutôt qu'une recherche par
mot-clé exact.

### `QdrantClient.java`
Parle à Qdrant (la base de données vectorielle) en HTTP simple. Trois usages : `upsertPoints()`
pour stocker les vecteurs+texte d'un document, `deleteByDocumentId()` pour les effacer (suppression
ou ré-ingestion), et `search()` (utilisé côté retrieval, voir plus bas) pour retrouver les morceaux
les plus proches d'une question.

## 2. Retrieval — "je réponds à une question en utilisant les documents"

### `RagRetrievalService.java`
Appelé à **chaque** message envoyé dans une conversation qui a des documents `READY` attachés.
Transforme la question de l'utilisateur en vecteur (même `TeiEmbeddingService` que l'ingestion),
demande à Qdrant les 5 morceaux les plus proches (`QdrantClient.search()`), et les formate en un
message "SYSTEM" clairement étiqueté comme "extrait de document, pas une instruction" (pour éviter
qu'un texte piégé dans un document ne soit pris pour un ordre par le modèle — voir
`docs/rag-security-review.md`). Si Qdrant ou l'embedding échoue, il ne fait pas planter le chat —
la conversation continue juste sans contexte documentaire.

### `ChatService.java` (juste la partie RAG)
C'est lui qui **déclenche** `RagRetrievalService` à chaque message, juste avant d'appeler le
modèle — et qui insère le message SYSTEM renvoyé dans la liste des messages envoyés au LLM. Il
compte aussi combien de morceaux ont été utilisés (`ragChunksUsed`) pour que le frontend puisse
afficher un badge honnête "N sources" sur la réponse.

## 3. Les fichiers de "support" (config, données, petits objets)

| Fichier | Rôle en une phrase |
|---|---|
| `config/RagProperties.java` | Tous les réglages RAG au même endroit (taille max, tailles de chunk, top-K, host Qdrant...) — rien n'est en dur ailleurs dans le code. |
| `entity/RagDocument.java` | La ligne en base pour un document uploadé : qui le possède, où il est stocké, son statut (PENDING/READY/FAILED). |
| `enums/RagDocumentStatus.java` | Les 4 statuts possibles d'un document, rien de plus. |
| `entity/Conversation.java` (champ `ragDocuments`) | La liaison many-to-many : quels documents sont attachés à quelle conversation. |
| `entity/Message.java` (champ `ragChunksUsed`) | Combien de morceaux ont réellement servi pour CETTE réponse précise — le badge "N sources" en dépend. |
| `repositories/RagDocumentRepository.java` | Les requêtes base de données (Spring Data JPA) pour aller chercher les documents. |
| `services/dto/RagDocumentView.java` | La version "publique" d'un `RagDocument" renvoyée par l'API (cache les champs internes comme `storageKey`/`sha256`). |
| `services/rag/dto/QdrantPoint.java` | Ce qu'on envoie à Qdrant à l'ingestion (vecteur + texte + métadonnées). |
| `services/rag/dto/RagChunkHit.java` | Ce que Qdrant nous renvoie à la recherche (un morceau trouvé + son score de pertinence). |

## 4. Frontend

### `frontend/src/api/rag.ts`
Les appels HTTP vers le backend RAG — upload, liste, suppression, attacher/détacher à une
conversation. Rien d'autre, pas de logique métier ici.

### `frontend/src/pages/ChatPage.tsx` (partie RAG)
L'affichage : le panneau "Documents" (upload, liste, statut, attacher/détacher), accessible via le
menu "+" du composer, et le badge "N sources" sur les réponses assistant qui ont utilisé du
contexte documentaire.

## 5. "Qui appelle qui" — en une image

```
UPLOAD
FileController… non pardon, RagDocumentController
  → RagDocumentService.upload()          (sauvegarde + statut PENDING)
      ↓ (15 sec plus tard, en arrière-plan)
  RagIngestionJob
      → PdfTextExtractor.extract()       (PDF → texte)
      → TextChunker.chunk()              (texte → morceaux)
      → TeiEmbeddingService.embed()      (morceau → vecteur, pour chaque morceau)
      → QdrantClient.upsertPoints()      (stocke tout dans Qdrant)
      → statut passe à READY

CHAT (à chaque message, si des documents READY sont attachés)
ChatService.prepareChat()
  → RagRetrievalService.retrieve()
      → TeiEmbeddingService.embed()      (question → vecteur)
      → QdrantClient.search()            (vecteur → 5 meilleurs morceaux)
      → RagRetrievalService.formatContext() (morceaux → message SYSTEM étiqueté)
  → le message SYSTEM est ajouté juste avant la question de l'utilisateur
  → le modèle répond avec ce contexte en plus
```

## 6. Si tu dois modifier quelque chose demain

- **Changer la taille des morceaux, le top-K, les timeouts...** → `RagProperties.java` +
  `docker-compose.yml`/`.env` (variable d'environnement correspondante, voir README principal).
- **Ajouter un nouveau format de fichier (Word, TXT...)** → nouveau fichier
  `services/rag/XxxTextExtractor.java` à côté de `PdfTextExtractor.java`, appelé depuis
  `RagIngestionJob` selon le type de fichier détecté.
- **Changer le modèle d'embedding** → argument `--model-id` du service `embedding-server` dans
  `docker-compose.yml` + `APP_RAG_EMBEDDING_DIMENSION` (doit correspondre exactement à ce que le
  nouveau modèle produit, sinon Qdrant refuse). TEI sert un seul modèle par instance, fixé au
  démarrage — pas de nom de modèle envoyé par requête, contrairement à Ollama.
- **Comprendre un bug de sécurité/accès** → `docs/rag-security-review.md`, section "Test 6"
  explique le double verrou anti-IDOR en détail.
