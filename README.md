# AI Gateway — MVP 1

Passerelle LLM (Spring Boot 3 + PostgreSQL + Ollama) avec interface d'administration React.

## Prérequis

- Java 17, Maven
- PostgreSQL (`Ai_Gateway_DB`)
- Node.js 18+
- [Ollama](https://ollama.com) avec un modèle local (ex. `qwen2.5:0.5b`)

## Démarrage rapide

```bash
# 1. Base de données
createdb Ai_Gateway_DB   # ou via pgAdmin

# 2. Backend (port 8080)
cd backend
./mvnw spring-boot:run

# 3. Frontend (port 5173)
cd frontend
npm install
npm run dev
```

Ouvrir http://localhost:5173

## Premier test

1. **Fournisseurs** → activer « Ollama Local »
2. **Modèles IA** → activer un modèle Ollama + le définir par défaut
3. **Chat** → envoyer un message
4. **Consommation** → vérifier les logs de tokens

## API principale

| Méthode | Route | Description |
|---------|-------|-------------|
| POST | `/api/chat` | Envoyer un message |
| GET/POST | `/api/conversations` | Conversations |
| GET/POST | `/api/models` | Modèles |
| GET/POST | `/api/providers` | Fournisseurs |
| GET | `/api/usage/summary` | Statistiques |

> Auth : `userId = 1` en dur (dev). Keycloak = Phase 2.

## Variables d'environnement

```bash
DB_URL=jdbc:postgresql://localhost:5432/Ai_Gateway_DB
DB_USERNAME=postgres
DB_PASSWORD=postgres
OLLAMA_BASE_URL=http://localhost:11434
APP_SEED_ENABLED=true
```

## Tests

```bash
cd backend && ./mvnw test
```
