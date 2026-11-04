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

        stage('Vault erişimini doğrula') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 'reserveflow-ansible-vault-file',
                        variable: 'DEPLOY_VAULT_FILE'
                    ),
                    string(
                        credentialsId: 'reserveflow-ansible-vault-password',
                        variable: 'DEPLOY_VAULT_PASSWORD'
                    )
                ]) {
                    withEnv(['PATH+ANSIBLE=/var/lib/jenkins/ansible-venv/bin']) {
                        sh '''
                            set -eu
                            set +x
                            umask 077

                            TEMP_DIR=$(mktemp -d)
                            trap 'rm -rf "$TEMP_DIR"' EXIT

                            printf '%s\\n' "$DEPLOY_VAULT_PASSWORD" \
                                > "$TEMP_DIR/vault-password"

                            ansible-vault view \
                                --vault-password-file "$TEMP_DIR/vault-password" \
                                "$DEPLOY_VAULT_FILE" > /dev/null

                            echo "Vault dosyası başarıyla açıldı."
                        '''
                    }
                }
            }
        }

        stage('Ansible bağlantısını doğrula') {
            steps {
                sshagent(credentials: ['reserveflow-deploy-ssh']) {
                    withEnv(['PATH+ANSIBLE=/var/lib/jenkins/ansible-venv/bin']) {
                        sh '''
                            set -eu

                            ANSIBLE_HOST_KEY_CHECKING=True \
                            ansible \
                                -i infra/ansible/inventory.jenkins.yaml \
                                lab \
                                -m ansible.builtin.ping
                        '''
                    }
                }
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

        stage('Dağıtım onayı') {
            when {
                expression {
                    env.GIT_BRANCH == 'origin/main' ||
                    env.GIT_BRANCH == 'main'
                }
            }

            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    input message: 'Test edilen sürüm Ubuntu ortamına dağıtılsın mı?',
                          ok: 'Dağıt'
                }
            }
        }
        stage('Ubuntu dağıtımı') {
            when {
                expression {
                    env.GIT_BRANCH == 'origin/main' ||
                    env.GIT_BRANCH == 'main'
                }
            }

            steps {
                sshagent(credentials: ['reserveflow-deploy-ssh']) {
                    withCredentials([
                        file(
                            credentialsId: 'reserveflow-ansible-vault-file',
                            variable: 'DEPLOY_VAULT_FILE'
                        ),
                        string(
                            credentialsId: 'reserveflow-ansible-vault-password',
                            variable: 'DEPLOY_VAULT_PASSWORD'
                        )
                    ]) {
                        withEnv([
                            'PATH+ANSIBLE=/var/lib/jenkins/ansible-venv/bin',
                            'ANSIBLE_HOST_KEY_CHECKING=True'
                        ]) {
                            sh '''
                                set -eu
                                set +x
                                umask 077

                                TEMP_DIR=$(mktemp -d)
                                trap 'rm -rf "$TEMP_DIR"' EXIT

                                printf '%s\\n' "$DEPLOY_VAULT_PASSWORD" \
                                    > "$TEMP_DIR/vault-password"

                                COMMIT=$(git rev-parse --short=12 HEAD)
                                export DEPLOY_IMAGE_TAG="ci-${BUILD_NUMBER}-${COMMIT}"

                                EXTRA_VARS=$(python3 -c 'import json, os; print(json.dumps({"deployment_vault_file": os.environ["DEPLOY_VAULT_FILE"], "deploy_image_tag": os.environ["DEPLOY_IMAGE_TAG"]}))')

                                echo "Dağıtılacak sürüm: $DEPLOY_IMAGE_TAG"

                                ansible-playbook \
                                    -i infra/ansible/inventory.jenkins.yaml \
                                    infra/ansible/deploy.yaml \
                                    --vault-password-file "$TEMP_DIR/vault-password" \
                                    --extra-vars "$EXTRA_VARS"
                            '''
                        }
                    }
                }
            }
        }
    }
}
