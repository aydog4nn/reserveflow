pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
               checkout scm

            }
        }
        stage('Ansible dosya kontrolü') {
            steps {
                withEnv(['PATH+ANSIBLE=/var/lib/jenkins/ansible-venv/bin']) {
                    sh '''
                        set -eu
                        ansible-galaxy collection install \
                            -r infra/ansible/requirements.yaml

                        for PLAYBOOK in setup deploy; do
                            ansible-playbook \
                                -i infra/ansible/inventory.yaml \
                                "infra/ansible/${PLAYBOOK}.yaml" \
                                --syntax-check
                        done

                        ansible-lint infra/ansible/
                    '''
                }
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
        stage('Docker image üretimi') {
            steps {
                sh '''
                    set -eu

                    COMMIT=$(git rev-parse --short=12 HEAD)
                    IMAGE_TAG="ci-${BUILD_NUMBER}-${COMMIT}"

                    for SERVICE in inventory order payment; do
                        docker build \
                            --tag "reserveflow-${SERVICE}:${IMAGE_TAG}" \
                            "services/${SERVICE}-service"
                    done
                '''
            }
        }
        stage('Container smoke testi') {
            steps {
                sh '''
                    set -eu

                    python3 --version

                    COMMIT=$(git rev-parse --short=12 HEAD)
                    export CI_IMAGE_TAG="ci-${BUILD_NUMBER}-${COMMIT}"

                    python3 scripts/smoke_container_flow.py
                '''
            }
        }
        stage('GHCR image yükleme') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'reserveflow-ghcr',
                    usernameVariable: 'GHCR_USER',
                    passwordVariable: 'GHCR_TOKEN'
                )]) {
                    sh '''
                        set -eu
                        set +x

                        export DOCKER_CONFIG="$(mktemp -d)"
                        trap 'rm -rf "$DOCKER_CONFIG"' EXIT

                        printf '%s' "$GHCR_TOKEN" |
                            docker login ghcr.io \
                                --username "$GHCR_USER" \
                                --password-stdin

                        COMMIT=$(git rev-parse --short=12 HEAD)
                        IMAGE_TAG="ci-${BUILD_NUMBER}-${COMMIT}"

                        for SERVICE in inventory order payment; do
                            LOCAL_IMAGE="reserveflow-${SERVICE}:${IMAGE_TAG}"
                            REMOTE_IMAGE="ghcr.io/aydog4nn/reserveflow-${SERVICE}:${IMAGE_TAG}"

                            docker tag "$LOCAL_IMAGE" "$REMOTE_IMAGE"
                            docker push "$REMOTE_IMAGE"
                        done
                    '''
                }
            }
        }

    }
}
