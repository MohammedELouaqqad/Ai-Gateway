package com.backend.Ai_Gateway.controller.dto.v1;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** OpenAI GET /v1/models response shape. */
public record V1ModelListResponse(String object, List<V1Model> data) {

    public record V1Model(
            String id,
            String object,
            long created,
            @JsonProperty("owned_by") String ownedBy
    ) {}

    public static V1ModelListResponse of(List<V1Model> models) {
        return new V1ModelListResponse("list", models);
    }
}
