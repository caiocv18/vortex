# 🏗️ Infraestrutura Vortex

Esta pasta contém todos os componentes de infraestrutura do projeto Vortex, organizados de forma modular e reutilizável. Todos os arquivos usam o **Docker Compose v2** (`docker compose`, sem hífen), toda imagem tem versão fixa, todo serviço tem `mem_limit` e `restart: "no"`, e todo container se chama `vortex-...`.

## 📁 Estrutura

```
infra/
├── docker/                 # Configurações Docker Compose
│   ├── docker-compose.yml             # Backend + Oracle Free (ambiente prd do launcher)
│   ├── docker-compose.override.yml    # Overrides locais (aplicado junto quando não há -f)
│   ├── docker-compose.leve.yml        # nginx estático do modo leve (vortex-static)
│   ├── docker-compose.auth.yml        # PostgreSQL + backend e frontend de autorização
│   ├── docker-compose.full.yml        # Stack completa (Backend + Oracle + Frontend)
│   ├── docker-compose.full-kafka.yml  # Stack completa com Kafka integrado
│   ├── docker-compose.full-rabbitmq.yml # Stack completa com RabbitMQ integrado
│   ├── docker-compose.kafka.yml       # Kafka + Confluent Platform 8.4 atrás de profiles
│   ├── docker-compose.kafka-simple.yml # Kafka nativo em KRaft (o do launcher)
│   ├── docker-compose.rabbitmq.yml    # RabbitMQ standalone
│   ├── nginx/static.conf              # Servidor estático dos dois frontends (5173 e 3001)
│   └── Dockerfile.backend             # Build do backend (JDK 27, roda na JRE 27 Alpine)
├── kafka/                  # Apache Kafka
│   ├── fix-kafka-issues.sh           # Script de correção do Kafka
│   └── application-kafka-test.properties # Configurações de teste
├── rabbitmq/               # RabbitMQ
│   ├── 20-vortex.conf                # Teto de memória, disk_free_limit e log do container
│   └── application-rabbitmq-test.properties # Configurações de teste
├── oracle/                 # Oracle Database
│   └── init/                          # Scripts de inicialização (/container-entrypoint-initdb.d)
│       ├── 01-init-schema.sql        # Usuário, privilégios e sequências
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
    └── stop-infra.sh                 # Parar toda infraestrutura (down sem -v)
```

## 🖼️ Imagens

| Serviço | Imagem | mem_limit |
|---------|--------|-----------|
| PostgreSQL (auth) | `postgres:18.6-alpine` | 256m |
| nginx estático (modo leve) | `nginx:1.30.6-alpine` | 32m |
| Kafka | `apache/kafka-native:4.4.0` (GraalVM, KRaft, sem ZooKeeper) | 384m |
| Kafka UI (profile `ui`) | `kafbat/kafka-ui:v1.5.0` | 384m |
| Kafka Connect, Schema Registry, ksqlDB, ferramentas (profiles) | `confluentinc/cp-*:8.4.0` | 384m a 768m |
| RabbitMQ | `rabbitmq:4.4.0-management-alpine` | 256m |
| Oracle | `gvenzl/oracle-free:23.26.3-slim-faststart` | 3g (o teto de 2 GB do Oracle Free cobre só SGA e PGA) |
| Backends (build) | `eclipse-temurin:27-jdk` | |
| Backends (runtime) | `eclipse-temurin:27-jre-alpine` | 768m (app) / 512m (auth) |
| Frontends (build / runtime) | `node:26.13.0-alpine` / `nginx:1.30.6-alpine` | 32m |

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

# Modo leve, o mais econômico (cerca de 620 MB no total)
./start-vortex.sh --modo leve -e dev -m none --no-interaction

# Iniciar com opções específicas
./start-vortex.sh -e prd -m kafka --logs

# Iniciar apenas aplicação principal (o serviço de autorização sobe sempre)
./start-vortex.sh --backend-only

# Parar todos os serviços
./start-vortex.sh --stop

# Corrigir problemas do Kafka
./start-vortex.sh --fix-kafka
```

## 🐳 Configurações Docker

> [!IMPORTANT]
> Os compose de prd (`docker-compose.yml`, `full`, `full-kafka` e `full-rabbitmq`) exigem `JWT_SECRET`: o backend no perfil `prd` recusa o segredo padrão. O Compose interpola o arquivo inteiro em todo comando, então sem ele até o `down` e o `up -d db` param com `required variable JWT_SECRET is missing a value`. Exporte antes: `export JWT_SECRET="$(openssl rand -base64 48)"`. O `./infra/scripts/start-oracle.sh` e o `./infra/scripts/stop-infra.sh` já cuidam disso sozinhos.
>
> Eles também montam, só leitura, a chave pública do serviço de autorização (`backend/vortex-authorization-service/src/main/resources/jwt/publicKey.pem`) em `/app/keys/auth-public-key.pem`. Ela fica fora do git; o `start-vortex.sh` gera o par quando falta. Sem o arquivo, o `up` falha com o caminho, em vez de criar uma pasta no lugar.

### Backend + Oracle (Produção)
```bash
cd infra/docker
docker compose up -d
```

O `vortex-app` entra também nas redes `vortex-simple` (Kafka do `docker-compose.kafka-simple.yml`) e `vortex-rabbitmq-network` (RabbitMQ do `docker-compose.rabbitmq.yml`); o launcher liga um ou outro com `KAFKA_ENABLED` e `RABBITMQ_ENABLED`.

### Stack Completa
```bash
cd infra/docker
docker compose -f docker-compose.full.yml up -d
```

### Stack com Kafka Integrado
```bash
cd infra/docker
docker compose -f docker-compose.full-kafka.yml up -d
```

### Stack com RabbitMQ Integrado
```bash
cd infra/docker
docker compose -f docker-compose.full-rabbitmq.yml up -d
```

### Apenas Serviços de Autenticação
```bash
cd infra/docker
docker compose -f docker-compose.auth.yml up -d
```

O PostgreSQL 18 guarda os dados no volume `vortex-auth-db-data-pg18`. O volume antigo `docker_auth-db-data` tem um cluster do PostgreSQL 17 na raiz, e a imagem 18 se recusa a subir em cima dele; ele nunca é apagado. Para trazer os usuários dele, com o Vortex parado: `bash scripts/migrar-auth-db-pg18.sh` (pg_dump do 17 numa cópia do volume e restauração no 18 numa transação só).

### Apenas Kafka
```bash
cd infra/docker
docker compose -f docker-compose.kafka-simple.yml up -d kafka
```

### Apenas RabbitMQ
```bash
cd infra/docker
docker compose -f docker-compose.rabbitmq.yml up -d rabbitmq
```

### nginx estático do modo leve (precisa dos dist/ compilados)
```bash
cd infra/docker
docker compose -f docker-compose.leve.yml up -d vortex-static
```

As redes `vortex-simple`, `vortex-kafka-network` e `vortex-rabbitmq-network` são `external`: o `start-vortex.sh` e os scripts de `infra/scripts` criam as três quando faltam (`docker network create <nome>`).

## 📨 Kafka

### Configuração Simplificada (Recomendada)
- Arquivo: `docker-compose.kafka-simple.yml`
- Broker: `localhost:9092` (host) e `kafka:19092` (containers na rede `vortex-simple`)
- KRaft de nó único: o mesmo processo é broker e controller; não há ZooKeeper (a porta 2181 saiu)
- Kafka UI: http://localhost:8090, só com `--profile ui`
- Dados na camada do container: somem no `docker compose down`

### Tópicos
Criados pelo próprio backend (`KafkaConfig`) e por auto-create no broker:
- `vortex.movimento.estoque` - Movimentações de estoque
- `vortex.produto.events` - Eventos de produtos
- `vortex.alertas.estoque` - Alertas de estoque baixo
- `vortex.relatorios.events` - Eventos de relatórios
- `vortex.auditoria` - Eventos de auditoria

### Comandos Úteis
A imagem nativa não traz as CLIs do Kafka; use a imagem JVM numa execução avulsa:
```bash
# Listar tópicos
docker run --rm --network vortex-simple apache/kafka:4.4.0 \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --list

# Consumer groups
docker run --rm --network vortex-simple apache/kafka:4.4.0 \
  /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:19092 --list
```

Com o `docker-compose.kafka.yml` (rede `vortex-kafka-network`), as CLIs ficam no container do profile `tools`: `docker exec vortex-kafka-tools kafka-topics --bootstrap-server kafka:19092 --list`.

## 🐰 RabbitMQ

### Configuração Padrão
- Arquivo: `docker-compose.rabbitmq.yml` (mais `rabbitmq/20-vortex.conf`)
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
- Imagem: `gvenzl/oracle-free:23.26.3-slim-faststart` (Oracle Database Free 23ai, sem login no registry)
- Porta: 1521
- Serviço (PDB): FREEPDB1
- Usuário da aplicação: vortex_user / vortex_password (`APP_USER`)
- SYS e SYSTEM: senha de `ORACLE_PASSWORD` (padrão Oracle_1234)
- Volume: `vortex-oracle-data`

### Scripts de Inicialização
Os scripts em `oracle/init/` são executados automaticamente na primeira inicialização do volume:
1. `01-init-schema.sql` - Usuário, privilégios e sequências
2. `02-insert-data.sql` - Dados iniciais

Detalhes em [docs/README-ORACLE.md](docs/README-ORACLE.md).

## 🔧 Troubleshooting

### Kafka não inicia
```bash
# Executar correção automática (recria o container; nenhum volume é apagado)
./infra/kafka/fix-kafka-issues.sh

# Ou usar o script principal
./start-vortex.sh --fix-kafka
```

### Oracle demora para iniciar
- A primeira subida baixa a imagem e copia o banco pronto para o volume
- Verifique logs: `docker logs vortex-db -f`
- Aguarde o healthcheck ficar "healthy"

### Portas ocupadas
```bash
# Quem escuta em cada porta (processos do host)
lsof -iTCP:8080 -sTCP:LISTEN

# Portas publicadas por containers
docker ps --format 'table {{.Names}}\t{{.Ports}}'

# Parar todos os serviços
./start-vortex.sh --stop
```

## 🎯 Próximos Passos

- [ ] Implementar configuração SQS
- [ ] Adicionar monitoramento (Prometheus/Grafana)
- [ ] Configurar backup automático Oracle
- [ ] Implementar health checks avançados
- [ ] Adicionar SSL/TLS para produção
