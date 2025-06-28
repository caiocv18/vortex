# Dockerfile para Frontend Vue.js/Vite

# 1) Build stage: Node.js para build da aplicação
FROM node:20-alpine AS build

WORKDIR /app

# Copiar package.json e package-lock.json para cache de dependências
COPY package*.json ./

# Instalar dependências
RUN npm ci

# Copiar código fonte
COPY . .

# Build da aplicação para produção
RUN npm run build

# 2) Production stage: Nginx para servir arquivos estáticos
FROM nginx:alpine AS production

# Copiar arquivos buildados para o Nginx
COPY --from=build /app/dist /usr/share/nginx/html

# Copiar configuração customizada do Nginx (opcional)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expor porta 80
EXPOSE 80

# Comando para iniciar o Nginx
CMD ["nginx", "-g", "daemon off;"] 