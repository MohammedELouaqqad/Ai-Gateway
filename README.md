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
| Auth | Keycloak JWT (Resource Server) — find-or-create user via `sub` |

**Règle streaming :** ajouter un modèle sous Ollama/OpenAI streame automatiquement. Un **nouveau type de provider** (vLLM, SGLang…) exige un adaptateur développeur (DTO + mapper).

## Prérequis

- Java 17, Maven
- PostgreSQL
- Node.js 18+ (si front natif)
- [Ollama](https://ollama.com) (ex. `llama3.2:1b`)
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
# Ollama doit tourner sur l’hôte (Keycloak est dans Compose)
docker compose --profile stack up --build -d
```

| Service    | URL |
|------------|-----|
| Frontend   | http://localhost:5173 |
| API        | http://localhost:8080 |
| Keycloak   | http://localhost:8180 (`admin` / `admin`) |
| Prometheus | http://localhost:9090 |
| Grafana    | http://localhost:3000 (`admin` / `admin`) |
| Postgres   | `localhost:5433` |

Prod-like (seed off, logs quiet) :

```bash
SPRING_PROFILES_ACTIVE=prod APP_SEED_ENABLED=false docker compose --profile stack up --build -d
```

**Note prod réelle :** Compose convient pour un serveur unique / démo. Pour une prod HA, préférer Kubernetes ou un IdP managé ; Keycloak devrait alors tourner en mode `start` (pas `start-dev`) avec sa propre base Postgres.
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
  -d '{"content":"Bonjour","model":"llama3.2:1b","stream":true}'
```

## Variables d'environnement

```bash
DB_URL=jdbc:postgresql://localhost:5432/Ai_Gateway_DB
DB_USERNAME=postgres
DB_PASSWORD=postgres
OLLAMA_BASE_URL=http://localhost:11434
OPENAI_API_KEY=sk-...
KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/Ai-Gateway
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
APP_SEED_ENABLED=true
SPRING_PROFILES_ACTIVE=dev   # ou prod
```

Ne jamais committer `backend/.env`.

### Monitoring (Prometheus + Grafana)

```bash
docker compose up -d prometheus grafana
```

- Prometheus : http://localhost:9090  
- Grafana : http://localhost:3000 (`admin` / `admin`)  
- Détails : [monitoring/README.md](monitoring/README.md)

### Auth Keycloak

Keycloak est démarré par Compose (profil `stack`) sur le port **8180**.

1. Ouvrir http://localhost:8180 → login `admin` / `admin`.
2. Créer le realm `Ai-Gateway`, client public `Ai-Gateway-frontend` (Direct Access Grants pour le login dev).
3. Créer les rôles realm `USER`, `DEVELOPER`, `ADMIN` et un utilisateur de test.
4. Backend : `iss` JWT = `http://localhost:8180/realms/Ai-Gateway` ; JWKS récupéré en interne via `http://keycloak:8080/.../certs`.
5. Front : `/login` → password grant (proxy Vite `/realms`) → `Authorization: Bearer` sur `/api/**`.
6. Chaque user Keycloak est synchronisé en BDD (`users.keycloak_id` = claim `sub`).
7. Les **rôles** viennent de Keycloak (`realm_access.roles`) et sont mappés en `ROLE_*` côté Spring.

#### Rôles Keycloak (realm roles)

| Rôle | Usage UI / API |
|------|----------------|
| `USER` | Chat, conversations, templates, usage, profil ; lecture Models/Providers |
| `DEVELOPER` | Comme USER + items roadmap visibles dans le menu |
| `ADMIN` | Mutations Models/Providers (`POST`/`PUT`/`PATCH`/`DELETE`) + tout le reste |

Assignez au moins `USER` (ou `ADMIN`) à chaque compte. Sans `ADMIN`, les boutons d’admin sont masqués côté front et l’API répond **403**.

```bash
# Sans token → 401
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/models

# Avec token
TOKEN=$(curl -s -X POST 'http://localhost:8180/realms/Ai-Gateway/protocol/openid-connect/token' \
  -d 'grant_type=password&client_id=Ai-Gateway-frontend&username=user1&password=...' \
  | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/profile

# Mutation admin (ROLE_ADMIN requis) → 403 si USER
curl -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/providers \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"X","type":"OLLAMA","enabled":false}'
```

## Tests

```bash
cd backend && ./mvnw test
```

## Roadmap (non livré)

- API Keys gateway
- Rate limiting (quotas / budget)
- Adaptateurs stream vLLM / SGLang
- Pricing éditable admin
- OIDC Authorization Code + PKCE (remplacer password grant)

Observabilité hub (liens Grafana/Prometheus + santé Actuator) : page `/observability` livrée.