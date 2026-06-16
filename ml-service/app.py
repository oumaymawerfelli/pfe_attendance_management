"""
Microservice de détection de démotivation — Module IA du PFE ArabSoft.

Expose l'arbre de décision entraîné (scikit-learn) via une API REST.
Le backend Spring Boot envoie les 4 indicateurs de présence d'un employé
et reçoit le verdict ML + la règle métier (approche hybride).

Lancement local :  uvicorn app:app --host 0.0.0.0 --port 8000
"""
from contextlib import asynccontextmanager
import joblib
import pandas as pd
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

MODEL_PATH = "model.joblib"
FEATURES = ["taux_absence", "taux_retard", "taux_demi_j", "taux_depart"]

# Chargé une seule fois au démarrage (et non à chaque requête)
_state = {}


@asynccontextmanager
async def lifespan(app: FastAPI):
    bundle = joblib.load(MODEL_PATH)
    _state["model"] = bundle["model"]
    _state["features"] = bundle["features"]
    _state["version"] = bundle.get("version", "1.0")
    yield
    _state.clear()


app = FastAPI(
    title="Détection de démotivation — Module IA",
    description="Prédit le risque de démotivation d'un employé à partir de ses indicateurs de présence.",
    version="1.0",
    lifespan=lifespan,
)

# CORS : autorise le backend / frontend à appeler le service
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],          # à restreindre en production
    allow_methods=["*"],
    allow_headers=["*"],
)


class EmployeeFeatures(BaseModel):
    """Les 4 indicateurs de présence, chacun entre 0 et 1."""
    taux_absence: float = Field(..., ge=0, le=1, description="Part de jours ouvrés sans pointage")
    taux_retard: float = Field(..., ge=0, le=1, description="Part de jours travaillés en retard")
    taux_demi_j: float = Field(..., ge=0, le=1, description="Part de jours en demi-journée")
    taux_depart: float = Field(..., ge=0, le=1, description="Part de jours avec départ anticipé")

    model_config = {
        "json_schema_extra": {
            "example": {"taux_absence": 0.62, "taux_retard": 0.10,
                        "taux_demi_j": 0.10, "taux_depart": 0.10}
        }
    }


class PredictionResponse(BaseModel):
    a_risque_ml: bool
    proba_risque: float
    a_risque_regle: bool
    verdict_hybride: bool
    niveau: str
    model_version: str


def regle_metier(f: EmployeeFeatures) -> bool:
    """Règle métier (baseline), identique à l'application Spring Boot."""
    cond_A = f.taux_absence > 0.30
    cond_B = (f.taux_absence + f.taux_demi_j + f.taux_depart) > 0.40
    return bool(cond_A or cond_B)


def niveau_risque(proba: float) -> str:
    if proba >= 0.66:
        return "ÉLEVÉ"
    if proba >= 0.33:
        return "MOYEN"
    return "FAIBLE"


@app.get("/health")
def health():
    """Sonde de vivacité — utilisée par Docker / Kubernetes."""
    return {"status": "ok", "model_loaded": "model" in _state,
            "version": _state.get("version")}


@app.post("/predict", response_model=PredictionResponse)
def predict(features: EmployeeFeatures):
    if "model" not in _state:
        raise HTTPException(status_code=503, detail="Modèle non chargé")

    X = pd.DataFrame([[getattr(features, c) for c in FEATURES]], columns=FEATURES)
    proba = float(_state["model"].predict_proba(X)[0][1])
    pred_ml = bool(_state["model"].predict(X)[0])
    pred_regle = regle_metier(features)

    return PredictionResponse(
        a_risque_ml=pred_ml,
        proba_risque=round(proba, 3),
        a_risque_regle=pred_regle,
        # Approche hybride : à risque si l'UNE des deux méthodes le signale
        verdict_hybride=bool(pred_ml or pred_regle),
        niveau=niveau_risque(proba),
        model_version=_state.get("version", "1.0"),
    )
