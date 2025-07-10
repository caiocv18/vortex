#!/bin/bash

# Script para verificar e liberar portas conforme PORTS.md
# Baseado no esquema de portas do Vortex

REQUIRED_PORTS=(3001 5173 8080 8081 9092)
PORT_SERVICES=(
    "3001:Auth Frontend (React)"
    "5173:Main Frontend (Vue.js)"
    "8080:Main Backend (Spring Boot)"
    "8081:Auth Backend (Quarkus)"
    "9092:Kafka"
)

echo "🔍 Verificando conflitos de porta conforme PORTS.md..."
echo

CONFLICTS_FOUND=false

for port in "${REQUIRED_PORTS[@]}"; do
    # Verificar se a porta está sendo usada
    if lsof -i :$port >/dev/null 2>&1; then
        CONFLICTS_FOUND=true
        echo "⚠️  Porta $port está em uso:"
        lsof -i :$port | grep LISTEN | while read line; do
            echo "   $line"
        done
        
        # Buscar descrição do serviço
        for service in "${PORT_SERVICES[@]}"; do
            if [[ $service == $port:* ]]; then
                service_name="${service#*:}"
                echo "   💡 Esta porta deveria ser usada por: $service_name"
                break
            fi
        done
        
        # Oferecer opção de matar processo (ou fazer automaticamente se --auto-kill)
        if [[ "$1" == "--auto-kill" ]]; then
            echo "   🔪 Finalizando processos automaticamente na porta $port..."
            lsof -ti :$port | xargs kill -9 2>/dev/null
            echo "   ✅ Processos finalizados"
        else
            read -p "   🤔 Deseja finalizar os processos nesta porta? (y/N): " kill_process
            if [[ $kill_process =~ ^[Yy]$ ]]; then
                echo "   🔪 Finalizando processos na porta $port..."
                lsof -ti :$port | xargs kill -9 2>/dev/null
                echo "   ✅ Processos finalizados"
            fi
        fi
        echo
    else
        echo "✅ Porta $port está livre"
        for service in "${PORT_SERVICES[@]}"; do
            if [[ $service == $port:* ]]; then
                service_name="${service#*:}"
                echo "   📌 Reservada para: $service_name"
                break
            fi
        done
        echo
    fi
done

if [ "$CONFLICTS_FOUND" = false ]; then
    echo "🎉 Todas as portas estão livres para uso!"
else
    echo "⚡ Verificação concluída. Execute novamente se necessário."
fi

echo
echo "📊 Esquema de portas completo (PORTS.md):"
echo "   🔐 Auth Frontend (React): 3001"
echo "   🏢 Main Frontend (Vue.js): 5173 (dev) / 4173 (prod)"
echo "   🔐 Auth Backend (Quarkus): 8081"
echo "   🏢 Main Backend (Spring): 8080"
echo "   📊 Kafka: 9092"
echo "   🐰 RabbitMQ: 5672 (AMQP) / 15672 (Management)"
echo "   🗄️  Oracle DB: 1521"