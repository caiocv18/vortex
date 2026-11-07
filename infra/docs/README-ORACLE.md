# Configuração Oracle para Produção

Este documento descreve como configurar e executar a aplicação com Oracle Database no profile de produção (`prd`).

## Pré-requisitos

- Docker Engine com o plugin Docker Compose v2 (`docker compose`)
- Nenhum login em registry: a imagem é a `gvenzl/oracle-free:23.26.3-slim-faststart`, do Docker Hub
- Cerca de 2 GB de RAM livres: o Oracle Database Free se limita a 2 GB, e o compose usa `mem_limit: 2g`

## Configuração Implementada

### 1. Profile de Produção (`application-prd.properties`)

O profile `prd` foi configurado com:
- Conexão Oracle JDBC por variáveis de ambiente (`ORACLE_DB_URL`, `ORACLE_DB_USERNAME`, `ORACLE_DB_PASSWORD`)
- URL padrão `jdbc:oracle:thin:@//localhost:1521/FREEPDB1` (serviço do PDB padrão do Oracle Free)
- Dialeto detectado pelo Hibernate 7 a partir da conexão
- Console H2 desabilitado

### 2. Imagem Oracle Database Free (gvenzl)

| Item | Valor |
|------|-------|
| Imagem | `gvenzl/oracle-free:23.26.3-slim-faststart` |
| Banco | Oracle Database Free 23ai, já criado na imagem (faststart) |
| PDB / serviço | `FREEPDB1` (no lugar do antigo `ORCLPDB1`) |
| Senha de SYS e SYSTEM | `ORACLE_PASSWORD` (padrão `Oracle_1234`) |
| Usuário da aplicação | `APP_USER=vortex_user`, criado no `FREEPDB1` na primeira subida |
| Senha da aplicação | `APP_USER_PASSWORD` (padrão `vortex_password`) |
| Volume | `vortex-oracle-data`, compartilhado pelas stacks prd |
| Healthcheck | `healthcheck.sh`, que vem na imagem |

A imagem antiga (`container-registry.oracle.com/database/enterprise`) exigia login no registry da Oracle e bem mais memória; a `express:21.3.0-xe` também saiu.

### 3. Scripts de Inicialização Oracle

Scripts em `infra/oracle/init/`, montados em `/container-entrypoint-initdb.d` (só leitura):
- `01-init-schema.sql`: usuário `vortex_user` (já criado pelo `APP_USER`; o erro ORA-01920 é ignorado), privilégios e sequências
- `02-insert-data.sql`: inserção de dados iniciais

Eles rodam **uma vez só**, na primeira inicialização do volume, como `SYS` no CDB (por isso cada um começa com `ALTER SESSION SET CONTAINER = FREEPDB1`). Erro de SQL num script não derruba o container.

> [!NOTE]
> O `02-insert-data.sql` insere em tabelas que o Hibernate só cria quando o backend sobe (`ddl-auto=update`). Na primeira inicialização do volume essas inserções falham sem derrubar nada, como já acontecia com a imagem antiga.

### 4. Docker Compose

- `infra/docker/docker-compose.yml` (+ `docker-compose.override.yml`): serviço `db` (container `vortex-db`) e backend `vortex` (container `vortex-app`)
- Stacks completas: `docker-compose.full.yml`, `docker-compose.full-kafka.yml` e `docker-compose.full-rabbitmq.yml`
- O backend recebe `ORACLE_DB_URL=jdbc:oracle:thin:@//vortex-db:1521/FREEPDB1`, `ORACLE_DB_USERNAME=vortex_user` e `ORACLE_DB_PASSWORD`

## Como Executar

### 1. Pelo launcher (recomendado)

```bash
./start-vortex.sh -e prd -m none
```

O launcher sobe o Oracle (`docker compose up -d db`), espera o log `DATABASE IS READY TO USE` e depois sobe o backend em container.

### 2. Script de conveniência

```bash
./infra/scripts/start-oracle.sh
```

### 3. Ambiente completo manual (Oracle + Aplicação)

```bash
cd infra/docker
docker compose up -d
```

### 4. Verificar logs

```bash
docker logs vortex-app -f   # aplicação
docker logs vortex-db -f    # Oracle
```

### 5. Acessar a aplicação

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

## Configurações de Conexão

### Variáveis de Ambiente (já configuradas nos compose)

- `SPRING_PROFILES_ACTIVE=prd` (o launcher acrescenta o perfil de mensageria)
- `ORACLE_DB_URL=jdbc:oracle:thin:@//vortex-db:1521/FREEPDB1`
- `ORACLE_DB_USERNAME=vortex_user`
- `ORACLE_DB_PASSWORD=vortex_password` (ou `ORACLE_APP_USER_PASSWORD`, se definido)

### String de Conexão Oracle

```
jdbc:oracle:thin:@//localhost:1521/FREEPDB1
```

## Estrutura das Tabelas

As seguintes tabelas são criadas automaticamente pelo Hibernate:

1. **tipo_produto**
   - id (SEQUENCE: tipo_produto_seq)
   - nome

2. **produto**
   - id (SEQUENCE: produto_seq)
   - descricao
   - valor_fornecedor
   - quantidade_em_estoque
   - tipo_produto_id (FK)

3. **movimento_estoque**
   - id (SEQUENCE: movimento_estoque_seq)
   - data_movimento
   - tipo_movimentacao
   - quantidade_movimentada
   - valor_venda
   - produto_id (FK)

## Dados de Teste

O `02-insert-data.sql` traz:
- 10 tipos de produto
- 20 produtos de exemplo
- 20 movimentos de estoque (10 entradas + 10 saídas)

## Troubleshooting

### Oracle não inicia

1. Verificar se há RAM livre suficiente (o container usa até 2 GB)
2. Aguardar a primeira subida (baixa a imagem e copia o banco pronto para o volume)
3. Verificar logs: `docker logs vortex-db`

### Aplicação não conecta ao Oracle

1. Verificar se o Oracle está healthy: `docker ps`
2. Testar a conexão: `docker exec vortex-db healthcheck.sh`
3. Verificar logs da aplicação: `docker logs vortex-app`

### Executar apenas Oracle (sem aplicação)

```bash
cd infra/docker
docker compose up -d db
```

### Conectar ao Oracle externamente

- Host: localhost
- Porta: 1521
- Serviço (PDB): FREEPDB1
- Usuário da aplicação: vortex_user / vortex_password
- Administrador: system / Oracle_1234 (ou o valor de `ORACLE_PASSWORD`)

```bash
docker exec -it vortex-db sqlplus vortex_user/vortex_password@//localhost:1521/FREEPDB1
```

## Desenvolvimento Local

Para executar a aplicação localmente (fora do Docker) conectando ao Oracle no Docker:

1. Subir apenas o Oracle:
```bash
cd infra/docker && docker compose up -d db
```

2. Executar a aplicação com profile prd:
```bash
cd backend/vortex-application-service
ORACLE_DB_USERNAME=vortex_user ORACLE_DB_PASSWORD=vortex_password \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=prd
```

A URL padrão do perfil `prd` já aponta para `localhost:1521/FREEPDB1`.
