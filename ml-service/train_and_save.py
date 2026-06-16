"""Entraîne l'arbre de décision et le sauvegarde pour le microservice.
Logique alignée sur le notebook, avec bruit appliqué uniquement aux cas
ambigus (proches de la frontière) pour éviter d'étiqueter à risque un
profil parfaitement sain."""
import numpy as np, pandas as pd, joblib
from sklearn.tree import DecisionTreeClassifier

RANDOM_STATE = 42
FEATURES = ["taux_absence", "taux_retard", "taux_demi_j", "taux_depart"]

def etiqueter(f):
    cond_A = f["taux_absence"] > 0.30
    cond_B = (f["taux_absence"] + f["taux_demi_j"] + f["taux_depart"]) > 0.40
    return (cond_A | cond_B).astype(int)

rng = np.random.default_rng(RANDOM_STATE)
N = 400
def generer(n, risk):
    if risk:
        ab=rng.beta(2,3,n)*0.7; dm=rng.beta(2,8,n)*0.4; dp=rng.beta(2,9,n)*0.3; rt=rng.beta(2,6,n)*0.5
    else:
        ab=rng.beta(1,20,n)*0.3; dm=rng.beta(1,15,n)*0.25; dp=rng.beta(1,18,n)*0.2; rt=rng.beta(1.5,8,n)*0.4
    return np.clip(np.c_[ab,rt,dm,dp],0,1)

X = np.vstack([generer(N//2,True), generer(N//2,False)])
synth = pd.DataFrame(X.round(3), columns=FEATURES)
y = etiqueter(synth)

# Bruit UNIQUEMENT sur les cas ambigus (score d'engagement proche du seuil 0.40)
desengagement = synth["taux_absence"] + synth["taux_demi_j"] + synth["taux_depart"]
ambigu = (desengagement > 0.25) & (desengagement < 0.55)
flip = (rng.random(len(y)) < 0.10) & ambigu
y = y.copy(); y[flip] = 1 - y[flip]

arbre = DecisionTreeClassifier(max_depth=3, random_state=RANDOM_STATE, class_weight="balanced")
arbre.fit(synth[FEATURES], y)

joblib.dump({"model": arbre, "features": FEATURES, "version": "1.0"}, "model.joblib")
print(f"Modèle sauvegardé. Bruit appliqué à {flip.sum()} cas ambigus.")

# Vérification de cohérence
tests = {"sain parfait":[0,0,0,0], "absent 62%":[0.62,0.1,0.1,0.1],
         "demi-j++":[0.05,0,0.35,0.05], "moyen":[0.15,0.1,0.1,0.1]}
for name, vals in tests.items():
    Xt = pd.DataFrame([vals], columns=FEATURES)
    print(f"  {name:15} pred={arbre.predict(Xt)[0]} proba={arbre.predict_proba(Xt)[0][1]:.2f}")
