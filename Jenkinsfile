pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'checkout scm',

            }
        }

        stage('Java kontrolü') {
            steps {
                sh 'java -version'
                sh 'javac -version'
            }
        }
        stage('Maven Kontrolü'){
            steps {
                sh 'sh ./mvnw --version'
            }
        }
        stage('Testler'){
            steps {
                sh '''
            sh ./mvnw -B \
              '-Dtest=*,!InventoryServiceApplicationTests' \
              -Dspring.rabbitmq.listener.simple.auto-startup=false \
              -Dspring.rabbitmq.username=test \
              -Dspring.rabbitmq.password=test \
              test
        '''
            }
            post {
                always {
                    junit testResults: '**/target/surefire-reports/TEST-*.xml'
                }
            }
        }
    }
}