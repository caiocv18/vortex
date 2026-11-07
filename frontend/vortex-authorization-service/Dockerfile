# Build stage
# Node 26: react-router 8 (Node >= 22.22), vite 8, vitest 5 e jsdom 31 não aceitam mais o Node 18
FROM node:26.13.0-alpine AS build

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies: todas, porque o build (tsc + vite + @tailwindcss/vite) usa devDependencies
RUN npm ci

# Copy source code (o .dockerignore deixa o node_modules do host de fora: os binários nativos
# do rolldown, do lightningcss e do Tailwind 4 são de glibc e não rodam no Alpine/musl)
COPY . .

# Build the application
RUN npm run build

# Production stage
FROM nginx:1.30.6-alpine

# Copy built application
COPY --from=build /app/dist /usr/share/nginx/html

# Copy nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 80
EXPOSE 80

# Start nginx
CMD ["nginx", "-g", "daemon off;"]
