package com.backend.Ai_Gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis garde-fous (RPM / RPD / budget).
 *
 * <p>Décisions documentées :
 * <ul>
 *   <li>D1 — Un blocage budget NE consomme PAS RPM/RPD : check/peek puis réserve budget,
 *       puis commit RPM+RPD.</li>
 *   <li>D2 — RPD compte la tentative acceptée (pas seulement SUCCESS LLM).</li>
 *   <li>D3 — Idempotence requestId : limite connue v1 (pas implémentée).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.redis")
public class RedisGuardProperties {

    /** Active Spring Data Redis + stores redis (sinon memory). */
    private boolean enabled = false;
    private String host = "localhost";
    private int port = 6379;
    private String password = "";
    private RateLimit rateLimit = new RateLimit();
    private Budget budget = new Budget();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password != null ? password : "";
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit != null ? rateLimit : new RateLimit();
    }

    public Budget getBudget() {
        return budget;
    }

    public void setBudget(Budget budget) {
        this.budget = budget != null ? budget : new Budget();
    }

    public static class RateLimit {
        /** false = Redis down → 429 ; true = laisser passer + métrique */
        private boolean failOpen = false;

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }
    }

    public static class Budget {
        /** Job de réconciliation orphelins (minutes). */
        private long reconcileIntervalMinutes = 5;

        public long getReconcileIntervalMinutes() {
            return reconcileIntervalMinutes;
        }

        public void setReconcileIntervalMinutes(long reconcileIntervalMinutes) {
            this.reconcileIntervalMinutes = Math.max(1, reconcileIntervalMinutes);
        }
    }
}
