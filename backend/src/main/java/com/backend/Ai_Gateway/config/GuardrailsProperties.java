package com.backend.Ai_Gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.guardrails")
public class GuardrailsProperties {

    private boolean enabled = true;
    private RateLimit rateLimit = new RateLimit();
    private Budget budget = new Budget();
    private Estimate estimate = new Estimate();
    private Map<String, ModelPrice> pricing = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Budget getBudget() {
        return budget;
    }

    public void setBudget(Budget budget) {
        this.budget = budget;
    }

    public Estimate getEstimate() {
        return estimate;
    }

    public void setEstimate(Estimate estimate) {
        this.estimate = estimate;
    }

    public Map<String, ModelPrice> getPricing() {
        return pricing;
    }

    public void setPricing(Map<String, ModelPrice> pricing) {
        this.pricing = pricing != null ? pricing : new HashMap<>();
    }

    public static class RateLimit {
        private int requestsPerMinute = 10;
        private int requestsPerDay = 500;

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }

        public int getRequestsPerDay() {
            return requestsPerDay;
        }

        public void setRequestsPerDay(int requestsPerDay) {
            this.requestsPerDay = requestsPerDay;
        }
    }

    public static class Budget {
        private double globalPerDayEur = 50;
        private double userPerDayEur = 5;

        public double getGlobalPerDayEur() {
            return globalPerDayEur;
        }

        public void setGlobalPerDayEur(double globalPerDayEur) {
            this.globalPerDayEur = globalPerDayEur;
        }

        public double getUserPerDayEur() {
            return userPerDayEur;
        }

        public void setUserPerDayEur(double userPerDayEur) {
            this.userPerDayEur = userPerDayEur;
        }
    }

    public static class Estimate {
        private int assumedCompletionTokens = 500;
        private int charsPerToken = 4;
        /** Flat per-image token estimate added to the prompt-token guess when a message
         *  carries an attachment — real vision pricing tiles/varies per provider, this is a
         *  deliberately rough, conservative stand-in (roughly OpenAI's low-detail image cost)
         *  so an image request doesn't get budgeted as if it were free. */
        private int imageFlatTokens = 1500;

        public int getAssumedCompletionTokens() {
            return assumedCompletionTokens;
        }

        public void setAssumedCompletionTokens(int assumedCompletionTokens) {
            this.assumedCompletionTokens = assumedCompletionTokens;
        }

        public int getCharsPerToken() {
            return charsPerToken;
        }

        public void setCharsPerToken(int charsPerToken) {
            this.charsPerToken = charsPerToken;
        }

        public int getImageFlatTokens() {
            return imageFlatTokens;
        }

        public void setImageFlatTokens(int imageFlatTokens) {
            this.imageFlatTokens = imageFlatTokens;
        }
    }

    public static class ModelPrice {
        private double inputPer1kEur;
        private double outputPer1kEur;

        public double getInputPer1kEur() {
            return inputPer1kEur;
        }

        public void setInputPer1kEur(double inputPer1kEur) {
            this.inputPer1kEur = inputPer1kEur;
        }

        public double getOutputPer1kEur() {
            return outputPer1kEur;
        }

        public void setOutputPer1kEur(double outputPer1kEur) {
            this.outputPer1kEur = outputPer1kEur;
        }
    }
}
