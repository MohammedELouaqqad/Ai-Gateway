# Monitoring — AI Gateway

Guide complet (Prometheus, Grafana, pages UI, sources de données) :

→ **[monitoring/README.md](monitoring/README.md)**

## Accès rapide

| Service / page | URL | Auth |
|----------------|-----|------|
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | admin / admin |
| Métriques | http://localhost:8080/actuator/prometheus | — |
| Dashboard | http://localhost:5173/dashboard | JWT USER+ |
| Usage | http://localhost:5173/usage | JWT USER+ |
| Observability | http://localhost:5173/observability | JWT ADMIN |
| Santé système | http://localhost:5173/admin/health | JWT ADMIN |

```bash
# Tout (app + monitoring) — scrape Prometheus = gateway:8080
docker compose --profile stack up --build -d
```

- **PostgreSQL** → tokens, coût, journal, KPI métier  
- **Prometheus / Grafana** → JVM, HTTP, targets  
- Volumes : `prometheus_data`, `grafana_data` (rétention Prometheus 15 jours)
