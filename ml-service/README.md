# Microservice ML — Détection de démotivation

Microservice REST (FastAPI) qui expose l'arbre de décision entraîné pour prédire
le risque de démotivation d'un employé à partir de ses indicateurs de présence.
Conçu pour s'intégrer au backend Spring Boot du PFE via Docker Compose.

## Architecture

```
Frontend Angular
      │
      ▼
Backend Spring Boot ──── HTTP ────► Microservice ML (FastAPI)
  (règle métier)                      (arbre de décision)
      │                                     │
      └──────── verdict hybride ◄───────────┘
```

Le backend conserve sa **règle métier** (baseline) et l'enrichit avec la
prédiction ML. Le verdict final est **hybride** : à risque si l'une des deux
méthodes le signale. Si le microservice est indisponible, le backend retombe
sur la règle seule (dégradation gracieuse).

## Contenu

| Fichier | Rôle |
|---|---|
| `app.py` | API FastAPI (endpoints `/health` et `/predict`) |
| `train_and_save.py` | Entraîne l'arbre et produit `model.joblib` |
| `requirements.txt` | Dépendances Python |
| `Dockerfile` | Image du service (entraîne le modèle au build) |
| `docker-compose.ml.yml` | Service à fusionner avec ton compose existant |
| `INTEGRATION_SPRING_BOOT.md` | Code Java d'intégration |

## Endpoints

**`GET /health`** — sonde de vivacité
```json
{"status": "ok", "model_loaded": true, "version": "1.0"}
```

**`POST /predict`** — prédiction
Requête :
```json
{"taux_absence": 0.62, "taux_retard": 0.10, "taux_demi_j": 0.10, "taux_depart": 0.10}
```
Réponse :
```json
{
  "a_risque_ml": true,
  "proba_risque": 0.985,
  "a_risque_regle": true,
  "verdict_hybride": true,
  "niveau": "ÉLEVÉ",
  "model_version": "1.0"
}
```

Documentation interactive auto-générée : `http://localhost:8000/docs`

## Déploiement sur ta VM

1. Place ce dossier `ml-service/` à la racine de ton projet (à côté du
   `docker-compose.yml` existant).

2. Fusionne le contenu de `docker-compose.ml.yml` dans ton `docker-compose.yml`
   (ajoute le service `ml-service` sous `services:`, et **adapte le nom du
   réseau** à celui que tu utilises déjà).

3. Construis et lance :
   ```bash
   docker compose build ml-service
   docker compose up -d ml-service
   ```

4. Vérifie :
   ```bash
   docker compose logs ml-service
   curl http://localhost:8000/health      # si tu as exposé le port
   ```

5. Côté Spring Boot, ajoute `ml.service.url=http://ml-service:8000` et suis
   `INTEGRATION_SPRING_BOOT.md`.

## Test rapide en local (sans Docker)

```bash
pip install -r requirements.txt
python train_and_save.py          # génère model.joblib
uvicorn app:app --reload --port 8000
```

## Note pour la migration K3s (phase ultérieure)

Le service est *stateless* : aucune base, aucun volume. Sa migration vers
Kubernetes est donc triviale — un simple Deployment + Service, sans PVC.
C'est le composant le plus simple à porter dans ta future architecture K3s/ArgoCD.
