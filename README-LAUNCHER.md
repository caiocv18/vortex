# 🚀 Vortex Launcher

Script de inicialização completo (`start-vortex.sh`) que sobe os dois backends (Spring Boot e Quarkus), os dois frontends (Vue.js e React) e a infraestrutura Docker (PostgreSQL, Kafka, RabbitMQ, Oracle), em dois ambientes e dois modos de execução.

## 📋 Características

- **🪶 Modo leve (padrão)**: jars com JVM enxuta e cache AOT, frontends compilados servidos por um nginx só. Todo o sistema roda em cerca de 650 MB
- **🔥 Modo dev**: hot reload (`spring-boot:run`, `quarkus:dev` e Vite), com teto de memória nas JVMs
- **🔧 Ambiente de Desenvolvimento**: H2 Database
- **🚀 Ambiente de Produção**: Oracle Database Free + backend em container
- **🔨 Build só do que mudou**: carimbo por artefato; a segunda subida sem mudança no código não compila nada
- **🧰 Toolchain própria**: carrega Java 27 (SDKMAN) e Node.js 26 (nvm) sozinho
- **🛑 Parada limpa**: pid files com a identidade do processo, TERM, até 15 s de espera e só então KILL
- **🔑 Chaves JWT do auth**: gera o par RSA quando falta, sem nunca sobrescrever uma chave
- **📊 Tabela de memória**: RSS de cada processo e container, RAM da VM do WSL e do Windows

## 🚀 Uso Rápido

### Modo Interativo (Recomendado)
```bash
./start-vortex.sh
```

A ordem das perguntas é: tema, ambiente, **modo (leve ou dev)**, mensageria e componentes.

### Modo Direto
```bash
# O mais leve possível: modo leve, H2, sem mensageria, sem perguntas
./start-vortex.sh --modo leve -e dev -m none --no-interaction

# Hot reload (modo dev)
./start-vortex.sh -e dev -r dev -m none

# Ambiente de produção (Oracle; backend principal no Docker). Exige um JWT_SECRET forte:
export JWT_SECRET="$(openssl rand -base64 48)"
./start-vortex.sh -e prd -m none

# Apenas backend
./start-vortex.sh -e dev --backend-only

# Apenas frontend
./start-vortex.sh -e dev --frontend-only

# Com logs
./start-vortex.sh -e prd --logs
```

`--no-interaction` usa os padrões: tema verde, ambiente `dev`, modo `leve`, mensageria `kafka` e componentes completos. Passe `-m none` para subir sem Kafka.

### Gerenciamento
```bash
# Parar todos os serviços (containers com "down" sem -v: os dados ficam)
./start-vortex.sh --stop

# Limpar ambiente (cuidado: apaga os dados do Oracle, do Kafka e do RabbitMQ!)
./start-vortex.sh --clean

# Memória de tudo que está de pé
./scripts/medir-memoria.sh
```

## ⚙️ Modos de Execução (`--modo leve|dev`, ou `-r`)

| | Leve (padrão) | Dev |
|---|---|---|
| Backend principal | `java -jar target/leve/<jar>` (jar extraído), perfis `dev,leve` | `./mvnw spring-boot:run`, perfil `dev` |
| Backend de autorização | `java -jar target/quarkus-app/quarkus-run.jar`, perfil `leve` | `./mvnw quarkus:dev` |
| Frontends | `dist/` compilado, servido pelo `vortex-static` (nginx) nas portas 5173 e 3001 | Vite dev server (`--port N --strictPort`) |
| Builds | só o que mudou, um por vez, antes de subir qualquer JVM | compilação do próprio Maven e do Vite |
| Cache AOT | sim (JEP 514/515), treinado na primeira vez | não |
| Hot reload | não | sim |
| Memória (medida) | **cerca de 650 MB** no total do Vortex (de 617 MB parado a 664 MB em uso) | não medida ainda (estimativa: 2 a 2,5 GB) |

### Orçamento de memória do modo leve

Medido com `./scripts/medir-memoria.sh` depois de `./start-vortex.sh --modo leve -e dev -m none --no-interaction` (segunda subida, com os caches AOT):

| Peça | Teto | RSS medido |
|------|------|-----------|
| Backend principal (Spring Boot, perfis `dev,leve`) | `-Xmx320m` | 358 a 362 MB |
| Backend de autorização (Quarkus, perfil `leve`) | `-Xmx224m` | 233 a 234 MB |
| `vortex-auth-db` (PostgreSQL 18) | `mem_limit: 256m` | 18 a 39 MB |
| `vortex-static` (nginx) | `mem_limit: 32m` | 3 MB |
| **Total do Vortex, parado** | | **617 a 633 MB** |
| **Total do Vortex em uso** (13/11/2026, depois do login e das telas de painel, produtos, movimentações e relatórios) | | **664 MB** (backend principal 373 MB, autorização 244 MB) |
| VM do WSL inteira (inclui o Docker Engine e o cache de disco) | | cerca de 1,2 GB usados |

Picos que acontecem uma vez só:
- **Primeira subida** (quatro builds e os dois treinos de cache AOT): a VM chegou a 2,15 GB usados. Os builds rodam um de cada vez, antes de qualquer JVM.
- **Treino do cache AOT do backend principal**: a JVM do treino dispara uma segunda JVM para montar o cache, e as duas juntas chegam a uns 1,4 GB por cerca de 35 s. Por isso os dois treinos nunca rodam ao mesmo tempo.

Com mensageria, some o broker: Kafka nativo até 384 MB (`mem_limit`) ou RabbitMQ até 256 MB.

### Flags de JVM (num lugar só)

As flags ficam nas variáveis do topo do `start-vortex.sh` e são **as mesmas** no treino do cache AOT e na execução (o cache só vale com flags iguais):

```bash
JVM_BASE_FLAGS="-XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=192m"
APP_JVM_FLAGS="$JVM_BASE_FLAGS -Xmx320m"    # backend principal
AUTH_JVM_FLAGS="$JVM_BASE_FLAGS -Xmx224m"   # backend de autorização
export MALLOC_ARENA_MAX=2
```

- `-XX:+UseSerialGC` é explícito porque o JDK 27 escolhe o G1 mesmo em máquina pequena (JEP 523).
- Os cabeçalhos de objeto compactos já vêm ligados por padrão no JDK 27 (JEP 534); não há flag para isso.
- O modo leve **não** usa `-XX:TieredStopAtLevel=1`: ele jogaria fora o perfil de métodos que o cache AOT guarda do treino (JEP 515).
- No modo dev, o Spring ganha `-XX:TieredStopAtLevel=1` (sobe mais rápido) e o Maven de cada processo recebe um `MAVEN_OPTS` pequeno, só para aquele processo.

### Cache AOT (modo leve)

O JDK 27 guarda num arquivo as classes já carregadas e ligadas, e o perfil dos métodos, de uma execução de treino. Nas subidas seguintes a JVM parte desse cache.

| Backend | Sem cache | Com cache |
|---------|-----------|-----------|
| Principal (Spring Boot) | 11,6 s até o `/health` (JVM: 10,7 s) | 5,8 s (JVM: 5,1 s) |
| Autorização (Quarkus) | 4,7 s até o `/q/health` (JVM: 4,4 s) | 2,8 a 2,9 s (JVM: 2,5 a 2,7 s) |

Como funciona:
1. **Backend principal**: o jar do Spring Boot é extraído (`java -Djarmode=tools -jar target/<jar> extract --destination target/leve`) e roda sempre a partir de `target/leve/<jar>`, porque o cache exige o layout extraído e os mesmos timestamps de jar. O treino roda uma vez com `-XX:AOTCacheOutput=target/leve/app.aot -Dspring.context.exit=onRefresh` (o contexto sobe e a JVM sai), com as mesmas flags e os mesmos perfis.
2. **Backend de autorização**: o treino roda com `-XX:AOTCacheOutput=target/quarkus-app/app.aot`; o launcher espera o `/q/health` responder 200 e manda TERM, porque o cache é gravado na saída.
3. **Quando treina de novo**: se o cache não existe, se ficou mais velho que o build ou a extração, ou se mudaram as flags, os perfis (inclusive o de mensageria) ou a versão do Java (carimbo em `app.aot.stamp`).
4. **Validação**: a primeira subida com um cache novo usa `-XX:AOTMode=on`, que exige o cache. Se a JVM subir, o cache está valendo de verdade (o log mostra `Opened AOT cache`). Se a JVM recusar o cache (o log termina em `Unable to use AOT cache.`), o launcher guarda esse log em `logs/<serviço>-aot-falhou.log`, apaga o cache, sobe sem ele e treina de novo na próxima vez. Se a JVM morrer por qualquer outro motivo (um `ApplicationRunner` que lança, porta ocupada, falta de memória), o cache fica e o launcher mostra o erro real: o treino do backend principal sai logo depois do refresh do contexto, então esses erros só aparecem nessa primeira subida.
5. **Subidas normais**: modo padrão (auto). Um cache velho nunca trava a subida; no máximo a JVM o ignora.

Os tempos de cada subida ficam em `logs/startup-times.log`.

### Build só do que mudou (modo leve)

Cada artefato tem um carimbo (hash do conteúdo):

| Artefato | Entra no carimbo | Onde fica |
|----------|------------------|-----------|
| Backends | `src/`, `pom.xml`, `.mvn/` e a versão do Java | `target/.vortex-build` |
| Frontends | `src/`, `public/`, `index.html`, `package.json`, `package-lock.json`, `vite.config.*`, `tsconfig*.json`, `.env*`, o tema, os `VITE_*` e a versão do Node | `dist/.vortex-build` |

O build roda só quando o carimbo muda ou o artefato some: `./mvnw -B -q clean package -DskipTests` em cada backend e `npm run build` em cada frontend, um por vez. O `npm ci` roda só se o `node_modules` não existe ou o `package-lock.json` mudou. O `.env.local` do tema só é reescrito quando o conteúdo muda; reescrever sempre faria todo build rodar de novo.

As URLs que entram no build dos frontends ficam no topo do launcher (`FRONT_VITE_*`): o navegador chama os backends direto em `http://localhost:8080/api` e `http://localhost:8081`, sem proxy.

## ⚠️ WSL: fechar o último terminal desliga tudo

Nesta distro WSL o Docker Engine sobe no boot da distro (o `[boot] command` do `/etc/wsl.conf`). Quando o **último** terminal (ou processo `wsl.exe`) da distro fecha, o WSL desliga a distro e leva junto as JVMs, os servidores Vite e os containers.

- Deixe um terminal da distro aberto enquanto usar o Vortex.
- Para rodar o launcher a partir de outro processo (script do Windows, agente), mantenha a distro viva com um processo à parte, por exemplo `wsl.exe -d tcc-pucrs-vortex -- sleep 3000`, e encerre-o no fim.
- Logo depois de abrir a distro o Docker pode ainda estar subindo: o launcher espera até 60 s pelo `/var/run/docker.sock` e pelo `docker info` antes de desistir.

## 🌍 Ambientes

### 🔧 Desenvolvimento (dev)
- **Backend**: Spring Boot com perfil `dev` (mais `leve` no modo leve)
- **Database**: H2 em memória
- **Frontend**: nginx estático (modo leve) ou Vite dev server (modo dev)
- **URLs**:
  - API: http://localhost:8080
  - Frontend: http://localhost:5173
  - H2 Console: http://localhost:8080/h2-console

### 🚀 Produção (prd)
- **Backend**: Spring Boot com perfil `prd`, em container (`vortex-app`, build dentro do Docker)
- **Database**: Oracle Database Free 23ai (`gvenzl/oracle-free:23.26.3-slim-faststart`, serviço `FREEPDB1`)
- **Serviço de autorização e frontends**: seguem o modo escolhido (no modo leve, jar com cache AOT e nginx estático)
- **`JWT_SECRET` obrigatório**: o backend no perfil `prd` recusa o segredo padrão e qualquer segredo fraco (menos de 32 caracteres, as palavras secret, password, change ou default, só letras ou só números, ou sem maiúsculas e com menos de 50 caracteres). O launcher confere com as mesmas regras antes de parar ou subir qualquer coisa e para com a dica; os compose de prd param com `required variable JWT_SECRET is missing a value` se ele faltar. Gere com `export JWT_SECRET="$(openssl rand -base64 48)"`
- **Chave pública do auth**: o `vortex-app` recebe a `publicKey.pem` do serviço de autorização montada só leitura em `/app/keys/auth-public-key.pem` (`AUTH_JWT_PUBLIC_KEY_LOCATION`), para conferir os tokens quando o auth está fora
- **Mensageria**: com `-m kafka` o `vortex-app` fala com o `kafka:19092` pela rede `vortex-simple`; com `-m rabbitmq`, com o `vortex-rabbitmq` pela rede `vortex-rabbitmq-network` (usuário `vortex`, vhost `vortex-vhost`)
- **Falha rápida**: se o container `vortex-app` parar durante a subida, o launcher mostra as últimas linhas do log dele na hora, sem esperar os 10 minutos do health
- **URLs**:
  - API: http://localhost:8080
  - Frontend: http://localhost:5173 (modo leve) ou http://localhost:4173 (modo dev, `vite preview`)
  - Oracle: localhost:1521/FREEPDB1

## 📦 Componentes

### Backend principal (Spring Boot 4.1, Java 27)
- **Leve**: jar extraído em `target/leve/`, cache AOT em `target/leve/app.aot`
- **Dev**: `./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="<flags> -Xmx320m -XX:TieredStopAtLevel=1"`
- **Prd**: container com Oracle
- **Logs**: `logs/backend.log` (leve e dev) ou `docker logs vortex-app` (prd)

### Backend de autorização (Quarkus 3.40, Java 27)
- **Leve**: `java ... -Dquarkus.profile=leve -jar target/quarkus-app/quarkus-run.jar` (mais o perfil de mensageria, se houver)
- **Dev**: `./mvnw quarkus:dev -Djvm.args="<flags> -Xmx224m"`
- **Logs**: `logs/auth-backend.log`

### Frontends (Vue.js 3.5 + Vuetify 4, React 19 + Tailwind 4; Vite 8)
- **Leve**: `npm run build` (só se mudou) e o container `vortex-static` serve os dois `dist/`
- **Dev**: `vite --port 5173 --strictPort` e `vite --port 3001 --strictPort`; o `vite.config.ts` nunca é editado
- O Vue DevTools só liga com `VITE_DEVTOOLS=true` no ambiente
- **Logs**: `logs/frontend.log` e `logs/auth-frontend.log` (modo dev) ou `docker logs vortex-static` (modo leve)

### Database
- **Dev**: H2 em memória (automático) e PostgreSQL 18 (`vortex-auth-db`, porta 5432) para o serviço de autorização
- **Prd**: Oracle Database Free (Docker), volume `vortex-oracle-data`, `mem_limit: 3g` (o teto de 2 GB do Oracle Free cobre só SGA e PGA; a folga é para os processos de fundo e o listener)
- **Persistência**: o volume `vortex-auth-db-data-pg18` guarda o PostgreSQL 18; `--stop` nunca apaga volume

#### PostgreSQL 17 → 18: volume novo
O volume antigo `docker_auth-db-data` guarda um cluster do PostgreSQL 17 na raiz (era montado em `/var/lib/postgresql/data`). A imagem 18 monta em `/var/lib/postgresql` e se recusa a subir em cima dele, então o compose usa o volume novo `vortex-auth-db-data-pg18`, criado vazio na primeira subida. O launcher avisa nessa hora se o volume antigo existe. Ele nunca é apagado; para trazer os usuários cadastrados antes:

```bash
./start-vortex.sh --stop
bash scripts/migrar-auth-db-pg18.sh     # pg_dump do 17 (numa cópia do volume) e restauração no 18
docker volume rm docker_auth-db-data    # só quando não precisar mais do volume antigo
```

Detalhes em [scripts/README.md](scripts/README.md#-migrar-auth-db-pg18sh).

### 🔑 Chaves JWT do serviço de autorização
O auth assina os tokens em RS256 com `backend/vortex-authorization-service/src/main/resources/jwt/privateKey.pem`, e o backend principal confere com a `publicKey.pem` quando o auth está fora. As duas ficam fora do git (`*.pem`). Antes dos builds, toda subida:
- par completo: só confere que as duas chaves são do mesmo par (um par trocado interrompe a subida, porque o auth assinaria tokens que nem ele consegue conferir);
- par só no local antigo (`src/main/resources/META-INF/resources`, servido como arquivo estático pelo Quarkus): move para `jwt/`, e os tokens já emitidos continuam valendo;
- só a privada: deriva a pública dela;
- só a pública: para com erro (gerar uma privada nova trocaria a pública);
- nenhuma: gera um par RSA 2048 com `openssl` (PKCS#8 e X.509, privada com permissão 600).

Nenhuma chave existente é sobrescrita. Nos modos leve e dev o backend principal recebe `AUTH_JWT_PUBLIC_KEY_LOCATION` (caminho absoluto da `publicKey.pem`) e `AUTH_JWT_ISSUER` (o `JWT_ISSUER` do auth, padrão `vortex-auth-service`).

## 🔧 Pré-requisitos

### Obrigatórios
- ✅ Docker Engine com o plugin Docker Compose v2 (`docker compose`)
- ✅ Java 27 pelo SDKMAN: `sdk install java 27.0.1-tem`
- ✅ Node.js 26 pelo nvm: `nvm install 26`

O launcher carrega o SDKMAN e o nvm sozinho (o `.bashrc` retorna cedo em shell não interativo) e para com uma mensagem clara se faltar algum. O Maven não precisa estar instalado: cada backend tem o seu `./mvnw` (Maven 3.10.0).

## 🛑 Como a parada funciona

`./start-vortex.sh --stop` (e o começo de toda subida):
1. Lê os pid files em `logs/` (backends, JVMs filhas do modo dev, wrappers do Maven, servidores Vite e treinos de cache AOT). Cada um guarda `PID início boot_id`: o instante de início do processo (campo 22 do `/proc/PID/stat`) e o `boot_id` do kernel. O sinal só vai se o PID ainda for o **mesmo processo** (mesmo instante de início, mesmo boot e linha de comando do programa esperado). Os pid files sobrevivem quando a distro desliga e os PIDs recomeçam baixos na volta: um PID reaproveitado por um language server do editor ou pelo seu terminal nunca recebe sinal, e o pid file velho é descartado com aviso (o mesmo vale para um pid file no formato antigo, só com o PID). Depois manda **TERM**, espera até 15 s e só então manda KILL. Nada de `pkill -f vite` (que pegaria vitest e Playwright) nem `pkill -f kafka` (que pegaria o próprio backend com o perfil kafka).
2. Faz `docker compose ... down` **sem `-v`** em cada stack (inclusive o `vortex-static`): os volumes ficam.
3. Para qualquer container `vortex-*` que tenha sobrado.

Na subida, o `scripts/check-ports.sh --auto-kill` libera as portas 3001, 5173, 8080, 8081, 9092 e 5432: só conta quem está escutando, pula portas publicadas por containers e manda TERM antes do KILL.

## 📊 Tabela de memória

```bash
./scripts/medir-memoria.sh
```

Mostra o RSS de cada processo dos pid files (sozinho e com os filhos), o uso de cada container `vortex-*` (`docker stats --no-stream`), o total da VM do WSL (`free -m`) e a RAM livre do Windows (`powershell.exe` pela interop do WSL). O launcher imprime essa tabela no fim de toda subida. O script sempre sai com 0, mesmo com partes paradas.

## 📋 Comandos Úteis

### Durante Execução
```bash
# Status dos containers
docker ps

# Logs em tempo real
tail -f logs/backend.log          # Backend principal (leve e dev)
tail -f logs/auth-backend.log     # Backend de autorização
docker logs vortex-static -f      # nginx dos frontends (modo leve)
docker logs vortex-app -f         # Backend (prd)
docker logs vortex-db -f          # Oracle

# Tempos de subida (com e sem cache AOT)
cat logs/startup-times.log

# Acessar Oracle
docker exec -it vortex-db sqlplus vortex_user/vortex_password@//localhost:1521/FREEPDB1
```

### Desenvolvimento manual
```bash
# Apenas Oracle para dev local
cd infra/docker && docker compose up -d db

# Backend dev local
cd backend/vortex-application-service && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend dev local
cd frontend/vortex-application-service && npx vite --port 5173 --strictPort
```

## 🐛 Troubleshooting

**1. Porta em uso**
```bash
./start-vortex.sh --stop
./scripts/check-ports.sh          # mostra quem escuta e pergunta antes de encerrar
```

**2. "Docker não respondeu em 60 s"**
```bash
sudo service docker status        # o Engine sobe pelo [boot] command do /etc/wsl.conf
```

**3. Oracle não inicia**
```bash
docker logs vortex-db             # o container pode usar até 3 GB de RAM (mem_limit: 3g)
```

**3b. `-e prd` para logo no começo com "JWT_SECRET ..."**
```bash
export JWT_SECRET="$(openssl rand -base64 48)"   # o perfil prd recusa o segredo padrão
```

**4. Forçar um build ou um treino de cache AOT novo**
```bash
rm -f backend/*/target/.vortex-build frontend/*/dist/.vortex-build   # build de tudo
rm -f backend/vortex-application-service/target/leve/app.aot*        # novo treino do backend principal
rm -f backend/vortex-authorization-service/target/quarkus-app/app.aot*  # novo treino do auth
```

**5. Logs de build**
```bash
ls logs/build-*.log logs/npm-ci-*.log logs/aot-train-*.log
```

**6. Testes do próprio launcher** (sem Docker, JVM nem rede)
```bash
bash scripts/test-start-vortex.sh
```

## 🔄 Fluxo de Execução

1. **Verificação**: diretórios, Docker (espera até 60 s), Compose v2, portas
2. **Toolchain**: Java 27 (SDKMAN) e Node.js 26 (nvm)
3. **Seleção**: tema, ambiente, modo, mensageria e componentes (no prd, confere o `JWT_SECRET` antes de seguir)
4. **Limpeza**: para o que estiver de pé (pid files e `docker compose down` sem `-v`)
5. **Chaves JWT do auth**: gera ou move o par quando falta, sem sobrescrever
6. **Builds** (modo leve): só o que mudou, um por vez
7. **Inicialização**: PostgreSQL, mensageria, serviço de autorização, backend principal e frontends
8. **Relatório**: status, URLs, modo e tabela de memória
