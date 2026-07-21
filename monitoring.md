# Monitoring — AI Gateway

Guide complet : **[monitoring/README.md](monitoring/README.md)**

## Accès rapide

| Service    | URL                       | Auth              |
|------------|---------------------------|-------------------|
| Prometheus | http://localhost:9090     | —                 |
| Grafana    | http://localhost:3000     | admin / admin     |
| Métriques  | http://localhost:8080/actuator/prometheus | —      |

```bash
# Tout (app + monitoring) — scrape Prometheus = gateway:8080
docker compose --profile stack up --build -d
```

Volumes persistants : `prometheus_data`, `grafana_data` (rétention Prometheus 15 jours).
