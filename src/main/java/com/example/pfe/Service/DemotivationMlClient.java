package com.example.pfe.Service;

import com.example.pfe.dto.MlFeaturesRequest;
import com.example.pfe.dto.MlPredictionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class DemotivationMlClient {

    private final RestTemplate restTemplate;
    private final String predictUrl;
    private final ObjectMapper objectMapper;

    public DemotivationMlClient(
            @Value("${ml.service.url:http://ml-service:8000}") String baseUrl,
            ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.predictUrl = baseUrl + "/predict";
        this.objectMapper = objectMapper;
    }

    public MlPredictionResponse predict(MlFeaturesRequest features) {
        try {
            String jsonBody = objectMapper.writeValueAsString(features);
            log.debug("ML request body: {}", jsonBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<MlPredictionResponse> response = restTemplate.exchange(
                    predictUrl,
                    HttpMethod.POST,
                    entity,
                    MlPredictionResponse.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.warn("Microservice ML indisponible : {}. Retour null.", e.getMessage());
            return null;
        }
    }
}