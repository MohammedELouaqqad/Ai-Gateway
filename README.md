# AI Gateway

Passerelle LLM multi-provider (Spring Boot 3 + PostgreSQL + React) avec chat sync/stream, admin modèles/fournisseurs, usage, monitoring et prompt templates.

## Fonctionnalités actuelles

| Domaine | État |
|---------|------|
| Chat sync (`POST /api/chat`) | Ollama + OpenAI (+ Azure compatible) |
| Chat stream SSE (`POST /api/chat/stream`) | Hérité du **provider** (Ollama / OpenAI / Azure) — pas de config par modèle |
| Conversations / messages | CRUD + historique |
| Modèles / Fournisseurs | Admin + activation |
| Dashboard (`/dashboard`) | KPI récents (scopes 24h / 7j / 30j / session), taux d’erreur, latence moy., conso tokens/coût |
| Usage (`/usage`) | Journal paginé (PostgreSQL), filtres, tendances, p50/p95/p99, export CSV |
| Observability (`/observability`) | ADMIN — fiabilité / latence (p50/p95), liens Grafana & Prometheus |
| Rate limiting (`/rate-limiting`) | ADMIN — config + état vitesse (req/min, req/jour) et budget (€) |
| Redis garde-fous | RPM/RPD/budget partagés (opt-in `APP_REDIS_ENABLED`) — Postgres = vérité |
| Santé système (`/admin/health`) | ADMIN — Gateway, Postgres, Prometheus + conso par utilisateur |
| Prompt templates | CRUD + insert chat (`{{content}}`) |
| Auth | Keycloak JWT (Resource Server) — find-or-create user via `sub` |
| Monitoring ops | Prometheus + Grafana (JVM / HTTP) — voir [monitoring/README.md](monitoring/README.md) |

**Règle streaming :** ajouter un modèle sous Ollama/OpenAI streame automatiquement. Un **nouveau type de provider** (vLLM, SGLang…) exige un adaptateur développeur (DTO + mapper).

**Modèle local par défaut (seed) :** `llama3.2:1b` (Ollama). Le seed active Ollama + ce modèle comme défaut.

## Prérequis

- Java 17, Maven
- PostgreSQL
- Node.js 18+ (si front natif)
- [Ollama](https://ollama.com) avec `llama3.2:1b` (`ollama pull llama3.2:1b`)
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

### Mode DEV — live reload (recommandé pour coder)

Volumes + Vite HMR + Spring DevTools (recompile auto) :

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml --profile stack up --build
```

| Zone | Comportement |
|------|----------------|
| `frontend/src`, `style.css` | HMR immédiat dans le navigateur |
| `backend/src` (Java / resources) | Recompile (~2 s) puis restart Spring |
| `pom.xml` / deps npm | Rebuild image concernée |

Sans le fichier `docker-compose.dev.yml`, les images sont **figées** (il faut `--build` à chaque change).

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

**Note prod réelle :** Compose convient pour un serveur unique / démo. Pour une prod HA, préférer Kubernetes ou un IdP managé ; Keycloak tourne déjà sur Postgres (`base keycloak`, même instance que l’app) — en prod utiliser `start` (pas `start-dev`) + HA.
## Premier test

1. **Ollama** → `ollama pull llama3.2:1b` (le seed peut déjà activer le modèle en BDD)
2. **Chat** → envoyer un message (texte progressif en stream)
3. **Dashboard** → vérifier requêtes / taux d’erreur / tokens sur le scope **24h**
4. **Consommation** → journal, filtres, export CSV
5. **Modèles prompts** → créer un template avec `{{content}}`, l’appliquer dans le chat
6. **ADMIN** → Observability + Santé système (`/admin/health`)

## Pages monitoring (UI)

| Page | Rôle | Scope | Refresh | Source données |
|------|------|-------|---------|----------------|
| `/dashboard` | USER+ | 24h (défaut), 7j, 30j, session | auto 15 s | résumé usage (PostgreSQL) + liste providers |
| `/usage` | USER+ | total ou plage (filtres dates) | manuel | PostgreSQL (`usage_logs`) |
| `/observability` | ADMIN | 24h / 7j / 30j / session | auto 15 s | usage (erreur, p50/p95) + Actuator health |
| `/admin/health` | ADMIN | système | auto 15 s | `/actuator/health`, Prometheus, `/api/usage/by-user` |

**Règles KPI :**
- Afficher le **taux d’erreur** uniquement (pas de taux de succès redondant).
- **p50 / p95** sur Dashboard & Observability ; **p50 / p95 / p99** sur Usage (volume plus large).
- Coût / tokens / journal ligne à ligne → **PostgreSQL**, pas Prometheus.
- Grafana / Prometheus → métriques **système** (JVM, HTTP).

## API principale

| Méthode | Route | Description |
|---------|-------|-------------|
| POST | `/api/chat` | Chat synchrone (JSON complet) |
| POST | `/api/chat/stream` | Chat streaming SSE (`event: chunk` → `ChatChunk`) |
| GET/POST | `/api/conversations` | Conversations |
| GET | `/api/conversations/{id}/messages` | Messages |
| GET/POST | `/api/models` | Modèles (`supportsStreaming` hérité du provider) |
| GET/POST | `/api/providers` | Fournisseurs |
| GET | `/api/usage` | Liste complète user (compat ; préférer `/logs`) |
| GET | `/api/usage/summary?from&to` | Agrégats : tokens, coût, requêtes, errorRate, avgLatencyMs |
| GET | `/api/usage/logs?page&size&sort&…` | Journal paginé (filtres model/provider/location/q/from/to) |
| GET | `/api/usage/by-model?…` | Agrégat par modèle |
| GET | `/api/usage/by-user?…` | Agrégat par utilisateur (**ADMIN**) |
| GET | `/api/usage/export/csv?…` | Export CSV (streaming réponse) |
| GET/PUT | `/api/admin/guardrails/config` | Limites rate/budget (**ADMIN**) |
| GET | `/api/admin/guardrails/status` | Consommation vs plafonds (**ADMIN**) |
| GET/POST | `/api/prompt-templates` | Templates |

### Garde-fous LLM (rate limit + budget)

Point de passage : `ChatService` (sync + stream), **avant** tout appel provider.

| Axe | Règle (défaut) | Ollama local | Cloud payant |
|-----|----------------|--------------|--------------|
| Vitesse | 10 req/min/user · 500 req/jour/user | Oui | Oui |
| Montant | 5 €/user/jour · 50 €/global/jour | Non (coût 0) | Oui |

Config : `application-guardrails.yml` (prix EUR / 1K tokens).  
HTTP **429** + champs `reason` / `resetsAt`.  
Métriques : `ai_gateway_tokens_total`, `ai_gateway_cost_eur_total`, `ai_gateway_requests_blocked_total`, `ai_gateway_llm_latency`, `ai_gateway_budget_global_ratio`.  
Test : `scripts/test-guardrails.sh`.

#### Redis (opt-in) — RPM / RPD / budget partagés

- Compose : service `redis:7-alpine`, `noeviction`, password `${REDIS_PASSWORD:-changeme}`  
- Activation stack : `APP_REDIS_ENABLED=true` (déjà dans Compose backend)  
- Local sans Redis : `APP_REDIS_ENABLED=false` → stores **in-memory** (comportement historique)  
- Scripts Lua : `backend/src/main/resources/redis/*.lua`  
- **D1** budget block ≠ consommer RPM/RPD · **D2** RPD = tentative acceptée · **D3** pas d’idempotence requestId en v1  
- Redis down : RPM/RPD `fail-open` configurable (défaut **false** → 429) ; budget → fallback `SUM` Postgres  
- Job réconciliation budget global toutes les ~5 min (orphelins après crash pod)

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

**Persistance :** une seule instance Postgres (`database`), volume Docker `db-data` → `/var/lib/postgresql/data`.
Deux bases sur ce serveur (recommandé — ne pas mélanger les schémas Keycloak et app) :

| Base | Usage |
|------|--------|
| `Ai_Gateway_DB` | Backend Spring |
| `keycloak` | Keycloak (user `keycloak` / `keycloak`) |

1. Ouvrir http://localhost:8180 → login `admin` / `admin`.
2. Créer le realm `Ai-Gateway`, client public `Ai-Gateway-frontend` :
   - **Standard flow** ON, **Direct access grants** OFF (recommandé)
   - Valid redirect URIs : `http://localhost:5173/auth/callback`
   - Valid post logout redirect URIs : `http://localhost:5173/*`
   - Web origins : `http://localhost:5173`
3. Créer les rôles realm `USER`, `DEVELOPER`, `ADMIN` et un utilisateur de test.
4. Backend : `iss` JWT = `http://localhost:8180/realms/Ai-Gateway` ; JWKS récupéré en interne via `http://keycloak:8080/.../certs`.
5. Front : redirect vers **page login Keycloak** (Authorization Code + PKCE) → callback `/auth/callback` → `Authorization: Bearer` sur `/api/**`.
6. Chaque user Keycloak est synchronisé en BDD (`users.keycloak_id` = claim `sub`).
7. Les **rôles** viennent de Keycloak (`realm_access.roles`) et sont mappés en `ROLE_*` côté Spring.

#### Rôles Keycloak (realm roles)

| Rôle | Usage UI / API |
|------|----------------|
| `USER` | Chat, conversations, templates, dashboard, usage, profil |
| `DEVELOPER` | Comme USER + items roadmap visibles dans le menu |
| `ADMIN` | Mutations Models/Providers + `/observability`, `/admin/health`, `/rate-limiting`, `/api/usage/by-user` |

Assignez au moins `USER` (ou `ADMIN`) à chaque compte. Sans `ADMIN`, les boutons d’admin sont masqués côté front et l’API répond **403**.

```bash
# Sans token → 401
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/models

# Token CLI (optionnel — Direct access grants ON) :
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

Couverture prioritaire ajoutée :

| Type | Exemples |
|------|----------|
| Unitaire | `UsageGuardServiceTest` (RPM/RPD/budget/D1), `ModelPricingServiceTest`, `ChatServiceBudgetLifecycleTest` (release/confirm), `InMemoryGuardStoresTest`, `ProviderFailureClassifierTest` |
| Intégration Redis | `RedisGuardStoresIT` (Testcontainers `redis:7-alpine`, skip si Docker absent) |
| Intégration HTTP | `AuthAndChatIntegrationTest` (401/403, chat mock LLM), `ApiSmokeTest`, `SecurityRegressionTest` |

Prérequis IT Spring : PostgreSQL joignable (comme les tests existants). Redis IT : Docker.

## Swagger / OpenAPI

UI : http://localhost:8080/swagger-ui.html  
Spec JSON : http://localhost:8080/v3/api-docs  

Dans Swagger → **Authorize** → coller un JWT Keycloak (`Bearer` automatique).  
Les routes `/api/**` restent protégées ; seule la doc UI est publique.

## Roadmap (non livré)

- API Keys gateway
- Adaptateurs stream vLLM / SGLang
- Pricing éditable admin (persisté BDD ; runtime UI existe déjà)