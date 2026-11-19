# Configuração Oracle para Produção

Este documento descreve como configurar e executar a aplicação com Oracle Database no profile de produção (`prd`).

## Pré-requisitos

- Docker Engine com o plugin Docker Compose v2 (`docker compose`)
- Nenhum login em registry: a imagem é a `gvenzl/oracle-free:23.26.3-slim-faststart`, do Docker Hub
- Cerca de 3 GB de RAM livres. O compose usa `mem_limit: 3g`: o Oracle Database Free limita a 2 GB só a SGA e a PGA, e o container também conta a memória privada dos processos de fundo, das sessões e do listener. Medido em 07/11/2026 com o `23.26.3-slim-faststart` e o limite de 3g: `sga_target` 1536M e `pga_aggregate_target` 512M; memória que não dá para devolver (anônima mais a SGA em memória compartilhada) de 1,3 GiB ocioso e 1,5 GiB com 20 sessões dedicadas (o máximo do pool do perfil `prd`), 63 a 104 processos e nenhum OOM. Só a SGA inteira (1,5 GiB) mais a memória anônima medida com as 20 sessões (0,7 GiB) já passam dos 2 GB, então o antigo `mem_limit: 2g` não tinha folga. O resto do limite é cache de disco dos datafiles, que o kernel devolve sob pressão; por isso o `docker stats` do `vortex-db` mostra 2,2 a 2,6 GiB mesmo ocioso
- `JWT_SECRET` forte exportado (o backend no perfil `prd` recusa o padrão): `export JWT_SECRET="$(openssl rand -base64 48)"`
- `ORACLE_PASSWORD` (SYS e SYSTEM) e `ORACLE_APP_USER_PASSWORD` (`vortex_user`) fortes, exportadas antes da primeira subida do volume: os compose não têm padrão para nenhuma das duas, e o launcher e o `start-oracle.sh` recusam senha fraca com a regra do backend no perfil `prd`. `export ORACLE_PASSWORD="$(openssl rand -hex 24)" ORACLE_APP_USER_PASSWORD="$(openssl rand -hex 24)"`. Hexadecimal porque a barra do Base64 quebraria o `usuário/senha@serviço` do `sqlplus`

## Configuração Implementada

### 1. Profile de Produção (`application-prd.properties`)

O profile `prd` foi configurado com:
- Conexão Oracle JDBC por variáveis de ambiente (`ORACLE_DB_URL`, `ORACLE_DB_USERNAME`, `ORACLE_DB_PASSWORD`). `ORACLE_DB_PASSWORD` não tem padrão: sem ela o boot para antes de qualquer bean, nomeando a variável
- URL padrão `jdbc:oracle:thin:@//localhost:1521/FREEPDB1` (serviço do PDB padrão do Oracle Free)
- Dialeto detectado pelo Hibernate 7 a partir da conexão
- Console H2 desabilitado

### 2. Imagem Oracle Database Free (gvenzl)

| Item | Valor |
|------|-------|
| Imagem | `gvenzl/oracle-free:23.26.3-slim-faststart` |
| Banco | Oracle Database Free 23ai, já criado na imagem (faststart) |
| PDB / serviço | `FREEPDB1` (no lugar do antigo `ORCLPDB1`) |
| Senha de SYS e SYSTEM | `ORACLE_PASSWORD`, obrigatória e sem padrão |
| Usuário da aplicação | `APP_USER=vortex_user`, criado no `FREEPDB1` na primeira subida |
| Senha da aplicação | `APP_USER_PASSWORD`, vinda de `ORACLE_APP_USER_PASSWORD`, obrigatória e sem padrão |
| Volume | `vortex-oracle-data`, compartilhado pelas stacks prd |
| Healthcheck | `healthcheck.sh`, que vem na imagem |

A imagem antiga (`container-registry.oracle.com/database/enterprise`) exigia login no registry da Oracle e bem mais memória; a `express:21.3.0-xe` também saiu.

### 3. Scripts de Inicialização Oracle

Scripts em `infra/oracle/init/`, montados em `/container-entrypoint-initdb.d` (só leitura):
- `01-init-schema.sql`: privilégios do `vortex_user`, que a imagem cria antes, a partir de `APP_USER` e `APP_USER_PASSWORD`

Ele roda **uma vez só**, na primeira inicialização do volume, como `SYS` no CDB (por isso começa com `ALTER SESSION SET CONTAINER = FREEPDB1`). Erro de SQL no script não derruba o container.

> [!NOTE]
> Tabelas e sequences são do Hibernate (`ddl-auto=update`), criadas no schema do `vortex_user` quando o backend sobe. Até 19/11/2026 havia também um `02-insert-data.sql` com dados de exemplo e um bloco de sequences no `01`: os dois rodavam como `SYS` antes de as tabelas existirem, então os INSERTs falhavam (`ORA-00942: table or view "SYS"."TIPO_PRODUTO" does not exist`) e as sequences nasciam no schema do `SYS`, invisíveis ao `vortex_user` (`ORA-02289`). Saíram por isso; o banco Oracle começa vazio.

### 4. Docker Compose

- `infra/docker/docker-compose.yml` (+ `docker-compose.override.yml`): serviço `db` (container `vortex-db`) e backend `vortex` (container `vortex-app`)
- Stacks completas: `docker-compose.full.yml`, `docker-compose.full-kafka.yml` e `docker-compose.full-rabbitmq.yml`
- O backend recebe `ORACLE_DB_URL=jdbc:oracle:thin:@//vortex-db:1521/FREEPDB1`, `ORACLE_DB_USERNAME=vortex_user` e `ORACLE_DB_PASSWORD`
- O backend também recebe `JWT_SECRET`, e o Oracle recebe `ORACLE_PASSWORD` e `ORACLE_APP_USER_PASSWORD`, todos obrigatórios: todo compose de prd usa `${VAR:?...}`, e como o Compose interpola o arquivo inteiro, até o `docker compose up -d db` e o `down` param sem eles (`required variable ... is missing a value`)
- A chave pública do serviço de autorização é montada só leitura em `/app/keys/auth-public-key.pem` (`AUTH_JWT_PUBLIC_KEY_LOCATION`); o `start-vortex.sh` gera o par de chaves quando falta

## Como Executar

### 1. Pelo launcher (recomendado)

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
export ORACLE_PASSWORD="$(openssl rand -hex 24)"
export ORACLE_APP_USER_PASSWORD="$(openssl rand -hex 24)"
./start-vortex.sh -e prd -m none
```

O backend recusa subir no prd com senha de banco fraca (menos de 12 caracteres, só letras ou só dígitos, ou com `password` ou `admin`). A imagem do Oracle grava as duas senhas só na primeira subida do volume, então elas são exportadas antes da primeira subida e mantidas nas seguintes.

O launcher confere o `JWT_SECRET` e as duas senhas do Oracle (as mesmas regras do `SecurityConfigurationValidator`) antes de mexer em qualquer coisa e mostra todos os problemas de uma vez, sobe o Oracle (`docker compose up -d db`), espera o log `DATABASE IS READY TO USE` e depois sobe o backend em container. Se o `vortex-app` parar durante a subida, o launcher mostra as últimas linhas do log dele na hora.

### 2. Script de conveniência

```bash
export ORACLE_PASSWORD="$(openssl rand -hex 24)" ORACLE_APP_USER_PASSWORD="$(openssl rand -hex 24)"
./infra/scripts/start-oracle.sh
```

Sobe só o Oracle. Confere as duas senhas como o launcher, e o `JWT_SECRET` recebe um valor neutro, porque o banco não o usa.

### 3. Ambiente completo manual (Oracle + Aplicação)

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
export ORACLE_PASSWORD="$(openssl rand -hex 24)"            # antes da primeira subida do volume
export ORACLE_APP_USER_PASSWORD="$(openssl rand -hex 24)"   # idem
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
- `ORACLE_DB_PASSWORD`, com o valor de `ORACLE_APP_USER_PASSWORD` (sem ela o Compose para antes de criar qualquer container)

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

O perfil `prd` (Oracle) começa sem dados. Os 10 tipos, 20 produtos e 20 movimentos de exemplo existem no H2 (`import.sql`, perfis padrão e `dev`) e no PostgreSQL (migração Flyway `V2__seed_demo_data.sql`, perfil `postgres`).

## Troubleshooting

### Oracle não inicia

1. Verificar se há RAM livre suficiente (o container pode usar até 3 GB, o `mem_limit`)
2. Aguardar a primeira subida (baixa a imagem e copia o banco pronto para o volume)
3. Verificar logs: `docker logs vortex-db`

### Aplicação não conecta ao Oracle

1. Verificar se o Oracle está healthy: `docker ps`
2. Testar a conexão: `docker exec vortex-db healthcheck.sh`
3. Verificar logs da aplicação: `docker logs vortex-app`
4. `ORA-01017: invalid credential or not authorized; logon denied` no log: o volume `vortex-oracle-data` foi criado com outra senha do `vortex_user` (a do antigo padrão `vortex_password`, por exemplo), e a imagem só grava `APP_USER_PASSWORD` na primeira subida. Troque a senha no banco como em [Volume criado com outras senhas](#volume-criado-com-outras-senhas) ou recrie o volume com `./start-vortex.sh --clean`.

### Volume criado com outras senhas

A imagem só aplica `ORACLE_PASSWORD` e `APP_USER_PASSWORD` na primeira subida do volume `vortex-oracle-data` e ignora as duas nas seguintes. Um volume criado antes de as senhas serem obrigatórias guarda os antigos padrões: `vortex_password` no `vortex_user`, que derruba o backend com `ORA-01017`, e `Oracle_1234` em SYS e SYSTEM, que não dá erro nenhum e continua valendo na porta 1521 publicada no host. Com o container de pé e as duas senhas novas exportadas, troque as duas no banco. Os comandos entram como SYS pela autenticação do sistema operacional do container, então não pedem a senha antiga:

```bash
docker exec vortex-db resetPassword "$ORACLE_PASSWORD"   # SYS e SYSTEM, pelo script da imagem
docker exec -i vortex-db sqlplus -s / as sysdba <<SQL
ALTER SESSION SET CONTAINER = FREEPDB1;
ALTER USER vortex_user IDENTIFIED BY "$ORACLE_APP_USER_PASSWORD";
SQL
```

Ou recrie o volume, perdendo os dados dele, com `./start-vortex.sh --clean`.

### Executar apenas Oracle (sem aplicação)

```bash
export ORACLE_PASSWORD="$(openssl rand -hex 24)" ORACLE_APP_USER_PASSWORD="$(openssl rand -hex 24)"
./infra/scripts/start-oracle.sh      # confere as duas senhas e passa um JWT_SECRET neutro: o banco não usa

# ou direto pelo Compose (o arquivo exige JWT_SECRET e as duas senhas, mesmo subindo só o db)
cd infra/docker
JWT_SECRET=nao-usado-pelo-oracle docker compose up -d db
```

### Conectar ao Oracle externamente

- Host: localhost
- Porta: 1521
- Serviço (PDB): FREEPDB1
- Usuário da aplicação: vortex_user / o valor de `ORACLE_APP_USER_PASSWORD` da primeira subida do volume
- Administrador: system / o valor de `ORACLE_PASSWORD` da primeira subida do volume

```bash
docker exec -it vortex-db sqlplus "vortex_user/$ORACLE_APP_USER_PASSWORD@//localhost:1521/FREEPDB1"
```

## Desenvolvimento Local

Para executar a aplicação localmente (fora do Docker) conectando ao Oracle no Docker:

1. Subir apenas o Oracle, com as duas senhas definidas antes da primeira subida do volume:
```bash
export ORACLE_PASSWORD="$(openssl rand -hex 24)"
export ORACLE_APP_USER_PASSWORD="$(openssl rand -hex 24)"
./infra/scripts/start-oracle.sh
```

2. Executar a aplicação com profile prd (o perfil exige um `JWT_SECRET` forte e recusa senha de banco fraca):
```bash
cd backend/vortex-application-service
JWT_SECRET="$(openssl rand -base64 48)" ORACLE_DB_USERNAME=vortex_user ORACLE_DB_PASSWORD="$ORACLE_APP_USER_PASSWORD" \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=prd
```

A URL padrão do perfil `prd` já aponta para `localhost:1521/FREEPDB1`.
