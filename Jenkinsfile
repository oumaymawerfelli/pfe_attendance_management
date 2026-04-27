pipeline {
    agent any

    tools {
        maven 'Maven3'
        jdk 'JDK17'
    }

    environment {
        IMAGE_NAME = 'mon-backend'
        APP_PORT = '8080'
    }

    stages {

        stage('1️⃣ Checkout') {
            steps {
                echo '📥 Récupération du code depuis GitHub...'
                checkout scm
            }
        }

        stage('2️⃣ Maven Compile') {
            steps {
                echo '🔨 Compilation Maven...'
                sh 'mvn clean compile -B'
            }
        }

        stage('3️⃣ Tests unitaires') {
            steps {
                echo '🧪 Lancement des tests (mode tolérant)...'
                sh 'mvn test -B -fae || true'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('4️⃣ Package JAR') {
            steps {
                echo '📦 Création du JAR...'
                sh 'mvn package -DskipTests -B'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        stage('5️⃣ Docker Build') {
            steps {
                echo '🐳 Construction de l image Docker...'
                sh "docker build -t ${IMAGE_NAME}:${BUILD_NUMBER} -t ${IMAGE_NAME}:latest ."
            }
        }

        stage('6️⃣ Vérification') {
            steps {
                echo '🔍 Vérification de l image Docker...'
                sh "docker images | grep ${IMAGE_NAME}"
            }
        }
    }

    post {
        success {
            echo '''
            ✅ ===============================
               PIPELINE BACKEND RÉUSSI !
               Image: mon-backend:latest
            ================================='''
        }
        failure {
            echo '❌ PIPELINE ÉCHOUÉ - voir les logs'
        }
        always {
            echo '🧹 Pipeline terminé'
        }
    }
}