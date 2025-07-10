# 🚢 Guia Completo para Deploy do Vortex em Kubernetes

## 📋 Visão Geral

Este guia apresenta uma análise detalhada de como implementar o projeto **Vortex** em um ambiente Kubernetes, incluindo estratégias de migração, configurações necessárias e boas práticas para um sistema de controle de estoque distribuído.

## 🏗️ Arquitetura Atual do Projeto

### 📦 Componentes do Sistema

O projeto Vortex é composto por uma arquitetura de microsserviços com os seguintes componentes:

#### **Backend Services**
1. **Vortex Application Service** (Spring Boot 3.5.3)
   - **Porta**: 8080
   - **Framework**: Spring Boot com Java 24
   - **Funcionalidades**: Controle de estoque, produtos, movimentações e relatórios
   - **Dependências**: JPA, Security, Validation, OpenAPI

2. **Vortex Authorization Service** (Quarkus 3.24.2)
   - **Porta**: 8081
   - **Framework**: Quarkus com Java 24
   - **Funcionalidades**: Autenticação, autorização, JWT, OIDC
   - **Dependências**: Hibernate Panache, SmallRye JWT, Health Check

#### **Frontend Applications**
1. **Main Application Frontend** (Vue.js 3.5.17)
   - **Porta**: 5173 (dev) / 4173 (prod)
   - **Framework**: Vue.js + Vite + TypeScript
   - **UI Library**: Vuetify 3.8.11
   - **Funcionalidades**: Interface principal do sistema

2. **Authorization Frontend** (React 18.2.0)
   - **Porta**: 3001
   - **Framework**: React + TypeScript + Vite
   - **Funcionalidades**: Interface de login e registro

#### **Infraestrutura**
- **Banco de Dados**: Oracle Enterprise (produção) / H2 (desenvolvimento)
- **Message Brokers**: Apache Kafka, RabbitMQ, Amazon SQS (configurável)
- **Containerização**: Docker com multi-stage builds
- **Orquestração**: Docker Compose (atual)

## 🎯 Estratégia de Migração para Kubernetes

### 1. **Preparação da Infraestrutura**

#### **1.1 Cluster Kubernetes**
```yaml
# Requisitos mínimos recomendados
apiVersion: v1
kind: Namespace
metadata:
  name: vortex-system
  labels:
    name: vortex-system
    environment: production
```

**Especificações de Cluster:**
- **Nodes**: Mínimo 3 nodes (1 master + 2 workers)
- **CPU**: 8 vCPUs por node
- **Memória**: 16GB RAM por node
- **Armazenamento**: 100GB SSD por node
- **Versão**: Kubernetes 1.28+

#### **1.2 Ferramentas Necessárias**
```bash
# Ferramentas essenciais
kubectl           # Cliente Kubernetes
helm              # Gerenciador de pacotes
kustomize         # Personalização de manifests
istio             # Service mesh (opcional)
prometheus        # Monitoramento
grafana           # Visualização
jaeger            # Tracing distribuído
```

### 2. **Configuração de Persistência**

#### **2.1 Storage Classes**
```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: vortex-ssd
  namespace: vortex-system
provisioner: kubernetes.io/aws-ebs
parameters:
  type: gp3
  fsType: ext4
reclaimPolicy: Retain
allowVolumeExpansion: true
```

#### **2.2 Persistent Volumes para Oracle**
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: oracle-pvc
  namespace: vortex-system
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: vortex-ssd
  resources:
    requests:
      storage: 50Gi
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: kafka-pvc
  namespace: vortex-system
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: vortex-ssd
  resources:
    requests:
      storage: 20Gi
```

### 3. **Gerenciamento de Configurações**

#### **3.1 ConfigMaps**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: vortex-app-config
  namespace: vortex-system
data:
  application.properties: |
    spring.profiles.active=prd,kubernetes
    spring.jpa.hibernate.ddl-auto=validate
    spring.datasource.url=jdbc:oracle:thin:@oracle-service:1521:ORCLCDB
    spring.kafka.bootstrap-servers=kafka-service:9092
    spring.kafka.consumer.group-id=vortex-inventory-group
    management.endpoints.web.exposure.include=health,metrics,prometheus
    management.endpoint.health.show-details=always
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: vortex-auth-config
  namespace: vortex-system
data:
  application.properties: |
    quarkus.profile=kubernetes
    quarkus.http.port=8081
    quarkus.datasource.jdbc.url=jdbc:h2:mem:authdb
    quarkus.jwt.public-key.location=privatekey.pem
    quarkus.oidc.enabled=true
    quarkus.smallrye-health.root-path=/q/health
```

#### **3.2 Secrets**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: vortex-secrets
  namespace: vortex-system
type: Opaque
data:
  oracle-password: T3JhY2xlXzEyMzQ=  # Oracle_1234 em base64
  kafka-password: a2Fma2ExMjM=       # kafka123 em base64
  jwt-secret: dm9ydGV4LWp3dC1zZWNyZXQtc3VwZXItc2VjdXJlLWtleQ==
---
apiVersion: v1
kind: Secret
metadata:
  name: oracle-db-secret
  namespace: vortex-system
type: Opaque
data:
  oracle-sid: T1JDTENEQg==          # ORCLCDB
  oracle-pdb: T1JDTFBEQjE=          # ORCLPDB1
  oracle-user: c3lzdGVt              # system
```

### 4. **Deployments dos Microsserviços**

#### **4.1 Oracle Database**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: oracle-db
  namespace: vortex-system
  labels:
    app: oracle-db
    tier: database
spec:
  replicas: 1
  selector:
    matchLabels:
      app: oracle-db
  template:
    metadata:
      labels:
        app: oracle-db
        tier: database
    spec:
      containers:
      - name: oracle
        image: container-registry.oracle.com/database/enterprise:latest
        ports:
        - containerPort: 1521
        - containerPort: 5500
        env:
        - name: ORACLE_SID
          valueFrom:
            secretKeyRef:
              name: oracle-db-secret
              key: oracle-sid
        - name: ORACLE_PDB
          valueFrom:
            secretKeyRef:
              name: oracle-db-secret
              key: oracle-pdb
        - name: ORACLE_PWD
          valueFrom:
            secretKeyRef:
              name: vortex-secrets
              key: oracle-password
        volumeMounts:
        - name: oracle-storage
          mountPath: /opt/oracle/oradata
        - name: oracle-init
          mountPath: /opt/oracle/scripts/startup
        resources:
          requests:
            memory: "4Gi"
            cpu: "2"
          limits:
            memory: "8Gi"
            cpu: "4"
        livenessProbe:
          exec:
            command: ["lsnrctl", "status"]
          initialDelaySeconds: 120
          periodSeconds: 30
          timeoutSeconds: 10
        readinessProbe:
          exec:
            command: ["lsnrctl", "status"]
          initialDelaySeconds: 60
          periodSeconds: 15
          timeoutSeconds: 5
      volumes:
      - name: oracle-storage
        persistentVolumeClaim:
          claimName: oracle-pvc
      - name: oracle-init
        configMap:
          name: oracle-init-scripts
---
apiVersion: v1
kind: Service
metadata:
  name: oracle-service
  namespace: vortex-system
spec:
  selector:
    app: oracle-db
  ports:
  - name: oracle-port
    port: 1521
    targetPort: 1521
  - name: oracle-em
    port: 5500
    targetPort: 5500
  type: ClusterIP
```

#### **4.2 Apache Kafka**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: zookeeper
  namespace: vortex-system
spec:
  replicas: 1
  selector:
    matchLabels:
      app: zookeeper
  template:
    metadata:
      labels:
        app: zookeeper
    spec:
      containers:
      - name: zookeeper
        image: confluentinc/cp-zookeeper:7.4.0
        ports:
        - containerPort: 2181
        env:
        - name: ZOOKEEPER_CLIENT_PORT
          value: "2181"
        - name: ZOOKEEPER_TICK_TIME
          value: "2000"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: zookeeper-service
  namespace: vortex-system
spec:
  selector:
    app: zookeeper
  ports:
  - port: 2181
    targetPort: 2181
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kafka
  namespace: vortex-system
spec:
  replicas: 3
  selector:
    matchLabels:
      app: kafka
  template:
    metadata:
      labels:
        app: kafka
    spec:
      containers:
      - name: kafka
        image: confluentinc/cp-kafka:7.4.0
        ports:
        - containerPort: 9092
        - containerPort: 29092
        env:
        - name: KAFKA_BROKER_ID
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: KAFKA_ZOOKEEPER_CONNECT
          value: "zookeeper-service:2181"
        - name: KAFKA_LISTENER_SECURITY_PROTOCOL_MAP
          value: "PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT"
        - name: KAFKA_ADVERTISED_LISTENERS
          value: "PLAINTEXT://kafka-service:29092,PLAINTEXT_HOST://localhost:9092"
        - name: KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR
          value: "3"
        - name: KAFKA_AUTO_CREATE_TOPICS_ENABLE
          value: "true"
        volumeMounts:
        - name: kafka-storage
          mountPath: /var/lib/kafka/data
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1"
      volumes:
      - name: kafka-storage
        persistentVolumeClaim:
          claimName: kafka-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: kafka-service
  namespace: vortex-system
spec:
  selector:
    app: kafka
  ports:
  - name: kafka-internal
    port: 29092
    targetPort: 29092
  - name: kafka-external
    port: 9092
    targetPort: 9092
  type: ClusterIP
```

#### **4.3 Vortex Authorization Service**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vortex-auth-service
  namespace: vortex-system
  labels:
    app: vortex-auth-service
    tier: backend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: vortex-auth-service
  template:
    metadata:
      labels:
        app: vortex-auth-service
        tier: backend
    spec:
      containers:
      - name: vortex-auth
        image: vortex/auth-service:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8081
        env:
        - name: QUARKUS_PROFILE
          value: "kubernetes"
        - name: QUARKUS_HTTP_PORT
          value: "8081"
        volumeMounts:
        - name: auth-config
          mountPath: /deployments/config
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 15
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8081
          initialDelaySeconds: 10
          periodSeconds: 5
      volumes:
      - name: auth-config
        configMap:
          name: vortex-auth-config
---
apiVersion: v1
kind: Service
metadata:
  name: vortex-auth-service
  namespace: vortex-system
spec:
  selector:
    app: vortex-auth-service
  ports:
  - port: 8081
    targetPort: 8081
  type: ClusterIP
```

#### **4.4 Vortex Application Service**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vortex-app-service
  namespace: vortex-system
  labels:
    app: vortex-app-service
    tier: backend
spec:
  replicas: 3
  selector:
    matchLabels:
      app: vortex-app-service
  template:
    metadata:
      labels:
        app: vortex-app-service
        tier: backend
    spec:
      initContainers:
      - name: wait-for-db
        image: busybox:1.35
        command: ['sh', '-c', 'until nc -z oracle-service 1521; do echo waiting for oracle; sleep 2; done;']
      - name: wait-for-kafka
        image: busybox:1.35
        command: ['sh', '-c', 'until nc -z kafka-service 29092; do echo waiting for kafka; sleep 2; done;']
      containers:
      - name: vortex-app
        image: vortex/app-service:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prd,kubernetes"
        - name: DB_HOST
          value: "oracle-service"
        - name: DB_PORT
          value: "1521"
        - name: DB_USER
          valueFrom:
            secretKeyRef:
              name: oracle-db-secret
              key: oracle-user
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: vortex-secrets
              key: oracle-password
        - name: KAFKA_ENABLED
          value: "true"
        - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
          value: "kafka-service:29092"
        volumeMounts:
        - name: app-config
          mountPath: /app/config
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 20
        readinessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
      volumes:
      - name: app-config
        configMap:
          name: vortex-app-config
---
apiVersion: v1
kind: Service
metadata:
  name: vortex-app-service
  namespace: vortex-system
spec:
  selector:
    app: vortex-app-service
  ports:
  - port: 8080
    targetPort: 8080
  type: ClusterIP
```

#### **4.5 Frontend Applications**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vortex-main-frontend
  namespace: vortex-system
  labels:
    app: vortex-main-frontend
    tier: frontend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: vortex-main-frontend
  template:
    metadata:
      labels:
        app: vortex-main-frontend
        tier: frontend
    spec:
      containers:
      - name: vortex-frontend
        image: vortex/main-frontend:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 80
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "200m"
        livenessProbe:
          httpGet:
            path: /
            port: 80
          initialDelaySeconds: 15
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /
            port: 80
          initialDelaySeconds: 5
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: vortex-main-frontend-service
  namespace: vortex-system
spec:
  selector:
    app: vortex-main-frontend
  ports:
  - port: 80
    targetPort: 80
  type: ClusterIP
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vortex-auth-frontend
  namespace: vortex-system
  labels:
    app: vortex-auth-frontend
    tier: frontend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: vortex-auth-frontend
  template:
    metadata:
      labels:
        app: vortex-auth-frontend
        tier: frontend
    spec:
      containers:
      - name: auth-frontend
        image: vortex/auth-frontend:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 80
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "200m"
---
apiVersion: v1
kind: Service
metadata:
  name: vortex-auth-frontend-service
  namespace: vortex-system
spec:
  selector:
    app: vortex-auth-frontend
  ports:
  - port: 80
    targetPort: 80
  type: ClusterIP
```

### 5. **Exposição dos Serviços**

#### **5.1 Ingress Controller**
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: vortex-ingress
  namespace: vortex-system
  annotations:
    kubernetes.io/ingress.class: "nginx"
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/proxy-body-size: "10m"
    nginx.ingress.kubernetes.io/cors-allow-origin: "*"
    nginx.ingress.kubernetes.io/cors-allow-methods: "GET, POST, PUT, DELETE, OPTIONS"
    nginx.ingress.kubernetes.io/cors-allow-headers: "DNT,X-CustomHeader,Keep-Alive,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Authorization"
spec:
  tls:
  - hosts:
    - vortex.yourdomain.com
    - auth.vortex.yourdomain.com
    - api.vortex.yourdomain.com
    secretName: vortex-tls
  rules:
  - host: vortex.yourdomain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: vortex-main-frontend-service
            port:
              number: 80
  - host: auth.vortex.yourdomain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: vortex-auth-frontend-service
            port:
              number: 80
  - host: api.vortex.yourdomain.com
    http:
      paths:
      - path: /api
        pathType: Prefix
        backend:
          service:
            name: vortex-app-service
            port:
              number: 8080
      - path: /auth
        pathType: Prefix
        backend:
          service:
            name: vortex-auth-service
            port:
              number: 8081
```

### 6. **Monitoramento e Observabilidade**

#### **6.1 Prometheus e Grafana**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: vortex-system
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
    
    scrape_configs:
    - job_name: 'vortex-app-service'
      static_configs:
      - targets: ['vortex-app-service:8080']
      metrics_path: '/actuator/prometheus'
    
    - job_name: 'vortex-auth-service'
      static_configs:
      - targets: ['vortex-auth-service:8081']
      metrics_path: '/q/metrics'
    
    - job_name: 'kubernetes-pods'
      kubernetes_sd_configs:
      - role: pod
        namespaces:
          names: ['vortex-system']
      relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prometheus
  namespace: vortex-system
spec:
  replicas: 1
  selector:
    matchLabels:
      app: prometheus
  template:
    metadata:
      labels:
        app: prometheus
    spec:
      containers:
      - name: prometheus
        image: prom/prometheus:v2.40.0
        ports:
        - containerPort: 9090
        volumeMounts:
        - name: prometheus-config
          mountPath: /etc/prometheus
        - name: prometheus-storage
          mountPath: /prometheus
        args:
        - '--config.file=/etc/prometheus/prometheus.yml'
        - '--storage.tsdb.path=/prometheus'
        - '--web.console.libraries=/etc/prometheus/console_libraries'
        - '--web.console.templates=/etc/prometheus/consoles'
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1"
      volumes:
      - name: prometheus-config
        configMap:
          name: prometheus-config
      - name: prometheus-storage
        emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: prometheus-service
  namespace: vortex-system
spec:
  selector:
    app: prometheus
  ports:
  - port: 9090
    targetPort: 9090
```

#### **6.2 Logging com ELK Stack**
```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: fluentd
  namespace: vortex-system
spec:
  selector:
    matchLabels:
      name: fluentd
  template:
    metadata:
      labels:
        name: fluentd
    spec:
      containers:
      - name: fluentd
        image: fluent/fluentd-kubernetes-daemonset:v1.14.6-debian-elasticsearch7-1.0
        env:
        - name: FLUENT_ELASTICSEARCH_HOST
          value: "elasticsearch-service"
        - name: FLUENT_ELASTICSEARCH_PORT
          value: "9200"
        volumeMounts:
        - name: varlog
          mountPath: /var/log
        - name: varlibdockercontainers
          mountPath: /var/lib/docker/containers
          readOnly: true
      volumes:
      - name: varlog
        hostPath:
          path: /var/log
      - name: varlibdockercontainers
        hostPath:
          path: /var/lib/docker/containers
```

### 7. **Autoscaling e Alta Disponibilidade**

#### **7.1 Horizontal Pod Autoscaler**
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: vortex-app-hpa
  namespace: vortex-system
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: vortex-app-service
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
      - type: Percent
        value: 100
        periodSeconds: 15
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: vortex-auth-hpa
  namespace: vortex-system
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: vortex-auth-service
  minReplicas: 2
  maxReplicas: 6
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

#### **7.2 Pod Disruption Budgets**
```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: vortex-app-pdb
  namespace: vortex-system
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app: vortex-app-service
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: vortex-auth-pdb
  namespace: vortex-system
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: vortex-auth-service
```

### 8. **Segurança**

#### **8.1 Network Policies**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: vortex-network-policy
  namespace: vortex-system
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    - podSelector:
        matchLabels:
          tier: frontend
    - podSelector:
        matchLabels:
          tier: backend
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: oracle-db
    ports:
    - protocol: TCP
      port: 1521
  - to:
    - podSelector:
        matchLabels:
          app: kafka
    ports:
    - protocol: TCP
      port: 29092
  - to: []
    ports:
    - protocol: TCP
      port: 53
    - protocol: UDP
      port: 53
```

#### **8.2 Service Accounts e RBAC**
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: vortex-service-account
  namespace: vortex-system
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: vortex-role
  namespace: vortex-system
rules:
- apiGroups: [""]
  resources: ["pods", "services", "configmaps", "secrets"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["apps"]
  resources: ["deployments", "replicasets"]
  verbs: ["get", "list", "watch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: vortex-role-binding
  namespace: vortex-system
subjects:
- kind: ServiceAccount
  name: vortex-service-account
  namespace: vortex-system
roleRef:
  kind: Role
  name: vortex-role
  apiGroup: rbac.authorization.k8s.io
```

### 9. **Backup e Disaster Recovery**

#### **9.1 Backup Strategy**
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: oracle-backup
  namespace: vortex-system
spec:
  schedule: "0 2 * * *"  # Daily at 2 AM
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: oracle-backup
            image: oracle/database:19.3.0-ee
            command:
            - /bin/bash
            - -c
            - |
              expdp system/Oracle_1234@oracle-service:1521/ORCLPDB1 \
                directory=backup_dir \
                dumpfile=vortex_backup_$(date +%Y%m%d).dmp \
                logfile=vortex_backup_$(date +%Y%m%d).log \
                schemas=VORTEX
            volumeMounts:
            - name: backup-storage
              mountPath: /opt/oracle/backup
          volumes:
          - name: backup-storage
            persistentVolumeClaim:
              claimName: backup-pvc
          restartPolicy: OnFailure
```

#### **9.2 Velero Backup Configuration**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: velero-backup-config
  namespace: vortex-system
data:
  backup-schedule.yaml: |
    apiVersion: velero.io/v1
    kind: Schedule
    metadata:
      name: vortex-daily-backup
      namespace: velero
    spec:
      schedule: "0 1 * * *"
      template:
        includedNamespaces:
        - vortex-system
        storageLocation: default
        volumeSnapshotLocations:
        - default
        ttl: "720h"  # 30 days
```

## 🚀 Procedimentos de Deploy

### 1. **Preparação do Ambiente**

#### **1.1 Build das Imagens**
```bash
# Construir imagem do backend principal
cd backend/vortex-application-service
docker build -t vortex/app-service:latest .

# Construir imagem do serviço de autorização
cd ../vortex-authorization-service
docker build -t vortex/auth-service:latest .

# Construir imagem do frontend principal
cd ../../frontend/vortex-application-service
docker build -t vortex/main-frontend:latest .

# Construir imagem do frontend de autorização
cd ../vortex-authorization-service
docker build -t vortex/auth-frontend:latest .

# Push para registry (exemplo com Docker Hub)
docker tag vortex/app-service:latest your-registry/vortex/app-service:latest
docker push your-registry/vortex/app-service:latest

docker tag vortex/auth-service:latest your-registry/vortex/auth-service:latest
docker push your-registry/vortex/auth-service:latest

docker tag vortex/main-frontend:latest your-registry/vortex/main-frontend:latest
docker push your-registry/vortex/main-frontend:latest

docker tag vortex/auth-frontend:latest your-registry/vortex/auth-frontend:latest
docker push your-registry/vortex/auth-frontend:latest
```

#### **1.2 Preparar Cluster**
```bash
# Criar namespace
kubectl create namespace vortex-system

# Aplicar labels
kubectl label namespace vortex-system environment=production

# Instalar ingress controller (NGINX)
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm install ingress-nginx ingress-nginx/ingress-nginx --namespace ingress-nginx --create-namespace

# Instalar cert-manager para SSL
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml
```

### 2. **Deploy Sequencial**

#### **2.1 Infraestrutura Base**
```bash
# Aplicar storage classes e PVCs
kubectl apply -f k8s/storage/

# Deploy do Oracle Database
kubectl apply -f k8s/database/oracle-deployment.yaml
kubectl wait --for=condition=ready pod -l app=oracle-db --timeout=300s

# Deploy do Kafka
kubectl apply -f k8s/messaging/zookeeper.yaml
kubectl wait --for=condition=ready pod -l app=zookeeper --timeout=120s

kubectl apply -f k8s/messaging/kafka.yaml
kubectl wait --for=condition=ready pod -l app=kafka --timeout=180s
```

#### **2.2 Serviços de Aplicação**
```bash
# Deploy do serviço de autorização
kubectl apply -f k8s/services/auth-service.yaml
kubectl wait --for=condition=ready pod -l app=vortex-auth-service --timeout=120s

# Deploy do serviço principal
kubectl apply -f k8s/services/app-service.yaml
kubectl wait --for=condition=ready pod -l app=vortex-app-service --timeout=180s

# Deploy dos frontends
kubectl apply -f k8s/frontend/
kubectl wait --for=condition=ready pod -l tier=frontend --timeout=120s
```

#### **2.3 Configurações de Rede**
```bash
# Aplicar ingress
kubectl apply -f k8s/ingress/vortex-ingress.yaml

# Aplicar network policies
kubectl apply -f k8s/security/network-policies.yaml

# Configurar autoscaling
kubectl apply -f k8s/autoscaling/
```

### 3. **Validação do Deploy**

#### **3.1 Verificação de Pods**
```bash
# Verificar status dos pods
kubectl get pods -n vortex-system

# Verificar logs
kubectl logs -n vortex-system deployment/vortex-app-service
kubectl logs -n vortex-system deployment/vortex-auth-service

# Verificar health checks
kubectl get endpoints -n vortex-system
```

#### **3.2 Testes de Conectividade**
```bash
# Teste interno de conectividade
kubectl run test-pod --image=busybox -n vortex-system --rm -it -- /bin/sh

# Dentro do pod de teste:
nslookup oracle-service
nslookup kafka-service
nslookup vortex-app-service
nslookup vortex-auth-service

# Teste de APIs
curl http://vortex-app-service:8080/health
curl http://vortex-auth-service:8081/q/health
```

#### **3.3 Monitoramento**
```bash
# Verificar métricas
kubectl top pods -n vortex-system
kubectl top nodes

# Verificar HPA
kubectl get hpa -n vortex-system

# Verificar eventos
kubectl get events -n vortex-system --sort-by='.lastTimestamp'
```

## 📊 Vantagens da Migração para Kubernetes

### **1. Escalabilidade Automática**
- **HPA**: Escala horizontal baseado em CPU/memória
- **VPA**: Escala vertical de recursos
- **Cluster Autoscaler**: Adiciona/remove nós conforme demanda

### **2. Alta Disponibilidade**
- **Multi-AZ**: Distribui pods entre zonas de disponibilidade
- **Pod Disruption Budgets**: Garante disponibilidade durante manutenções
- **Health Checks**: Monitora saúde dos serviços automaticamente

### **3. Gerenciamento de Configuração**
- **ConfigMaps**: Configurações externalizadas
- **Secrets**: Gerenciamento seguro de credenciais
- **Environment-specific**: Diferentes configs por ambiente

### **4. Service Discovery**
- **DNS Interno**: Resolução automática de nomes de serviços
- **Load Balancing**: Distribuição automática de carga
- **Service Mesh**: Comunicação segura entre serviços (Istio)

### **5. Observabilidade**
- **Prometheus**: Métricas centralizadas
- **Grafana**: Dashboards visuais
- **Jaeger**: Tracing distribuído
- **ELK Stack**: Logs centralizados

### **6. DevOps e CI/CD**
- **GitOps**: Deploy via Git (ArgoCD, Flux)
- **Blue/Green**: Deploy sem downtime
- **Canary**: Deploy gradual
- **Rollback**: Reversão rápida

### **7. Isolamento e Segurança**
- **Namespaces**: Isolamento lógico
- **Network Policies**: Controle de tráfego
- **RBAC**: Controle de acesso granular
- **Pod Security Standards**: Políticas de segurança

## 🛠️ Ferramentas Complementares

### **1. Helm Charts**
```bash
# Estrutura de Helm Chart para Vortex
vortex-helm/
├── Chart.yaml
├── values.yaml
├── values-prod.yaml
├── values-staging.yaml
└── templates/
    ├── deployment.yaml
    ├── service.yaml
    ├── ingress.yaml
    ├── configmap.yaml
    └── secrets.yaml
```

### **2. Kustomize**
```bash
# Estrutura com Kustomize
k8s/
├── base/
│   ├── kustomization.yaml
│   ├── deployment.yaml
│   └── service.yaml
├── overlays/
│   ├── production/
│   │   ├── kustomization.yaml
│   │   └── patches/
│   └── staging/
│       ├── kustomization.yaml
│       └── patches/
```

### **3. ArgoCD para GitOps**
```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: vortex-app
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/your-org/vortex-k8s
    targetRevision: HEAD
    path: k8s/overlays/production
  destination:
    server: https://kubernetes.default.svc
    namespace: vortex-system
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

## 📈 Métricas e KPIs

### **1. Performance**
- **Response Time**: < 200ms (P95)
- **Throughput**: > 1000 req/s
- **Availability**: 99.9% uptime
- **Error Rate**: < 0.1%

### **2. Recursos**
- **CPU Utilization**: 60-80%
- **Memory Utilization**: 70-85%
- **Disk I/O**: < 80%
- **Network**: < 70%

### **3. Negócio**
- **Cost per Transaction**: Redução de 30%
- **Time to Market**: Redução de 50%
- **MTTR**: < 15 minutos
- **MTBF**: > 30 dias

## 🔧 Manutenção e Operação

### **1. Atualizações**
```bash
# Rolling update
kubectl set image deployment/vortex-app-service vortex-app=vortex/app-service:v2.0.0 -n vortex-system

# Verificar rollout
kubectl rollout status deployment/vortex-app-service -n vortex-system

# Rollback se necessário
kubectl rollout undo deployment/vortex-app-service -n vortex-system
```

### **2. Backup e Restore**
```bash
# Backup completo com Velero
velero backup create vortex-backup --include-namespaces vortex-system

# Restore
velero restore create --from-backup vortex-backup
```

### **3. Troubleshooting**
```bash
# Debug de pods
kubectl describe pod <pod-name> -n vortex-system
kubectl logs <pod-name> -n vortex-system --previous

# Debug de rede
kubectl exec -it <pod-name> -n vortex-system -- nslookup <service-name>
kubectl exec -it <pod-name> -n vortex-system -- ping <service-name>

# Debug de recursos
kubectl top pods -n vortex-system
kubectl get events -n vortex-system --sort-by='.lastTimestamp'
```

## 🎯 Conclusão

A migração do projeto Vortex para Kubernetes oferece benefícios significativos em termos de escalabilidade, disponibilidade, e operação. A arquitetura apresentada aproveita as melhores práticas do ecossistema Kubernetes, mantendo a compatibilidade com a estrutura atual do projeto.

### **Próximos Passos Recomendados:**

1. **Fase 1**: Implementar ambiente de desenvolvimento em Kubernetes
2. **Fase 2**: Migrar ambiente de staging
3. **Fase 3**: Deploy gradual em produção com blue/green
4. **Fase 4**: Implementar service mesh e observabilidade avançada
5. **Fase 5**: Otimizar custos e performance

### **Considerações Importantes:**

- **Treinamento**: Equipe deve estar familiarizada com Kubernetes
- **Monitoramento**: Implementar observabilidade desde o início
- **Segurança**: Aplicar políticas de segurança rigorosas
- **Backup**: Estratégia de backup robusta é essencial
- **Testes**: Testes extensivos em ambiente de staging

Esta implementação garante que o sistema Vortex seja resiliente, escalável e facilmente manutenível em um ambiente de produção moderno.