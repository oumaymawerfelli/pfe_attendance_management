# ============================================
# ÉTAPE 1 : Build avec Maven
# ============================================
FROM maven:3.8-eclipse-temurin-17 AS build

WORKDIR /app

# Cache des dépendances
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build
COPY src ./src
RUN mvn clean package -DskipTests

# ============================================
# ÉTAPE 2 : Image finale légère
# ============================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Créer le dossier d'upload (pour les avatars)
RUN mkdir -p /app/uploads/avatars

# Copier le JAR
COPY --from=build /app/target/*.jar app.jar

# Port exposé
EXPOSE 8080

# Healthcheck (vérifie que l'app tourne)
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider --server-response http://localhost:8080/ 2>&1 | grep -q "HTTP/" || exit 1

# Lancer l'application
ENTRYPOINT ["java", "-jar", "app.jar"]