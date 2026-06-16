# Intégration du microservice ML dans Spring Boot

Le microservice tourne dans le même Docker Compose que le backend. Spring Boot
l'appelle via l'URL interne du réseau Docker : `http://ml-service:8000`.

## 1. Configuration (`application.properties`)

```properties
# URL du microservice ML (nom du service docker-compose)
ml.service.url=http://ml-service:8000
```

En profil local (hors Docker), surcharge avec `http://localhost:8000` dans
`application-local.properties`.

## 2. DTOs (records Java)

```java
// Requête envoyée au microservice
public record MlFeaturesRequest(
        double taux_absence,
        double taux_retard,
        double taux_demi_j,
        double taux_depart) {}

// Réponse reçue du microservice
public record MlPredictionResponse(
        boolean a_risque_ml,
        double  proba_risque,
        boolean a_risque_regle,
        boolean verdict_hybride,
        String  niveau,
        String  model_version) {}
```

## 3. Client REST (`DemotivationMlClient.java`)

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DemotivationMlClient {

    private final RestClient restClient;

    public DemotivationMlClient(@Value("${ml.service.url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public MlPredictionResponse predict(MlFeaturesRequest features) {
        return restClient.post()
                .uri("/predict")
                .body(features)
                .retrieve()
                .body(MlPredictionResponse.class);
    }

    public boolean isHealthy() {
        try {
            restClient.get().uri("/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

> `RestClient` est disponible depuis Spring 6.1 / Boot 3.2. Si ta version est
> plus ancienne, utilise `RestTemplate` ou `WebClient` (même logique).

## 4. Utilisation dans ton service existant

Dans `DemotivationBaselineService`, tu calcules déjà les 4 taux par employé.
Réutilise-les pour appeler le ML :

```java
MlFeaturesRequest req = new MlFeaturesRequest(
        tauxAbsence, tauxRetard, tauxDemiJournee, tauxDepartAnticipe);

MlPredictionResponse ml = mlClient.predict(req);

// Approche hybride : tu disposes des deux verdicts
boolean aRisque = ml.verdict_hybride();   // règle OU ml
String niveauMl = ml.niveau();            // FAIBLE / MOYEN / ÉLEVÉ
double probabilite = ml.proba_risque();
```

## 5. Robustesse (recommandé)

Le ML est un service *complémentaire* : s'il est indisponible, ta règle métier
doit continuer à fonctionner. Encapsule l'appel :

```java
public boolean evaluerRisque(MlFeaturesRequest req, boolean verdictRegle) {
    try {
        return mlClient.predict(req).verdict_hybride();
    } catch (Exception e) {
        log.warn("Microservice ML indisponible, repli sur la règle métier", e);
        return verdictRegle;   // dégradation gracieuse
    }
}
```

C'est un point fort à mentionner en soutenance : architecture **résiliente**,
le ML enrichit le système sans en devenir un point de défaillance unique.
