package com.example.pfe.Service;

import com.example.pfe.dto.MlFeaturesRequest;
import com.example.pfe.dto.MlPredictionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class DemotivationMlClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DemotivationMlClient(
            @Value("${ml.service.url:http://ml-service:8000}") String baseUrl,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    public MlPredictionResponse predict(MlFeaturesRequest features) {
        try {
            String jsonBody = objectMapper.writeValueAsString(features);
            log.debug("ML request body: {}", jsonBody);
            return restClient.post()
                    .uri("/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(MlPredictionResponse.class);
        } catch (Exception e) {
            log.warn("Microservice ML indisponible : {}. Retour null.", e.getMessage());
            return null;
        }
    }
}