# Dockerfile para Frontend Vue.js/Vite

# 1) Build stage: Node.js para build da aplicação
# Node 26: vitest 5, jsdom 31, npm-run-all2 9 e vite 8 não aceitam mais o Node 20 (EOL)
FROM node:26.13.0-alpine AS build

WORKDIR /app

# Copiar package.json e package-lock.json para cache de dependências
COPY package*.json ./

# Instalar dependências
RUN npm ci

# Copiar código fonte (o .dockerignore deixa o node_modules do host de fora: os binários
# nativos do rolldown são de glibc e não rodam no Alpine/musl)
COPY . .

# Build da aplicação para produção
RUN npm run build

# 2) Production stage: Nginx para servir arquivos estáticos
FROM nginx:1.30.6-alpine AS production

# Copiar arquivos buildados para o Nginx
COPY --from=build /app/dist /usr/share/nginx/html

# Copiar configuração customizada do Nginx (opcional)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expor porta 80
EXPOSE 80

# Comando para iniciar o Nginx
CMD ["nginx", "-g", "daemon off;"]