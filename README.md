# AI Gateway

Passerelle LLM multi-provider (Spring Boot 3 + PostgreSQL + React) avec chat sync/stream, admin modèles/fournisseurs, usage et prompt templates.

## Fonctionnalités actuelles

| Domaine | État |
|---------|------|
| Chat sync (`POST /api/chat`) | Ollama + OpenAI (+ Azure compatible) |
| Chat stream SSE (`POST /api/chat/stream`) | Hérité du **provider** (Ollama / OpenAI / Azure) — pas de config par modèle |
| Conversations / messages | CRUD + historique |
| Modèles / Fournisseurs | Admin + activation |
| Usage | Tokens, coût estimé, latence |
| Prompt templates | CRUD + insert chat (`{{content}}`) |
| Auth | Dev : `userId = 1` (Keycloak / API Keys = plus tard) |

**Règle streaming :** ajouter un modèle sous Ollama/OpenAI streame automatiquement. Un **nouveau type de provider** (vLLM, SGLang…) exige un adaptateur développeur (DTO + mapper).

## Prérequis

- Java 17, Maven
- PostgreSQL
- Node.js 18+ (si front natif)
- [Ollama](https://ollama.com) (ex. `qwen2.5:0.5b`)
- Optionnel : `OPENAI_API_KEY` pour GPT

## Démarrage rapide (natif)

```bash
# 1. Base
createdb Ai_Gateway_DB   # ou Docker : docker compose up database

# 2. Backend (:8080)
cd backend
cp .env.example .env     # renseigner OPENAI_API_KEY si besoin
./mvnw spring-boot:run

# 3. Frontend (:5173)
cd frontend
npm install
npm run dev
```

Ouvrir http://localhost:5173

## Docker (stack complète)

```bash
# Ollama doit tourner sur l’hôte
docker compose --profile stack up --build
```

- Front : http://localhost:5173  
- API : http://localhost:8080  
- Postgres : port hôte `5433`

Prod-like (seed off, logs quiet) :

```bash
SPRING_PROFILES_ACTIVE=prod docker compose --profile stack up --build
```

## Premier test

1. **Fournisseurs** → activer Ollama (et/ou OpenAI)
2. **Modèles IA** → activer un modèle + défaut
3. **Chat** → envoyer un message (texte progressif en stream)
4. **Consommation** → vérifier tokens / coût
5. **Modèles prompts** → créer un template avec `{{content}}`, l’appliquer dans le chat

## API principale

| Méthode | Route | Description |
|---------|-------|-------------|
| POST | `/api/chat` | Chat synchrone (JSON complet) |
| POST | `/api/chat/stream` | Chat streaming SSE (`event: chunk` → `ChatChunk`) |
| GET/POST | `/api/conversations` | Conversations |
| GET | `/api/conversations/{id}/messages` | Messages |
| GET/POST | `/api/models` | Modèles (`supportsStreaming` hérité du provider) |
| GET/POST | `/api/providers` | Fournisseurs |
| GET | `/api/usage` · `/api/usage/summary` | Usage |
| GET/POST | `/api/prompt-templates` | Templates |

### Exemple stream

```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"content":"Bonjour","model":"qwen2.5:0.5b","stream":true}'
```

## Variables d'environnement

```bash
DB_URL=jdbc:postgresql://localhost:5432/Ai_Gateway_DB
DB_USERNAME=postgres
DB_PASSWORD=postgres
OLLAMA_BASE_URL=http://localhost:11434
OPENAI_API_KEY=sk-...
APP_SEED_ENABLED=true
SPRING_PROFILES_ACTIVE=dev   # ou prod
```

Ne jamais committer `backend/.env`.

## Tests

```bash
cd backend && ./mvnw test
```

## Roadmap (non livré)

- Auth Keycloak / JWT
- API Keys gateway
- Rate limiting, observabilité avancée
- Adaptateurs stream vLLM / SGLang
- Pricing éditable admin
