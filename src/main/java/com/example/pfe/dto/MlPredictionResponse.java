package com.example.pfe.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MlPredictionResponse {

    @JsonProperty("a_risque_ml")
    private boolean aRisqueMl;

    @JsonProperty("proba_risque")
    private double probaRisque;

    @JsonProperty("a_risque_regle")
    private boolean aRisqueRegle;

    @JsonProperty("verdict_hybride")
    private boolean verdictHybride;

    @JsonProperty("niveau")
    private String niveau;

    @JsonProperty("model_version")
    private String modelVersion;
}