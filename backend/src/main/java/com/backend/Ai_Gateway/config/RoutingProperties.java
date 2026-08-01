package com.backend.Ai_Gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@ConfigurationProperties(prefix = "app.routing")
public class RoutingProperties {

    public enum Mode {
        SHADOW,
        /** Sticky percent of conversations get live winner; rest stay on default. */
        CANARY,
        ACTIVE
    }

    public enum Profile {
        ECONOMY,
        QUALITY,
        SPEED,
        BALANCED
    }

    private boolean enabled = true;
    private Mode mode = Mode.SHADOW;
    /** 0–100, used only when mode=CANARY. Sticky by conversationId (fallback userId). */
    private int canaryPercent = 10;
    private Profile profile = Profile.BALANCED;
    private int maxRetries = 1;
    private long retryBackoffMs = 200;
    private int defaultContextChars = 120_000;
    private Map<String, Integer> contextWindows = new HashMap<>();
    private Map<String, ModelScore> modelScores = new HashMap<>();
    private Map<String, ProfileWeights> profiles = defaultProfiles();
    private Judge judge = new Judge();
    private Privacy privacy = new Privacy();
    /**
     * Absolute € ceiling for costScore on a typical request estimate.
     * 0 = derive from max estimated cost across the full model catalog (not only survivors),
     * so disabling one expensive model does not reshuffle others' cost scores.
     */
    private double costScoreCeilingEur = 0;
    /**
     * Quality feedback contract: measured thumbs/judge never auto-write modelScores.quality.
     * Humans adjust YAML / runtime scores after reviewing evidence.
     */
    private String qualityFeedbackLoop = "HUMAN_IN_THE_LOOP";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode != null ? mode : Mode.SHADOW;
    }

    public void setMode(String mode) {
        if (mode == null || mode.isBlank()) {
            this.mode = Mode.SHADOW;
            return;
        }
        this.mode = Mode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
    }

    public int getCanaryPercent() {
        return canaryPercent;
    }

    public void setCanaryPercent(int canaryPercent) {
        this.canaryPercent = Math.max(0, Math.min(100, canaryPercent));
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile != null ? profile : Profile.BALANCED;
    }

    public void setProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            this.profile = Profile.BALANCED;
            return;
        }
        this.profile = Profile.valueOf(profile.trim().toUpperCase(Locale.ROOT));
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, maxRetries);
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = Math.max(0, retryBackoffMs);
    }

    public int getDefaultContextChars() {
        return defaultContextChars;
    }

    public void setDefaultContextChars(int defaultContextChars) {
        this.defaultContextChars = Math.max(1, defaultContextChars);
    }

    public Map<String, Integer> getContextWindows() {
        return contextWindows;
    }

    public void setContextWindows(Map<String, Integer> contextWindows) {
        this.contextWindows = contextWindows != null ? contextWindows : new HashMap<>();
    }

    public Map<String, ModelScore> getModelScores() {
        return modelScores;
    }

    public void setModelScores(Map<String, ModelScore> modelScores) {
        this.modelScores = modelScores != null ? modelScores : new HashMap<>();
    }

    public Map<String, ProfileWeights> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<String, ProfileWeights> profiles) {
        this.profiles = profiles != null && !profiles.isEmpty() ? profiles : defaultProfiles();
    }

    public int contextCharsFor(String modelName) {
        if (modelName != null) {
            Integer configured = contextWindows.get(modelName.trim());
            if (configured != null && configured > 0) {
                return configured;
            }
        }
        return defaultContextChars;
    }

    public ModelScore scoreFor(String modelName) {
        if (modelName == null) {
            return ModelScore.defaults();
        }
        ModelScore score = modelScores.get(modelName.trim());
        return score != null ? score : ModelScore.defaults();
    }

    public ProfileWeights weightsFor(Profile profile) {
        ProfileWeights w = profiles.get(profile.name());
        return w != null ? w : defaultProfiles().get(Profile.BALANCED.name());
    }

    public Judge getJudge() {
        return judge;
    }

    public void setJudge(Judge judge) {
        this.judge = judge != null ? judge : new Judge();
    }

    public Privacy getPrivacy() {
        return privacy;
    }

    public void setPrivacy(Privacy privacy) {
        this.privacy = privacy != null ? privacy : new Privacy();
    }

    public double getCostScoreCeilingEur() {
        return costScoreCeilingEur;
    }

    public void setCostScoreCeilingEur(double costScoreCeilingEur) {
        this.costScoreCeilingEur = Math.max(0, costScoreCeilingEur);
    }

    public String getQualityFeedbackLoop() {
        return qualityFeedbackLoop;
    }

    public void setQualityFeedbackLoop(String qualityFeedbackLoop) {
        this.qualityFeedbackLoop = qualityFeedbackLoop != null && !qualityFeedbackLoop.isBlank()
                ? qualityFeedbackLoop.trim()
                : "HUMAN_IN_THE_LOOP";
    }

    private static Map<String, ProfileWeights> defaultProfiles() {
        Map<String, ProfileWeights> map = new LinkedHashMap<>();
        map.put("ECONOMY", new ProfileWeights(0.70, 0.15, 0.15));
        map.put("QUALITY", new ProfileWeights(0.10, 0.70, 0.20));
        map.put("SPEED", new ProfileWeights(0.15, 0.15, 0.70));
        map.put("BALANCED", new ProfileWeights(0.34, 0.33, 0.33));
        return map;
    }

    public static class ModelScore {
        private double quality = 50;
        private double speed = 50;

        public static ModelScore defaults() {
            return new ModelScore();
        }

        public double getQuality() {
            return quality;
        }

        public void setQuality(double quality) {
            this.quality = quality;
        }

        public double getSpeed() {
            return speed;
        }

        public void setSpeed(double speed) {
            this.speed = speed;
        }
    }

    public static class ProfileWeights {
        private double cost = 0.34;
        private double quality = 0.33;
        private double speed = 0.33;

        public ProfileWeights() {}

        public ProfileWeights(double cost, double quality, double speed) {
            this.cost = cost;
            this.quality = quality;
            this.speed = speed;
        }

        public double getCost() {
            return cost;
        }

        public void setCost(double cost) {
            this.cost = cost;
        }

        public double getQuality() {
            return quality;
        }

        public void setQuality(double quality) {
            this.quality = quality;
        }

        public double getSpeed() {
            return speed;
        }

        public void setSpeed(double speed) {
            this.speed = speed;
        }
    }

    /** Phase 2 disagreement judge — proposed model once vs stored applied answer. */
    public static class Judge {
        private boolean enabled = true;
        /** Max disagreements to process per /judge/run call. */
        private int batchSize = 5;
        /**
         * When true, ask a cheap compare model for PROPOSED|APPLIED|TIE.
         * When false, use a deterministic heuristic (no extra LLM).
         */
        private boolean llmCompare = false;
        /** Optional compare model name; null → default active model. */
        private String compareModel;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = Math.max(1, Math.min(50, batchSize));
        }

        public boolean isLlmCompare() {
            return llmCompare;
        }

        public void setLlmCompare(boolean llmCompare) {
            this.llmCompare = llmCompare;
        }

        public String getCompareModel() {
            return compareModel;
        }

        public void setCompareModel(String compareModel) {
            this.compareModel = compareModel;
        }
    }

    /**
     * Prompt/response snapshots are user content (GDPR). Store only when needed for judge,
     * then purge after judgment or retention window.
     */
    public static class Privacy {
        /** Keep snapshots this many days (unjudged or residual). */
        private int snapshotRetentionDays = 14;
        /** Clear prompt/response text immediately after a successful judge run. */
        private boolean clearSnapshotsAfterJudge = true;

        public int getSnapshotRetentionDays() {
            return snapshotRetentionDays;
        }

        public void setSnapshotRetentionDays(int snapshotRetentionDays) {
            this.snapshotRetentionDays = Math.max(1, Math.min(365, snapshotRetentionDays));
        }

        public boolean isClearSnapshotsAfterJudge() {
            return clearSnapshotsAfterJudge;
        }

        public void setClearSnapshotsAfterJudge(boolean clearSnapshotsAfterJudge) {
            this.clearSnapshotsAfterJudge = clearSnapshotsAfterJudge;
        }
    }
}
