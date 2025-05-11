pipeline {
    agent any

    environment {
        DOCKER_IMAGE = "ghandgevikas/devopsexamapp:latest"
        SCANNER_HOME = tool 'sonar-scanner'
    }

    stages {
        stage('Git Checkout') {
            steps {
                git url: 'https://github.com/Vikasghandge/Docker.git', 
                    branch: 'main'
            }
        }

        stage('File System Scan') {
            steps {
                sh "trivy fs --scanners vuln,misconfig --format table -o trivy-fs-report.html ."
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('sonar') {
                    sh """
                        ${SCANNER_HOME}/bin/sonar-scanner \
                        -Dsonar.projectName=devops-exam-app \
                        -Dsonar.projectKey=devops-exam-app \
                        -Dsonar.sources=. \
                        -Dsonar.exclusions=**/*.java \
                        -Dsonar.python.version=3 \
                        -Dsonar.host.url=http://localhost:9000
                    """
                }
            }
        }

        stage('Verify Docker Compose') {
            steps {
                sh '''
                    docker compose version || { echo "Docker Compose not available"; exit 1; }
                '''
            }
        }

        stage('Build Docker Image') {
            steps {
                dir('devops-exam-app-master/backend') {
                    script {
                        withDockerRegistry(credentialsId: 'docker', toolName: 'docker') {
                            sh "docker build -t ${DOCKER_IMAGE} ."
                            sh "docker push ${DOCKER_IMAGE}"
                        }
                    }
                }
            }
        }

        stage('Docker Scout Image Analysis') {
            steps {
                script {
                    withDockerRegistry(credentialsId: 'docker', toolName: 'docker') {
                        sh "docker-scout quickview ${DOCKER_IMAGE}"
                        sh "docker-scout cves ${DOCKER_IMAGE}"
                        sh "docker-scout recommendations ${DOCKER_IMAGE}"
                        
                    }
                }
            }
        }

        stage('Deploy with Docker Compose') {
            steps {
                dir('devops-exam-app-master') {
                    sh '''#!/bin/bash
                        echo "=== Current Directory ==="
                        pwd
                        ls -la

                        echo "Stopping existing containers..."
                        docker compose down --remove-orphans || true

                        echo "Starting services..."
                        docker compose up -d --build || { echo "Failed to start containers"; exit 1; }

                        echo "Waiting for MySQL..."
                        for i in {1..24}; do
                            if docker compose exec mysql mysqladmin ping -uroot -prootpass --silent; then
                                echo "MySQL ready!"
                                break
                            fi
                            echo "Attempt $i/24: MySQL not ready"
                            docker compose logs mysql --tail=5 || true
                            sleep 5
                            if [ $i -eq 24 ]; then
                                echo "ERROR: MySQL failed to start"
                                exit 1
                            fi
                        done

                        echo "Final verification:"
                        docker compose ps
                        sleep 10
                    '''
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                dir('devops-exam-app-master') {
                    sh '''
                        echo "=== Container Status ==="
                        docker compose ps -a
                        echo "=== Testing Flask Endpoint ==="
                        curl -I http://localhost:5000 || true
                    '''
                }
            }
        }
    }

    post {
        success {
            echo '🚀 Deployment successful!'
            archiveArtifacts artifacts: 'trivy-fs-report.html', allowEmptyArchive: true
        }
        failure {
            echo '❗ Pipeline failed. Check logs above.'
            sh '''
                echo "=== Error Investigation ==="
                docker compose logs --tail=50 || true
            '''
        }
        always {
            sh '''
                echo "=== Final Logs ==="
                docker compose logs --tail=20 || true
            '''
            archiveArtifacts artifacts: 'trivy-fs-report.html', allowEmptyArchive: true
        }
    }
}






---------------------------------------------------------------------------------------------------
---------------------------------------------------------------------------------------------------
---------------------------------------------------------------------------------------------------
// this is another pipeline for devops-exam-master-app to deploy a application on kubernete cluster.//
---------------------------------------------------------------------------------------------------
---------------------------------------------------------------------------------------------------
---------------------------------------------------------------------------------------------------

pipeline {
    agent any

    environment {
        DOCKER_IMAGE = "ghandgevikas/devopsexamapp:latest"
        SCANNER_HOME = tool 'sonar-scanner'
        CLUSTER_NAME = "my-eks"
        REGION = "ap-south-1"
    }

    stages {
        stage('Git Checkout') {
            steps {
                git url: 'https://github.com/Vikasghandge/Docker.git', branch: 'main'
            }
        }

        stage('File System Scan') {
            steps {
                sh "trivy fs --scanners vuln,misconfig --format table -o trivy-fs-report.html ."
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('sonar') {
                    sh """
                        ${SCANNER_HOME}/bin/sonar-scanner \
                        -Dsonar.projectName=devops-exam-app \
                        -Dsonar.projectKey=devops-exam-app \
                        -Dsonar.sources=. \
                        -Dsonar.exclusions=**/*.java \
                        -Dsonar.python.version=3 \
                        -Dsonar.host.url=http://localhost:9000
                    """
                }
            }
        }

        stage('Verify Docker Compose') {
            steps {
                sh 'docker compose version || { echo "Docker Compose not available"; exit 1; }'
            }
        }

        stage('Build Docker Image') {
            steps {
                dir('devops-exam-app-master/backend') {
                    script {
                        withDockerRegistry(credentialsId: 'docker', toolName: 'docker') {
                            sh "docker build -t ${DOCKER_IMAGE} ."
                            sh "docker push ${DOCKER_IMAGE}"
                        }
                    }
                }
            }
        }

        stage('Docker Scout Image Analysis') {
            steps {
                script {
                    withDockerRegistry(credentialsId: 'docker', toolName: 'docker') {
                        sh "docker-scout quickview ${DOCKER_IMAGE}"
                        sh "docker-scout cves ${DOCKER_IMAGE}"
                        sh "docker-scout recommendations ${DOCKER_IMAGE}"
                    }
                }
            }
        }

        stage('Deploy with Docker Compose') {
            steps {
                dir('devops-exam-app-master') {
                    sh '''
                        echo "Stopping existing containers..."
                        docker compose down --remove-orphans || true

                        echo "Starting services..."
                        docker compose up -d --build

                        echo "Waiting for MySQL..."
                        for i in {1..24}; do
                            if docker compose exec mysql mysqladmin ping -uroot -prootpass --silent; then
                                echo "MySQL ready!"
                                break
                            fi
                            echo "Attempt $i/24: MySQL not ready"
                            docker compose logs mysql --tail=5 || true
                            sleep 5
                            if [ $i -eq 24 ]; then
                                echo "ERROR: MySQL failed to start"
                                exit 1
                            fi
                        done
                    '''
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                dir('devops-exam-app-master') {
                    sh '''
                        docker compose ps -a
                        echo "Testing Flask endpoint:"
                        curl -I http://localhost:5000 || true
                    '''
                }
            }
        }

        stage('Deploy to EKS Cluster') {
            steps {
                dir('devops-exam-app-master') {
                    script {
                        sh '''
                            echo "Verifying AWS credentials..."
                            aws sts get-caller-identity

                            echo "Configuring kubectl for EKS cluster..."
                            aws eks update-kubeconfig --name ${CLUSTER_NAME} --region ${REGION}

                            echo "Verifying kubeconfig..."
                            kubectl config current-context

                            echo "Deploying application to EKS..."
                            kubectl apply -f deployment.yml
                            kubectl apply -f service.yml

                            echo "Verifying deployment..."
                            kubectl get pods
                            kubectl get svc
                        '''
                    }
                }
            }
        }
    }

    post {
        success {
            echo '🚀 Deployment successful!'
            archiveArtifacts artifacts: 'trivy-fs-report.html', allowEmptyArchive: true
        }
        failure {
            echo '❗ Pipeline failed. Check logs above.'
            sh '''
                echo "=== Error Investigation ==="
                docker compose logs --tail=50 || true
            '''
        }
        always {
            sh '''
                echo "=== Final Logs ==="
                docker compose logs --tail=20 || true
            '''
            archiveArtifacts artifacts: 'trivy-fs-report.html', allowEmptyArchive: true
        }
    }
}
