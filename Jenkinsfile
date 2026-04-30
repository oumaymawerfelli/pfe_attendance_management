pipeline {
    agent any

    tools {
        maven 'M2_HOME'
        jdk 'JAVA_HOME'
    }

    environment {
        IMAGE_NAME = 'mon-backend'
        APP_PORT = '8080'
        ENV_FILE = '/etc/pfe/.env'
    }

    stages {

        stage('1 Checkout') {
            steps {
                echo ' Récupération du code depuis GitHub...'
                checkout scm
            }
        }

        stage('2 Maven Compile') {
            steps {
                echo ' Compilation Maven...'
                sh 'mvn clean compile -B'
            }
        }

        stage('3 Tests unitaires') {
            steps {
                echo ' Lancement des tests (mode tolérant)...'
                sh 'mvn test -B -fae || true'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('4 Package JAR') {
            steps {
                echo ' Création du JAR...'
                sh 'mvn package -DskipTests -B'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        stage('5 Docker Build') {
            steps {
                echo ' Construction de l image Docker...'
                sh "docker build -t ${IMAGE_NAME}:${BUILD_NUMBER} -t ${IMAGE_NAME}:latest ."
            }
        }

        stage('6 Préparer .env') {
            steps {
                echo ' Récupération du fichier .env...'
                sh '''
                    if [ ! -f ${ENV_FILE} ]; then
                        echo " ERREUR: ${ENV_FILE} introuvable sur la VM !"
                        exit 1
                    fi
                    cp ${ENV_FILE} .env
                    echo " Fichier .env copié dans le workspace"
                '''
            }
        }

        stage('7 Deploy avec docker-compose') {
            steps {
                echo ' Déploiement avec docker-compose...'
                sh '''
                    # Arrêter les anciens conteneurs (sans toucher au volume MySQL)
                    docker-compose down --remove-orphans || true

                    # Lancer la nouvelle version
                    docker-compose up -d --no-build

                    # Attendre que tout démarre
                    echo " Attente démarrage des conteneurs..."
                    sleep 20

                    # Vérifier
                    docker-compose ps
                '''
            }
        }

        stage('8 Health Check') {
            steps {
                echo ' Vérification de la santé du backend...'
                sh '''
                    # Attendre que le backend réponde (max 2 minutes)
                    for i in $(seq 1 24); do
                        if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/ | grep -qE "200|401|403"; then
                            echo " Backend répond !"
                            curl -s -o /dev/null -w "Code HTTP: %{http_code}\n" http://localhost:8080/
                            exit 0
                        fi
                        echo " Tentative $i/24... (5s)"
                        sleep 5
                    done

                    echo "Backend ne répond pas après 2 minutes"
                    docker-compose logs --tail=50 backend-app
                    exit 1
                '''
            }
        }
    }

    post {
        success {
            echo '''
             ===============================================
                DÉPLOIEMENT RÉUSSI !

                Backend : http://192.168.33.10:8080
                Frontend: http://192.168.33.10:4200

               Build #''' + "${BUILD_NUMBER}" + '''
            ==============================================='''
        }
        failure {
            echo ' ÉCHEC du déploiement - voir les logs'
            sh '''
                echo "=== Logs des conteneurs ==="
                docker-compose ps || true
                docker-compose logs --tail=30 || true
            '''
        }
        always {
            echo ' Pipeline terminé'
            // Nettoyer le .env du workspace après le déploiement (sécurité)
            sh 'rm -f .env || true'
        }
    }
}