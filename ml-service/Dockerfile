FROM python:3.12-slim

WORKDIR /app

# Dépendances d'abord (cache Docker : ne réinstalle que si requirements change)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Code + script d'entraînement
COPY app.py .
COPY train_and_save.py .

# Entraîner le modèle DANS l'image, avec les versions installées ci-dessus.
# Évite tout décalage de version scikit-learn entre l'entraînement et l'exécution.
RUN python train_and_save.py

EXPOSE 8000

# Sonde de santé pour Docker / Kubernetes
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
