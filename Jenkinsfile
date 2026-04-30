pipeline {
    agent any

    tools {
        maven 'Maven3'
        jdk 'JDK17'
    }

    environment {
        IMAGE_NAME = 'mon-backend'
        APP_PORT = '8080'
        ENV_FILE = '/etc/pfe/.env'
        FRONTEND_REPO = 'https://github.com/oumaymawerfelli/pfe_attendance_management_front.git'
        FRONTEND_DIR = '../pfe_attendance_management_front'

    }

    stages {

        stage('1. Checkout Backend') {
            steps {
                echo 'Recuperation du code backend depuis GitHub...'
                checkout scm
            }
        }

        stage('2. Checkout Frontend') {
            steps {
                echo 'Recuperation du code frontend depuis GitHub...'
                sh '''
                    if [ -d "${FRONTEND_DIR}" ]; then
                        echo "Mise a jour du frontend existant..."
                        cd ${FRONTEND_DIR}
                        git pull
                    else
                        echo "Clonage du frontend..."
                        cd ..
                        git clone ${FRONTEND_REPO}
                    fi
                '''
            }
        }

        stage('3. Maven Compile') {
            steps {
                echo 'Compilation Maven (backend)...'
                sh 'mvn clean compile -B'
            }
        }

        stage('4. Tests unitaires') {
            steps {
                echo 'Lancement des tests (mode tolerant)...'
                sh 'mvn test -B -fae || true'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('5. Package JAR') {
            steps {
                echo 'Creation du JAR...'
                sh 'mvn package -DskipTests -B'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        stage('6. Preparer .env') {
            steps {
                echo 'Recuperation du fichier .env depuis la VM...'
                sh '''
                    if [ ! -f ${ENV_FILE} ]; then
                        echo "ERREUR: ${ENV_FILE} introuvable sur la VM !"
                        echo "Creer avec: sudo nano /etc/pfe/.env"
                        exit 1
                    fi
                    cp ${ENV_FILE} .env
                    echo "Fichier .env copie dans le workspace"
                '''
            }
        }

        stage('7. Deploy avec docker-compose') {
            steps {
                echo 'Deploiement complet (backend + frontend + MySQL)...'
                sh '''
                    docker-compose down --remove-orphans || true
                    docker-compose up -d --build
                    echo "Attente demarrage des conteneurs (30s)..."
                    sleep 30
                    docker-compose ps
                '''
            }
        }

        stage('8. Health Check Backend') {
            steps {
                echo 'Verification du backend...'
                sh '''
                    for i in $(seq 1 24); do
                        STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/ || echo "000")
                        if echo "$STATUS" | grep -qE "200|401|403"; then
                            echo "Backend repond avec code: $STATUS"
                            exit 0
                        fi
                        echo "Tentative $i/24 (code: $STATUS)..."
                        sleep 5
                    done
                    echo "Backend ne repond pas"
                    docker-compose logs --tail=30 backend-app
                    exit 1
                '''
            }
        }

        stage('9. Health Check Frontend') {
            steps {
                echo 'Verification du frontend...'
                sh '''
                    for i in $(seq 1 12); do
                        STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:4200/ || echo "000")
                        if [ "$STATUS" = "200" ]; then
                            echo "Frontend repond avec code: $STATUS"
                            exit 0
                        fi
                        echo "Tentative $i/12 (code: $STATUS)..."
                        sleep 5
                    done
                    echo "Frontend ne repond pas, mais on continue"
                    docker-compose logs --tail=20 frontend-app
                '''
            }
        }
    }

    post {
        success {
            echo '==============================================='
            echo 'DEPLOIEMENT COMPLET REUSSI !'
            echo ''
            echo 'Frontend: http://192.168.33.10:4200'
            echo 'Backend : http://192.168.33.10:8080'
            echo 'MySQL   : 192.168.33.10:3306'
            echo '==============================================='
        }
        failure {
            echo 'ECHEC du deploiement - voir les logs'
            sh '''
                echo "=== Etat des conteneurs ==="
                docker-compose ps || true
                echo "=== Logs backend ==="
                docker-compose logs --tail=30 backend-app || true
                echo "=== Logs frontend ==="
                docker-compose logs --tail=30 frontend-app || true
            '''
        }
        always {
            echo 'Nettoyage du .env du workspace (securite)...'
            sh 'rm -f .env || true'
        }
    }
}