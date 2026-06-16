package com.example.pfe.Service;

import com.example.pfe.dto.MlFeaturesRequest;
import com.example.pfe.dto.MlPredictionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class DemotivationMlClient {

    private final RestClient restClient;

    public DemotivationMlClient(
            @Value("${ml.service.url:http://ml-service:8000}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public MlPredictionResponse predict(MlFeaturesRequest features) {
        try {
            return restClient.post()
                    .uri("/predict")
                    .body(features)
                    .retrieve()
                    .body(MlPredictionResponse.class);
        } catch (Exception e) {
            log.warn("Microservice ML indisponible : {}. Retour null.", e.getMessage());
            return null;  // le service appelant gère le cas null
        }
    }
}