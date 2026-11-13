#!/bin/bash

# Script para testar o endpoint de filas
echo "🧪 Testando o endpoint de filas..."

# URL base do backend
BASE_URL="http://localhost:8080"

echo "📋 1. Testando listagem de filas..."
curl -s -X GET "$BASE_URL/api/queues" | jq '.' || echo "Erro: Endpoint não disponível ou JSON inválido"

echo ""
echo "📊 2. Testando status do sistema..."
curl -s -X GET "$BASE_URL/api/queues/status" | jq '.' || echo "Erro: Endpoint não disponível ou JSON inválido"

echo ""
echo "🔍 3. Testando fila específica..."
curl -s -X GET "$BASE_URL/api/queues/vortex.movimento.estoque.queue" | jq '.' || echo "Erro: Endpoint não disponível ou JSON inválido"

echo ""
echo "✅ Teste concluído!" 