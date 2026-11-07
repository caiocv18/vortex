#!/bin/bash

# Script para verificar e liberar portas conforme docs/PORTS.md
# Baseado no esquema de portas do Vortex
#
# Uso: scripts/check-ports.sh [--auto-kill]
#   - Só conta como conflito quem ESCUTA na porta (LISTEN); conexões de cliente não contam
#     (ex.: o backend de autorização conectado ao PostgreSQL na 5432).
#   - Porta publicada por container Docker nunca é mexida: quem cuida é o docker compose.
#   - Processo do host recebe TERM, tem até 10 s para sair e só então leva KILL.

REQUIRED_PORTS=(3001 5173 8080 8081 9092 5432)
PORT_SERVICES=(
    "3001:Auth Frontend (React)"
    "5173:Main Frontend (Vue.js)"
    "8080:Main Backend (Spring Boot)"
    "8081:Auth Backend (Quarkus)"
    "9092:Kafka"
    "5432:PostgreSQL (Auth DB)"
)

service_name() {
    local service
    for service in "${PORT_SERVICES[@]}"; do
        if [[ $service == "$1":* ]]; then
            echo "${service#*:}"
            return
        fi
    done
}

# Nome do container que publica a porta (vazio se nenhum)
port_container() {
    command -v docker > /dev/null 2>&1 || return 0
    docker ps --format '{{.Names}} {{.Ports}}' 2>/dev/null | grep ":${1}->" | awk '{print $1}' | head -1
}

listen_pids() {
    lsof -t -iTCP:"$1" -sTCP:LISTEN 2>/dev/null | sort -u
}

port_listening() {
    ss -ltnH "sport = :$1" 2>/dev/null | grep -q .
}

# TERM, até 10 s de espera, KILL no que sobrar
terminate_port() {
    local port=$1 pids pid i alive
    pids=$(listen_pids "$port")
    [[ -z "$pids" ]] && return 0
    echo "   🔪 Enviando TERM para: $(echo $pids)"
    # shellcheck disable=SC2086
    kill -TERM $pids 2>/dev/null
    for ((i = 0; i < 40; i++)); do
        alive=0
        for pid in $pids; do
            kill -0 "$pid" 2>/dev/null && alive=1
        done
        [[ $alive -eq 0 ]] && break
        sleep 0.25
    done
    if [[ $alive -eq 1 ]]; then
        echo "   ⚠️  Ainda vivos após 10 s: KILL"
        # shellcheck disable=SC2086
        kill -KILL $pids 2>/dev/null
    fi
    echo "   ✅ Processos finalizados"
}

echo "🔍 Verificando conflitos de porta conforme PORTS.md..."
echo

CONFLICTS_FOUND=false

for port in "${REQUIRED_PORTS[@]}"; do
    name=$(service_name "$port")
    container=$(port_container "$port")
    if [[ -n "$container" ]]; then
        echo "🐳 Porta $port está com o container $container (não mexo; use docker compose)"
        echo "   📌 Reservada para: $name"
        echo
        continue
    fi

    if ! port_listening "$port"; then
        echo "✅ Porta $port está livre"
        echo "   📌 Reservada para: $name"
        echo
        continue
    fi

    CONFLICTS_FOUND=true
    echo "⚠️  Porta $port está em uso:"
    lsof -Pi :"$port" -sTCP:LISTEN 2>/dev/null | tail -n +2 | while read -r line; do
        echo "   $line"
    done
    echo "   💡 Esta porta deveria ser usada por: $name"

    if [[ -z "$(listen_pids "$port")" ]]; then
        echo "   ⚠️  O processo é de outro usuário (sem permissão para ver o PID): veja com sudo ss -ltnp 'sport = :$port'"
    elif [[ "$1" == "--auto-kill" ]]; then
        echo "   🔪 Finalizando processos automaticamente na porta $port (TERM, depois KILL)..."
        terminate_port "$port"
    else
        read -r -p "   🤔 Deseja finalizar os processos nesta porta? (y/N): " kill_process
        if [[ $kill_process =~ ^[Yy]$ ]]; then
            echo "   🔪 Finalizando processos na porta $port (TERM, depois KILL)..."
            terminate_port "$port"
        fi
    fi
    echo
done

if [ "$CONFLICTS_FOUND" = false ]; then
    echo "🎉 Nenhum processo do host em conflito com as portas do Vortex!"
else
    echo "⚡ Verificação concluída. Execute novamente se necessário."
fi

echo
echo "📊 Esquema de portas completo (PORTS.md):"
echo "   🔐 Auth Frontend (React): 3001 (Vite no modo dev, nginx vortex-static no modo leve)"
echo "   🏢 Main Frontend (Vue.js): 5173 (Vite no modo dev, nginx vortex-static no modo leve) / 4173 (preview)"
echo "   🔐 Auth Backend (Quarkus): 8081"
echo "   🏢 Main Backend (Spring): 8080"
echo "   🐘 PostgreSQL (Auth DB): 5432"
echo "   📊 Kafka: 9092 / Kafka UI (profile ui): 8090"
echo "   🐰 RabbitMQ: 5672 (AMQP) / 15672 (Management)"
echo "   🗄️  Oracle DB: 1521"
