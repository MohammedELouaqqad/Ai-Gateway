# AI Gateway

Passerelle LLM multi-provider (Spring Boot 3 + PostgreSQL + React), compatible **OpenAI API** (`/v1/chat/completions`, `/v1/models`) pour les apps externes, plus chat interne sync/stream, admin modèles/fournisseurs/utilisateurs, clés API, usage, monitoring et prompt templates.

## Fonctionnalités actuelles

| Domaine | État |
|---------|------|
| **`/v1/chat/completions`, `/v1/models`** | Surface publique **OpenAI-compatible** — auth par clé API (`Authorization: Bearer sk-...`), pas Keycloak. Sync + streaming SSE (format `data: {...}` OpenAI standard) |
| **Clés API** (Settings) | Génération (secret affiché **une seule fois**, expiration optionnelle Jamais/30j/90j/1an), liste, révocation — self-service par utilisateur |
| Chat sync (`POST /api/chat`) | Ollama, OpenAI, Azure OpenAI (auth `api-key`, pas Bearer), vLLM, SGLang |
| Chat stream SSE (`POST /api/chat/stream`) | Hérité du **provider** — pas de config par modèle |
| Conversations / messages | CRUD + historique. Suppression = disparition immédiate côté user ; conservée ~30j en base comme filet de sécurité technique avant purge réelle (`ConversationRetentionJob`), pas une corbeille visible |
| Modèles / Fournisseurs | Admin + activation. `apiKey` configurable directement dans l'UI (création + édition) — jamais réaffichée après coup, juste un badge "clé configurée/absente" (`apiKeyPresent`). Champ vide/non touché = clé existante conservée, pas d'effacement silencieux. `baseUrl` validée à l'écriture (https obligatoire pour OpenAI/Azure, IP metadata cloud bloquée) |
| **Journal d'audit** (`/admin/audit-log`, ADMIN) | Trace les actions admin réussies (users, providers, modèles, guardrails) — qui, quoi, cible, diff avant/après. Jamais de secret dans le diff (`apiKeyChanged` seulement) |
| **Users & Rôles** (Settings, ADMIN) | Liste + activer/désactiver un compte local. Les **rôles** restent exclusivement gérés dans Keycloak (pas ici) |
| Dashboard (`/dashboard`) | KPI récents (scopes 24h / 7j / 30j / session), taux d’erreur, latence moy., conso tokens/coût |
| Usage (`/usage`) | Journal paginé (PostgreSQL), filtres, tendances, p50/p95/p99, export CSV |
| Observability (`/observability`) | ADMIN — fiabilité / latence (p50/p95), liens Grafana & Prometheus |
| Rate limiting (`/rate-limiting`) | ADMIN — config + état vitesse (req/min, req/jour) et budget (€) |
| Redis garde-fous | RPM/RPD/budget partagés (opt-in `APP_REDIS_ENABLED`) — Postgres = vérité |
| Santé système (`/admin/health`) | ADMIN — Gateway, Postgres, Prometheus + conso par utilisateur |
| Prompt templates | CRUD + insert chat (`{{content}}`) |
| Auth | Keycloak JWT (Resource Server) sur `/api/**` — find-or-create user via `sub`, **désactivé par défaut** à la première connexion (approbation ADMIN requise, voir section Keycloak). Clé API sur `/v1/**` (chaîne de sécurité séparée) |
| Monitoring ops | Prometheus + Grafana (JVM / HTTP / usage par user-provider) — voir [monitoring/README.md](monitoring/README.md) |

**Règle streaming :** ajouter un modèle sous un provider existant (Ollama/OpenAI/Azure/vLLM/SGLang) streame automatiquement. Un **nouveau type de provider** au-delà de ces 5 exige un adaptateur développeur (sous-classe de `OpenAiCompatibleProvider` si le protocole est OpenAI-compatible).

**Providers OpenAI-compatibles :** `OpenAIProvider`, `AzureOpenAIProvider`, `VllmProvider`, `SglangProvider` partagent tous `OpenAiCompatibleProvider` — chaque famille expose ses propres hooks (`applyAuth`, `defaultBaseUrl`, `resolveApiKey`, et pour Azure `chatPath`/`queryParams`). vLLM/SGLang parlent le même format OpenAI standard (`/chat/completions`), auth optionnelle (Bearer si `Provider.apiKey` renseigné, sinon aucun header — cas courant d'un serveur auto-hébergé sans clé), défauts `http://localhost:8000/v1` / `http://localhost:30000/v1`. Azure a un chemin d'URL différent (`/openai/deployments/{déploiement}/chat/completions?api-version=...`) — le **nom de déploiement Azure = `Model.name`** dans ce gateway, pas un champ séparé. `api-version` est **fixée en dur à `2024-06-01`** (constante `AzureOpenAIProvider.API_VERSION`) — choix assumé, pas un oubli : Azure retire ses versions selon un calendrier, donc à bumper (ou externaliser en config) si un déploiement en a besoin d'une autre.

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
createdb Ai_Gateway_DB
# ou Docker (nécessite un .env racine rempli — cp .env.example .env puis éditer, voir section Docker plus bas) :
# docker compose up database

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
# 1. Requis avant tout démarrage — docker compose refuse de démarrer sans ce fichier
#    ET sans valeurs non-vides dedans (voir "⚠️ Identifiants" plus bas)
cp .env.example .env
# ... puis éditer .env et renseigner un mot de passe pour chaque variable

# 2. Ollama doit tourner sur l’hôte (Keycloak est dans Compose)
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
| Mailpit    | http://localhost:8025 (catcher SMTP local, emails Keycloak — voir "Inscription publique") |
| Prometheus | http://localhost:9090 |
| Grafana    | http://localhost:3000 (`admin` / `admin`) |
| Postgres   | `localhost:5433` |

Prod-like (seed off, logs quiet) :

```bash
SPRING_PROFILES_ACTIVE=prod APP_SEED_ENABLED=false docker compose --profile stack up --build -d
```

**Note prod réelle :** Compose convient pour un serveur unique / démo. Pour une prod HA, préférer Kubernetes ou un IdP managé ; Keycloak tourne déjà sur Postgres (`base keycloak`, même instance que l’app) — en prod utiliser `start` (pas `start-dev`) + HA.

**⚠️ Identifiants :** `docker compose` refuse de démarrer sans `.env` **ET** sans valeur non-vide pour chaque mot de passe (fail-closed — plus de repli silencieux sur `admin/admin`, et `.env.example` ne fournit délibérément aucune valeur toute prête pour ces champs, pour qu'un simple `cp` sans lecture ne puisse jamais aboutir à un déploiement avec des mots de passe faibles). Copier [.env.example](.env.example) vers `.env` (racine, gitignored, lu automatiquement par `docker compose`), l'éditer, et y mettre de **vraies valeurs** — y compris pour un usage purement local (Postgres, Redis, Keycloak et Grafana ont tous leur port publié par défaut, donc atteignables dès que le firewall de la machine ne bloque pas).

## Premier test

1. **Ollama** → `ollama pull llama3.2:1b` (le seed peut déjà activer le modèle en BDD)
2. **Chat** → envoyer un message (texte progressif en stream)
3. **Dashboard** → vérifier requêtes / taux d’erreur / tokens sur le scope **24h**
4. **Consommation** → journal, filtres, export CSV
5. **Modèles prompts** → créer un template avec `{{content}}`, l’appliquer dans le chat
6. **ADMIN** → Observability + Santé système (`/admin/health`)
7. **Clé API** → Settings → générer une clé, tester `curl .../v1/models -H "Authorization: Bearer sk-..."` (voir section `/v1/*` plus bas)

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
| POST | `/v1/chat/completions` | **OpenAI-compatible**, sync + stream (`stream:true` dans le body) — auth clé API |
| GET | `/v1/models` | Liste OpenAI-compatible des modèles utilisables — auth clé API |
| POST | `/api/chat` | Chat synchrone (JSON complet) |
| POST | `/api/chat/stream` | Chat streaming SSE (`event: chunk` → `ChatChunk`) |
| GET/POST | `/api/conversations` | Conversations |
| GET | `/api/conversations/{id}/messages` | Messages |
| GET/POST | `/api/models` | Modèles (`supportsStreaming` hérité du provider) |
| GET/POST | `/api/providers` | Fournisseurs |
| POST | `/api/api-keys` | Générer une clé (secret renvoyé **une fois**, jamais stocké en clair ; `expiresInDays` optionnel) |
| GET | `/api/api-keys` | Lister ses propres clés (préfixe + statut + expiration, jamais le secret) |
| PATCH | `/api/api-keys/{id}/revoke` | Révoquer sa propre clé |
| GET | `/api/admin/users` | Lister les utilisateurs locaux (**ADMIN**) |
| PATCH | `/api/admin/users/{id}/status` | Activer/désactiver un utilisateur (**ADMIN**, pas son propre compte) |
| GET | `/api/admin/audit-log?page&size&action&from&to` | Journal des actions admin, paginé (**ADMIN**) |
| GET | `/api/usage` | Liste complète user (compat ; préférer `/logs`) |
| GET | `/api/usage/summary?from&to` | Agrégats : tokens, coût, requêtes, errorRate, avgLatencyMs |
| GET | `/api/usage/logs?page&size&sort&…` | Journal paginé (filtres model/provider/location/q/from/to) |
| GET | `/api/usage/by-model?…` | Agrégat par modèle |
| GET | `/api/usage/by-user?…` | Agrégat par utilisateur (**ADMIN**) |
| GET | `/api/usage/export/csv?…` | Export CSV (streaming réponse) |
| GET/PUT | `/api/admin/guardrails/config` | Limites rate/budget (**ADMIN**) |
| GET | `/api/admin/guardrails/status` | Consommation vs plafonds (**ADMIN**) |
| GET/POST | `/api/prompt-templates` | Templates |

### `/v1/*` OpenAI-compatible + clés API

Surface publique pour des apps externes — **stateless** (le client envoie tout l'historique dans `messages`, pas de conversation persistée côté gateway), authentifiée par clé API, jamais par JWT Keycloak. Deux chaînes de sécurité Spring séparées : `/api/**` = JWT, `/v1/**` = clé API.

```bash
# 1. Générer une clé (JWT Keycloak requis pour cet appel self-service)
curl -s -X POST http://localhost:8080/api/api-keys \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -d '{"name":"mon-script"}'
# → { "id":1, "rawKey":"sk-...", ... }  — rawKey n'est JAMAIS renvoyé une deuxième fois

# 2. Utiliser la clé (aucun JWT nécessaire ici)
curl -s http://localhost:8080/v1/models -H "Authorization: Bearer sk-..."

curl -N -s -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer sk-..." -H 'Content-Type: application/json' \
  -d '{"model":"llama3.2:1b","messages":[{"role":"user","content":"Bonjour"}],"stream":true}'
```

Erreurs au format OpenAI (`{"error":{"message","type","code"}}`), pas le format `/api/*` interne. Guardrails (rate limit + budget) et usage logging s'appliquent identiquement aux deux surfaces. Un utilisateur désactivé (`/api/admin/users`) voit ses clés API rejetées aussi, pas seulement son accès humain.

**Limites de requête** : `messages[]` plafonné à 200 entrées, `content` à la même taille max que le chat interne, corps de requête rejeté (413) au-delà de ~20 Mo avant même désérialisation JSON — voir `ContentLimits`/`MaxRequestSizeFilter`.

### Garde-fous LLM (rate limit + budget)

Point de passage : `ChatService` (sync + stream), **avant** tout appel provider.

| Axe | Règle (défaut) | Ollama local | Cloud payant |
|-----|----------------|--------------|--------------|
| Vitesse | 10 req/min/user · 500 req/jour/user | Oui | Oui |
| Montant | 5 €/user/jour · 50 €/global/jour | Non (coût 0) | Oui |

Config : `application-guardrails.yml` (prix EUR / 1K tokens).  
HTTP **429** + champs `reason` / `resetsAt`.  
Métriques : `ai_gateway_tokens_total`, `ai_gateway_cost_eur_total`, `ai_gateway_requests_blocked_total` (tagué `user`), `ai_gateway_llm_latency` (tagué `provider`, p50/p95), `ai_gateway_budget_global_ratio`, `ai_gateway_provider_errors_total` (échecs d'appel LLM par provider/modèle/raison — distinct des blocages guardrails).  
Dashboard Grafana : ligne "LLM Usage & Guardrails" (coût/tokens par user, latence/erreurs par provider, blocages par raison) — voir [monitoring/README.md](monitoring/README.md).  
Alerte Prometheus : `AiGatewayBudgetGlobalHigh` (budget global > 80 %) — voir `monitoring/alerts.yml`.  
Test : `scripts/test-guardrails.sh`.

#### Redis (opt-in) — RPM / RPD / budget partagés

- Compose : service `redis:7-alpine`, `noeviction`, password requis via `.env` racine (`REDIS_PASSWORD`, fail-closed — voir section Docker plus haut)  
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

**Actuator sur port séparé :** `/actuator/*` (dont `/actuator/prometheus`, sans auth applicative) écoute sur `management.server.port` (8081 par défaut), **jamais publié à l'hôte** dans `docker-compose.yml` — seul le réseau Docker interne (Prometheus) l'atteint. Le port API (8080, publié) ne sert aucune route actuator.

### Auth Keycloak

Keycloak est démarré par Compose (profil `stack`) sur le port **8180**.

**Persistance :** une seule instance Postgres (`database`), volume Docker `db-data` → `/var/lib/postgresql/data`.
Deux bases sur ce serveur (recommandé — ne pas mélanger les schémas Keycloak et app) :

| Base | Usage |
|------|--------|
| `Ai_Gateway_DB` | Backend Spring |
| `keycloak` | Keycloak (user `keycloak` / `keycloak`) |

1. Ouvrir http://localhost:8180 → login `admin` / `admin`.
2. Le realm `Ai-Gateway` (client public `Ai-Gateway-frontend`, rôles `USER`/`DEVELOPER`/`ADMIN`, registration + verify email + SMTP mailpit + password policy + brute force déjà réglés) est **importé automatiquement** au 1er boot depuis [`docker/keycloak/import/Ai-Gateway-realm.json`](docker/keycloak/import/Ai-Gateway-realm.json) (`--import-realm`, ne s'exécute que sur un volume Postgres vide — un realm déjà présent n'est jamais écrasé). Rien à créer à la main pour la stack Compose ; il ne reste qu'à créer un utilisateur de test.
   - Sans Docker Compose (Keycloak lancé autrement), reproduire à la main : client public `Ai-Gateway-frontend`, **Standard flow** ON, **Direct access grants** OFF, Valid redirect URIs `http://localhost:5173/auth/callback`, Valid post logout redirect URIs `http://localhost:5173/*`, Web origins `http://localhost:5173`.
   - Après toute modif du realm via la console admin qu'on veut garder : ré-exporter avec `Realm settings → Action → Partial export` (rôles + clients, **pas** les users) et remplacer le fichier ci-dessus.
3. Créer les rôles realm `USER`, `DEVELOPER`, `ADMIN` et un utilisateur de test (rôles déjà créés par l'import — reste l'utilisateur de test).
4. Backend : `iss` JWT = `http://localhost:8180/realms/Ai-Gateway` ; JWKS récupéré en interne via `http://keycloak:8080/.../certs`.
5. Front : redirect vers **page login Keycloak** (Authorization Code + PKCE) → callback `/auth/callback` → `Authorization: Bearer` sur `/api/**`.
6. Chaque user Keycloak est synchronisé en BDD (`users.keycloak_id` = claim `sub`).
7. Les **rôles** viennent de Keycloak (`realm_access.roles`) et sont mappés en `ROLE_*` côté Spring.

#### Rôles Keycloak (realm roles)

| Rôle | Usage UI / API |
|------|----------------|
| `USER` | Chat, conversations, templates, dashboard, usage, profil |
| `DEVELOPER` | Comme USER + items roadmap visibles dans le menu |
| `ADMIN` | Mutations Models/Providers + `/observability`, `/admin/health`, `/admin/audit-log`, `/rate-limiting`, `/api/usage/by-user`, `/api/admin/users` |

Assignez au moins `USER` (ou `ADMIN`) à chaque compte. Sans `ADMIN`, les boutons d’admin sont masqués côté front et l’API répond **403**.

**Note `/api/admin/users` :** gère uniquement l'activation/désactivation du compte local (table `users`) — **pas** les rôles, qui restent 100 % Keycloak (aucun champ rôle en base côté app). Un compte désactivé est rejeté au prochain appel (`/api/**` **et** `/v1/**` via ses clés API), et ne se réactive plus jamais tout seul à la reconnexion (bug corrigé).

#### Inscription publique (self-registration)

Le login est **entièrement délégué** à la page hébergée par Keycloak (Authorization Code + PKCE, pas de formulaire côté React) — activer l'inscription publique est donc un simple toggle dans la console d'admin Keycloak, **pas du code**. Mais côté app, tout nouveau compte vu pour la première fois (auto-inscrit ou créé à la main par un admin dans Keycloak — l'app ne peut pas distinguer les deux) démarre **`enabled=false`** : un ADMIN doit l'activer explicitement dans **Users & Rôles** avant tout accès. Sans ça, une inscription publique ouverte laisserait n'importe qui avec un email consommer du budget LLM sans contrôle.

Checklist realm Keycloak avant d'activer `Realm Settings → Login → User registration` — **déjà appliquée** dans le realm importé ([`docker/keycloak/import/Ai-Gateway-realm.json`](docker/keycloak/import/Ai-Gateway-realm.json)) :

| Réglage | État | Pourquoi |
|---------|------|----------|
| **User registration** ON | ✅ | Affiche le lien "Register" sur la page de login |
| **Verify email** ❌ **OFF** (volontairement) | ✅ | Voir "Vérification d'email" ci-dessous — décision produit : vérification manuelle par un ADMIN dans Keycloak, pas d'email automatique |
| **SMTP** configuré (`Realm Settings → Email`) | ✅ | Sert uniquement à "mot de passe oublié" maintenant (plus à la vérification d'email). En local : **Mailpit** (service Compose, catcher SMTP, jamais de vraie délivrance) — `host=mailpit`, `port=1025`, pas d'auth/TLS ; UI web sur http://localhost:8025. En prod, pointer vers un vrai SMTP. |
| **Default roles** = `USER` uniquement | ✅ | Un défaut mal réglé sur `ADMIN` serait catastrophique pour un compte auto-inscrit. Sans `USER` dans les rôles par défaut, un compte auto-inscrit — même activé par un ADMIN via `/api/admin/users` — n'a **aucun rôle** dans son JWT (les rôles viennent exclusivement de Keycloak, `UserAdminController` ne fait qu'enable/disable) : tous les endpoints `hasRole(...)` restent 403 sans recours possible depuis l'app. Trouvé et corrigé pendant la mise en place de ce checklist. |
| **Password policy** (`Authentication → Policies`) | ✅ | `length(10) and notUsername and notEmail and upperCase(1) and lowerCase(1) and digits(1)` — le mot de passe est choisi par un utilisateur non vérifié au moment de l'inscription |
| **Brute force detection** ON | ✅ | Standard, protège aussi le formulaire d'inscription |
| **Reset password** ON | ✅ | "Mot de passe oublié" — utilise SMTP/Mailpit |

#### Vérification d'email : manuelle, pas automatique

Décision produit (pas la valeur par défaut d'un starter Keycloak) : **`Verify email` est désactivé sur le realm**. Un nouveau compte auto-inscrit reçoit son JWT immédiatement, avec `email_verified: false` dedans — **aucun email n'est envoyé**. C'est `UserService.requireCurrentUser()` qui bloque l'accès :

```java
if (Boolean.FALSE.equals(jwt.getClaimAsBoolean("email_verified"))) {
    throw new UnauthorizedException("Email non vérifié");
}
```

Ce check existait déjà comme "défense en profondeur" (au cas où le realm serait mal configuré) — il est maintenant le **mécanisme principal**, pas un filet de sécurité. Pour débloquer un utilisateur : un **ADMIN ouvre la console Keycloak** (`Users → <utilisateur> → Details`) et coche **Email Verified** à la main. Au prochain login, le nouveau JWT porte `email_verified: true` et ce check passe.

Ce reste **indépendant** de l'approbation applicative (`users.enabled`, voir ci-dessus) — un ADMIN doit faire les deux : cocher Email Verified dans Keycloak, **et** cliquer Activer dans Settings → Users & Rôles. Aucune des deux étapes ne remplace l'autre.

**Changer son mot de passe / nom / email :** entièrement délégué à l'**Account Console** Keycloak (self-service, même session SSO déjà active — pas de re-login) — lien dans **Settings** (`Auth Keycloak → Gérer mon compte`). Aucun formulaire de ce type côté app, volontairement : la sécurité (hash, politique de mot de passe, historique) reste 100 % du ressort de l'IdP.

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
| Unitaire | `UsageGuardServiceTest` (RPM/RPD/budget/D1), `ModelPricingServiceTest`, `ChatServiceBudgetLifecycleTest` (release/confirm), `InMemoryGuardStoresTest`, `ProviderFailureClassifierTest`, `ApiKeyServiceTest` (hash/révocation/user désactivé/expiration), `UserServiceTest` (enforcement compte désactivé, auto-toggle), `ConversationServiceTest` (suppression/purge), `ProviderBaseUrlValidatorTest` (SSRF), `AuditActorTest`/`AuditServiceTest`/`AuditDiffsTest` |
| Provider HTTP réel | `OpenAiCompatibleProviderTest` (Bearer vs `api-key` vs optionnel, chemin Azure + `api-version`, SSE), `AzureOpenAIProviderTest`, `VllmProviderTest`, `SglangProviderTest` |
| Intégration Redis | `RedisGuardStoresIT` (Testcontainers `redis:7-alpine`, skip si Docker absent) |
| Intégration HTTP | `AuthAndChatIntegrationTest` (401/403, chat mock LLM), `ApiSmokeTest`, `SecurityRegressionTest`, `ApiKeyAuthIntegrationTest`, `V1ChatApiTest` (`/v1/*` sync+stream+guardrails), `UserAdminControllerIntegrationTest`, `AuditLogIntegrationTest`, `ManagementPortSecurityTest` (actuator sur port séparé, vraie requête HTTP) |

Prérequis IT Spring : PostgreSQL joignable (comme les tests existants). Redis IT : Docker.

## Swagger / OpenAPI

UI : http://localhost:8080/swagger-ui.html  
Spec JSON : http://localhost:8080/v3/api-docs  

Dans Swagger → **Authorize** → coller un JWT Keycloak (`Bearer` automatique) pour `/api/**`.  
Les routes `/v1/**` (documentées aussi, mêmes contrôleurs Spring) s'authentifient avec une **clé API** (`sk-...`), pas un JWT — même champ `Bearer`, valeur différente.  
Seule la doc UI est publique ; les routes elles-mêmes restent protégées.

## Roadmap (non livré)

- Gestion des rôles depuis l'app (nécessiterait l'API Admin Keycloak — pas intégrée ; `/api/admin/users` gère seulement activer/désactiver un compte local)
- Alertmanager (les règles Prometheus existent et s'évaluent, mais rien n'est notifié activement — Slack/email à brancher)
- Pricing éditable admin (persisté BDD ; runtime UI existe déjà)