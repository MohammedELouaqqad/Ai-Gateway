# Monitoring — AI Gateway (Prometheus + Grafana + UI)

Deux couches complémentaires :

| Couche | Où | Contenu |
|--------|-----|---------|
| **Ops** | Prometheus + Grafana | JVM, CPU, HTTP (`http_server_requests_*`), targets scrape |
| **Produit** | UI Gateway | Coût, tokens, journal d’appels, taux d’erreur métier → **PostgreSQL** (`usage_logs`) |

## URLs

| Service / page | URL | Accès |
|----------------|-----|-------|
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | `admin` / `admin` |
| Actuator metrics | http://localhost:8080/actuator/prometheus | public (scrape) |
| Actuator health | http://localhost:8080/actuator/health | public (détails selon auth) |
| Dashboard app | http://localhost:5173/dashboard | USER+ |
| Usage | http://localhost:5173/usage | USER+ |
| Observability | http://localhost:5173/observability | ADMIN |
| Santé système | http://localhost:5173/admin/health | ADMIN |

**Changer le mot de passe Grafana** en prod (`GF_SECURITY_ADMIN_PASSWORD`).

## Démarrage

### Stack Docker (recommandé — scrape `gateway:8080`)

```bash
# Postgres + Keycloak + backend + frontend + Prometheus + Grafana
docker compose --profile stack up --build -d
```

Le service `backend` expose l’alias DNS **`gateway`** → scrape unique `gateway:8080` (job `ai-gateway`).

Prometheus / Grafana n’ont pas de `profiles` : ils démarrent avec Compose dès que tu lances la stack (ou `docker compose up -d prometheus grafana` si la stack tourne déjà).

> **Note :** le scrape du Gateway exige le backend dans Docker (`gateway:8080`). Un backend lancé seulement en natif n’est plus scrapé (job `host.docker.internal` retiré volontairement).

## Vérifications

```bash
# Santé Prometheus
curl -s http://localhost:9090/-/healthy

# Santé Grafana
curl -s http://localhost:3000/api/health

# Métriques Gateway
curl -s http://localhost:8080/actuator/prometheus | head

# Targets Prometheus (UI)
open http://localhost:9090/targets
```

Dans Grafana : dossier **AI Gateway** → dashboard **AI Gateway — JVM & HTTP** (le titre date d'avant la ligne "LLM Usage & Guardrails" ajoutée depuis — pas renommé pour ne pas casser des liens/favoris existants).

## Architecture

```
┌────────────┐     scrape /actuator/prometheus      ┌────────────┐
│  Gateway   │ ◄────────────────────────────────────│ Prometheus │
│  :8080     │                                      │   :9090    │
└────────────┘                                      └─────┬──────┘
                                                          │ datasource
                                                          ▼
                                                    ┌────────────┐
                                                    │  Grafana   │
                                                    │   :3000    │
                                                    └────────────┘
```

## Persistance

Volumes Docker nommés (survivent à `docker compose down`) :

| Volume            | Contenu                          |
|-------------------|----------------------------------|
| `prometheus_data` | TSDB Prometheus                  |
| `grafana_data`    | SQLite Grafana, préférences UI   |

Rétention Prometheus : **15 jours** (`--storage.tsdb.retention.time=15d`).

Pour tout effacer :

```bash
docker compose down
docker volume rm ai-gateway_prometheus_data ai-gateway_grafana_data
# (préfixe = nom du projet Compose ; vérifier avec : docker volume ls)
```

## Fichiers

```
monitoring/
  prometheus.yml
  alerts.yml                         ← règles d'alerte (chargées par Prometheus, rule_files)
  README.md                          ← ce fichier
  grafana/
    dashboards/
      ai-gateway.json                ← dashboard provisionné
    provisioning/
      datasources/datasource.yml     ← Prometheus @ http://prometheus:9090
      dashboards/dashboard.yml
```

## Métriques disponibles

Actuator + Micrometer exposent automatiquement :

- **JVM** : heap, threads, GC (`jvm_*`)
- **Process / système** : CPU (`process_cpu_usage`, `system_cpu_usage`)
- **HTTP** : taux et latence par URI/status (`http_server_requests_seconds_*`)

Percentiles HTTP (config `application.properties`, sans code Java) :

```properties
management.metrics.distribution.percentiles.http.server.requests=0.5,0.95,0.99
```

La latence des endpoints `/api/chat` et `/api/chat/stream` sert de **proxy** de la latence provider (temps total Gateway → LLM → réponse). Sur un modèle local (ex. `llama3.2:1b`), une moyenne de plusieurs dizaines de secondes peut être normale.

### Métriques métier (Micrometer custom) — ligne dashboard "LLM Usage & Guardrails"

Contrairement aux métriques JVM/HTTP ci-dessus, celles-ci sont écrites explicitement dans le code (`UsageGuardService`, `RedisRateLimitStore`, `RedisBudgetStore`) et n'existent dans Prometheus qu'**après au moins un vrai appel de chat** (un `Counter` Micrometer ne s'enregistre qu'au premier `.increment()`).

| Métrique | Type | Tags | Panel dashboard |
|----------|------|------|------------------|
| `ai_gateway_tokens_total` | Counter | `provider`, `model`, `user` | Tokens by user (top 10) |
| `ai_gateway_cost_eur_total` | Counter | `provider`, `model`, `user` | Cost by user (top 10) |
| `ai_gateway_llm_latency` | Timer (p50/p95) | `provider`, `location` | LLM latency by provider |
| `ai_gateway_provider_errors_total` | Counter | `provider`, `model`, `reason` | LLM call errors by provider |
| `ai_gateway_requests_blocked_total` | Counter | `reason`, `user` | Blocked requests by reason & user |
| `ai_gateway_budget_global_ratio` | Gauge | — | (alerting uniquement, pas de panel dédié) |

`ai_gateway_provider_errors_total` ≠ `ai_gateway_requests_blocked_total` : le premier compte les échecs d'appel LLM lui-même (timeout, 5xx provider…), le second compte les requêtes rejetées par les guardrails (rate limit / budget) **avant** même d'appeler le provider.

### Alertes Prometheus

Règles dans `monitoring/alerts.yml`, chargées via `rule_files` (`prometheus.yml`) — évaluées **par Prometheus lui-même**, pas par l'app ni par Grafana. Vérifier l'état : `http://localhost:9090/alerts` ou `GET /api/v1/rules`.

| Alerte | Condition |
|--------|-----------|
| `AiGatewayBudgetGlobalHigh` | `ai_gateway_budget_global_ratio > 0.8` pendant 2 min |

⚠️ **Aucun Alertmanager configuré** — une alerte qui passe `firing` ne notifie personne activement (pas de Slack/email), elle est seulement visible sur `/alerts`. À ajouter si des notifications actives sont nécessaires.

### Tokens / coût / journal

Stockés dans **PostgreSQL** via `UsageService` après chaque chat. APIs :

- `GET /api/usage/summary`
- `GET /api/usage/logs` (paginé)
- `GET /api/usage/by-model`
- `GET /api/usage/by-user` (ADMIN)
- `GET /api/usage/export/csv`

Prometheus **ne remplace pas** ces données (pas de coût par appel #N).

## Sécurité scrape

`/actuator/health` et `/actuator/prometheus` sont en `permitAll` pour laisser Prometheus scraper sans JWT.  
Ne pas exposer ces ports publiquement sans réseau privé / auth reverse-proxy en production.

## Dépannage

| Symptôme | Cause probable | Action |
|----------|----------------|--------|
| Target `gateway:8080` DOWN | Profile `stack` / backend arrêté | `docker compose --profile stack up -d backend` |
| 401 sur `/actuator/prometheus` | Ancienne image backend | Rebuild : `docker compose --profile stack build backend` |
| Dashboard vide | Pas encore de scrape | Attendre 15–30 s, vérifier `/targets` |
| Grafana healthcheck fail | Image sans `wget` | Voir logs `docker compose logs grafana` |
| Métriques doublées | Ancien job `ai-gateway-host` encore chargé | Recharger la config : `docker compose restart prometheus` |
