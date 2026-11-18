# Build stage
# Node 26: react-router 8 (Node >= 22.22), vite 8, vitest 5 e jsdom 31 não aceitam mais o Node 18
FROM node:26.13.0-alpine AS build

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies: todas, porque o build (tsc + vite + @tailwindcss/vite) usa devDependencies
# --ignore-scripts (Sonar docker:S6505): nenhuma dependência do build precisa de script de
# instalação; os binários nativos musl chegam por optionalDependencies, não por postinstall
RUN npm ci --ignore-scripts

# Copy source code (o .dockerignore deixa o node_modules do host de fora: os binários nativos
# do rolldown, do lightningcss e do Tailwind 4 são de glibc e não rodam no Alpine/musl)
COPY . .

# Build the application
RUN npm run build

# Production stage: nginx sem root (Sonar docker:S6471). A variante unprivileged já traz pid e
# cache em caminhos graváveis pelo UID 101
FROM nginxinc/nginx-unprivileged:1.30.6-alpine

# Copy built application (arquivos de root e só legíveis: o processo não reescreve o site)
COPY --from=build /app/dist /usr/share/nginx/html

# Copy nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Sem root não há porta abaixo de 1024: o nginx escuta na 8080
EXPOSE 8080

# UID do usuário nginx da imagem, explícito para não depender do padrão dela
USER 101

# Start nginx
CMD ["nginx", "-g", "daemon off;"]
