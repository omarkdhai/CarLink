# ============================================
# CarLink frontend — multi-stage production image
# Build with Node.js, serve static files with Nginx.
# ============================================

# ---- Stage 1: Build stage ----
FROM node:20-alpine AS build
WORKDIR /app

# Install dependencies first (layer cached unless package files change)
COPY package.json package-lock.json ./
RUN npm ci

# Copy source code and build the Vite React application
COPY . .
RUN npm run build

# ---- Stage 2: Runtime stage ----
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Remove default nginx static assets
RUN rm -rf ./*

# Copy compiled static assets from build stage
COPY --from=build /app/dist ./

# Copy Nginx SPA + API reverse proxy configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
