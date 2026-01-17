pipeline {
    agent any

    environment {
        DOCKER_IMAGE = "ghandgevikas/devopsexamapp:latest"
        SCANNER_HOME = tool 'sonar-scanner'
    }

    stages {
        stage('Git Checkout') {
            steps {
                git url: 'https://github.com/Vikasghandge/devops_exam-app.git', 
                    branch: 'demo'
            }
        }

        stage('File System Scan') {
            steps {
                sh "trivy fs --security-checks vuln,config --format table -o trivy-fs-report.html ."
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('sonar-server') {
                    sh """
                    ${SCANNER_HOME}/bin/sonar-scanner \
                    -Dsonar.projectName=devops-exam-app \
                    -Dsonar.projectKey=devops-exam-app \
                    -Dsonar.sources=. \
                    -Dsonar.language=py \
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
                            // Push the image to Docker Hub if needed
                            sh "docker push ${DOCKER_IMAGE}"
                        }
                    }
                }
            }
        }

        // Added Docker Scout Image Analysis
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
                # Clean up any existing containers
                docker compose down --remove-orphans || true
                
                # Start services with build
                docker compose up -d --build
                
                # Wait for MySQL to be ready
                echo "Waiting for MySQL to be ready..."
                timeout 120s bash -c '
                while ! docker compose exec -T mysql mysqladmin ping -uroot -prootpass --silent;
                do 
                    sleep 5;
                    docker compose logs mysql --tail=5 || true;
                done'
                
                # Additional wait for full initialization
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
            dir('devops-exam-app-master') {
            sh 'docker compose ps'
            archiveArtifacts artifacts: 'trivy-fs-report.html', allowEmptyArchive: true
        }
     }
        failure {
            echo '❗ Pipeline failed. Check logs above.'
            dir('devops-exam-app-master') {
            sh '''
            echo "=== Error Investigation ==="
            docker compose logs --tail=50 || true
            '''
         }
        }
        always {
            dir('devops-exam-app-master') {
            sh '''
            echo "=== Final Logs ==="
            docker compose logs --tail=20 || true
            '''
            archiveArtifacts artifacts: 'trivy-fs-report.html', allowEmptyArchive: true
        }
    
     }
  }
}
