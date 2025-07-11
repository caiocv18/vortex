# 🏗️ Infraestrutura Vortex

Esta pasta contém todos os componentes de infraestrutura do projeto Vortex, organizados de forma modular e reutilizável.

## 📁 Estrutura

```
infra/
├── docker/                 # Configurações Docker Compose
│   ├── docker-compose.yml             # Backend + Oracle
│   ├── docker-compose.override.yml    # Overrides locais
│   ├── docker-compose.full.yml        # Stack completa (Backend + Oracle + Frontend)
│   ├── docker-compose.full-kafka.yml  # Stack completa com Kafka integrado
│   ├── docker-compose.full-rabbitmq.yml # Stack completa com RabbitMQ integrado
│   ├── docker-compose.kafka.yml       # Kafka (configuração legada)
│   ├── docker-compose.kafka-simple.yml # Kafka (configuração simplificada)
│   ├── docker-compose.rabbitmq.yml    # RabbitMQ standalone
│   └── Dockerfile.backend             # Build do backend
├── kafka/                  # Apache Kafka
│   ├── fix-kafka-issues.sh           # Script de correção do Kafka
│   └── application-kafka-test.properties # Configurações de teste
├── rabbitmq/               # RabbitMQ
│   └── application-rabbitmq-test.properties # Configurações de teste
├── oracle/                 # Oracle Database
│   └── init/                          # Scripts de inicialização
│       ├── 01-init-schema.sql        # Criação do schema
│       └── 02-insert-data.sql        # Dados iniciais
├── sqs/                   # Amazon SQS (futuro)
├── docs/                  # Documentação de infraestrutura
│   ├── README-KAFKA.md               # Documentação do Kafka
│   ├── README-RABBITMQ.md            # Documentação do RabbitMQ
│   ├── README-ORACLE.md              # Documentação do Oracle
│   └── README-SQS.md                 # Documentação do SQS
└── scripts/               # Scripts de gerenciamento
    ├── start-kafka.sh                # Iniciar apenas Kafka
    ├── start-rabbitmq.sh             # Iniciar apenas RabbitMQ
    ├── start-oracle.sh               # Iniciar apenas Oracle
    └── stop-infra.sh                 # Parar toda infraestrutura
```

## 🚀 Scripts Disponíveis

### Scripts Específicos

```bash
# Iniciar apenas Kafka
./infra/scripts/start-kafka.sh

# Iniciar apenas RabbitMQ
./infra/scripts/start-rabbitmq.sh

# Iniciar apenas Oracle
./infra/scripts/start-oracle.sh

# Parar toda infraestrutura
./infra/scripts/stop-infra.sh
```

### Script Principal (na raiz)

```bash
# Iniciar sistema completo (interativo) - inclui autenticação
./start-vortex.sh

# Iniciar com opções específicas
./start-vortex.sh -e prd -m kafka --logs

# Iniciar apenas serviços de autenticação
./start-vortex.sh --auth-only

# Iniciar apenas aplicação principal (sem auth)
./start-vortex.sh --backend-only

# Parar todos os serviços
./start-vortex.sh --stop

# Corrigir problemas do Kafka
./start-vortex.sh --fix-kafka
```

## 🐳 Configurações Docker

### Backend + Oracle (Produção)
```bash
cd infra/docker
docker-compose up -d
```

### Stack Completa (com Autenticação)
```bash
cd infra/docker
docker-compose -f docker-compose.full.yml up -d
```

### Stack com Kafka Integrado (com Autenticação)
```bash
cd infra/docker
docker-compose -f docker-compose.full-kafka.yml up -d
```

### Stack com RabbitMQ Integrado (com Autenticação)
```bash
cd infra/docker
docker-compose -f docker-compose.full-rabbitmq.yml up -d
```

### Apenas Serviços de Autenticação
```bash
cd infra/docker
docker-compose -f docker-compose.auth.yml up -d
```

### Apenas Kafka
```bash
cd infra/docker
docker-compose -f docker-compose.kafka-simple.yml up -d
```

### Apenas RabbitMQ
```bash
cd infra/docker
docker-compose -f docker-compose.rabbitmq.yml up -d
```

## 📨 Kafka

### Configuração Simplificada (Recomendada)
- Arquivo: `docker-compose.kafka-simple.yml`
- Kafka UI: http://localhost:8090
- Broker: localhost:9092
- Zookeeper: localhost:2181

### Tópicos Padrão
- `movimento-estoque` - Movimentações de estoque
- `produto-events` - Eventos de produtos
- `alerta-estoque` - Alertas de estoque baixo
- `auditoria-events` - Eventos de auditoria

### Comandos Úteis
```bash
# Listar tópicos
docker exec vortex-kafka-simple kafka-topics --bootstrap-server localhost:9092 --list

# Criar tópico
docker exec vortex-kafka-simple kafka-topics --bootstrap-server localhost:9092 --create --topic meu-topico --partitions 3 --replication-factor 1

# Consumer groups
docker exec vortex-kafka-simple kafka-consumer-groups --bootstrap-server localhost:9092 --list
```

## 🐰 RabbitMQ

### Configuração Padrão
- Arquivo: `docker-compose.rabbitmq.yml`
- Management UI: http://localhost:15672
- AMQP Port: localhost:5672
- Usuário: vortex / Senha: vortex123
- Virtual Host: vortex-vhost

### Exchanges e Queues
- `vortex.movimento.exchange` - Movimentações de estoque
- `vortex.produto.exchange` - Eventos de produtos
- `vortex.alerta.exchange` - Alertas de estoque
- `vortex.auditoria.exchange` - Eventos de auditoria

### Comandos Úteis
```bash
# Listar filas
docker exec vortex-rabbitmq rabbitmqctl list_queues

# Listar exchanges
docker exec vortex-rabbitmq rabbitmqctl list_exchanges

# Listar bindings
docker exec vortex-rabbitmq rabbitmqctl list_bindings

# Status do RabbitMQ
docker exec vortex-rabbitmq rabbitmq-diagnostics status
```

## 🗄️ Oracle Database

### Configuração
- Porta: 1521
- SID: ORCLCDB
- PDB: ORCLPDB1
- Usuário: system
- Senha: Oracle_1234
- Enterprise Manager: http://localhost:5500/em

### Scripts de Inicialização
Os scripts em `oracle/init/` são executados automaticamente na primeira inicialização:
1. `01-init-schema.sql` - Criação das tabelas
2. `02-insert-data.sql` - Dados iniciais

## 🔧 Troubleshooting

### Kafka não inicia
```bash
# Executar correção automática
./infra/kafka/fix-kafka-issues.sh

# Ou usar o script principal
./start-vortex.sh --fix-kafka
```

### Oracle demora para iniciar
- Primeira inicialização pode levar 5-10 minutos
- Verifique logs: `docker logs vortex-db -f`
- Aguarde o healthcheck ficar "healthy"

### Portas ocupadas
```bash
# Verificar portas em uso
lsof -i :9092  # Kafka
lsof -i :1521  # Oracle
lsof -i :8080  # Backend

# Parar todos os serviços
./start-vortex.sh --stop
```

## 🎯 Próximos Passos

- [ ] Implementar configuração SQS
- [ ] Adicionar monitoramento (Prometheus/Grafana)
- [ ] Configurar backup automático Oracle
- [ ] Implementar health checks avançados
- [ ] Adicionar SSL/TLS para produção 