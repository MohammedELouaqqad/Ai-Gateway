package com.backend.Ai_Gateway.services.dto;

/**
 * Empirical per-model quality from thumbs.
 * {@code qualityStatus} is {@code MEASURED} only when volume &gt; 0; otherwise {@code NOT_MEASURED}.
 */
public record ModelQualityStats(
        String modelName,
        long ups,
        long downs,
        long volume,
        Double upRatio,
        String qualityStatus
) {
    public static final String MEASURED = "MEASURED";
    public static final String NOT_MEASURED = "NOT_MEASURED";

    public static ModelQualityStats notMeasured(String modelName) {
        return new ModelQualityStats(modelName, 0, 0, 0, null, NOT_MEASURED);
    }

    public static ModelQualityStats of(String modelName, long ups, long downs) {
        long volume = ups + downs;
        if (volume <= 0) {
            return notMeasured(modelName);
        }
        double ratio = (double) ups / (double) volume;
        return new ModelQualityStats(modelName, ups, downs, volume, ratio, MEASURED);
    }
}
