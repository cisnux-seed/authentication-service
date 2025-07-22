pipeline {
    agent any

    environment {
        REGISTRY = 'image-registry.openshift-image-registry.svc:5000'
        NAMESPACE = 'one-gate-payment'
        APP_NAME = 'authentication-service'
        // Update this version when you want to release a new version
        SEMANTIC_VERSION = '1.0.0'
    }

    stages {
        stage('Cleanup Workspace') {
            steps {
                cleanWs()
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    echo "Building Docker image with tags: latest and ${SEMANTIC_VERSION}"

                    sh """
                        # Build the image
                        podman build -t ${APP_NAME}:latest .

                        # Tag with semantic version
                        podman tag ${APP_NAME}:latest ${APP_NAME}:${SEMANTIC_VERSION}

                        # Tag for registry with both versions
                        podman tag ${APP_NAME}:latest ${REGISTRY}/${NAMESPACE}/${APP_NAME}:latest
                        podman tag ${APP_NAME}:latest ${REGISTRY}/${NAMESPACE}/${APP_NAME}:${SEMANTIC_VERSION}
                    """
                }
            }
        }

        stage('Push to Registry') {
            steps {
                script {
                    echo "Pushing both latest and ${SEMANTIC_VERSION} tags to registry..."

                    sh """
                        TOKEN=\$(oc whoami -t)
                        echo \$TOKEN | podman login -u jenkins --password-stdin ${REGISTRY}

                        # Push both tags
                        podman push ${REGISTRY}/${NAMESPACE}/${APP_NAME}:latest
                        podman push ${REGISTRY}/${NAMESPACE}/${APP_NAME}:${SEMANTIC_VERSION}
                    """
                }
            }
        }

        stage('Update Deployment') {
            steps {
                script {
                    echo "Updating deployment with latest image..."

                    sh """
                        oc project ${NAMESPACE}

                        # Update the deployment with latest image
                        oc set image deployment/${APP_NAME} ${APP_NAME}=${REGISTRY}/${NAMESPACE}/${APP_NAME}:latest -n ${NAMESPACE}

                        # Wait for rollout to complete
                        oc rollout status deployment/${APP_NAME} -n ${NAMESPACE} --timeout=300s

                        # Show deployment status
                        oc get pods -l app=${APP_NAME} -n ${NAMESPACE}

                        echo "✅ Deployed with tags: latest and ${SEMANTIC_VERSION}"
                    """
                }
            }
        }
    }

    post {
        always {
            script {
                sh """
                    podman rmi ${APP_NAME}:latest || true
                    podman rmi ${APP_NAME}:${SEMANTIC_VERSION} || true
                    podman rmi ${REGISTRY}/${NAMESPACE}/${APP_NAME}:latest || true
                    podman rmi ${REGISTRY}/${NAMESPACE}/${APP_NAME}:${SEMANTIC_VERSION} || true
                """
            }
        }
        success {
            echo "🎉 Pipeline completed successfully!"
            echo "Images available:"
            echo "  - ${APP_NAME}:latest"
            echo "  - ${APP_NAME}:${SEMANTIC_VERSION}"
            echo ""
            echo "To access the service via port-forward:"
            echo "oc port-forward svc/${APP_NAME} 8080:8080 -n ${NAMESPACE}"
        }
        failure {
            echo "❌ Pipeline failed! Check the logs above for details."
        }
    }
}