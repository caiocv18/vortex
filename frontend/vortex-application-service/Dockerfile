# Dockerfile para Frontend Vue.js/Vite

# 1) Build stage: Node.js para build da aplicação
# Node 26: vitest 5, jsdom 31, npm-run-all2 9 e vite 8 não aceitam mais o Node 20 (EOL)
FROM node:26.13.0-alpine AS build

WORKDIR /app

# Copiar package.json e package-lock.json para cache de dependências
COPY package*.json ./

# Instalar dependências
# --ignore-scripts (Sonar docker:S6505): nenhuma dependência do build precisa de script de
# instalação; os binários nativos musl chegam por optionalDependencies, não por postinstall
RUN npm ci --ignore-scripts

# Copiar código fonte (o .dockerignore deixa o node_modules do host de fora: os binários
# nativos do rolldown são de glibc e não rodam no Alpine/musl)
COPY . .

# Build da aplicação para produção
RUN npm run build

# 2) Production stage: Nginx sem root para servir arquivos estáticos (Sonar docker:S6471).
# A variante unprivileged do nginx já traz pid e cache em caminhos graváveis pelo UID 101
FROM nginxinc/nginx-unprivileged:1.31.7-alpine AS production

# Arquivos de root e só legíveis: o processo serve o site, mas não o reescreve
COPY --from=build /app/dist /usr/share/nginx/html

# Configuração customizada do Nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Sem root não há porta abaixo de 1024: o nginx escuta na 8080
EXPOSE 8080

# UID do usuário nginx da imagem, explícito para não depender do padrão dela
USER 101

# Comando para iniciar o Nginx
CMD ["nginx", "-g", "daemon off;"]