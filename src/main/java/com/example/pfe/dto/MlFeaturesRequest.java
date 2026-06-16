package com.example.pfe.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MlFeaturesRequest {

    @JsonProperty("taux_absence")
    private double tauxAbsence;

    @JsonProperty("taux_retard")
    private double tauxRetard;

    @JsonProperty("taux_demi_j")
    private double tauxDemiJ;

    @JsonProperty("taux_depart")
    private double tauxDepart;
}