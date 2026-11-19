# ============================================
# CarLink frontend — multi-stage production image
# Build with Node.js, serve static files with Nginx.
#
# Security plan S-20: base images pinned by digest (Dependabot bumps them),
# and nginx runs as the unprivileged `nginx` user on port 8080, with every
# writable path under /tmp so the container can run read-only.
# ============================================

# ---- Stage 1: Build stage ----
FROM node:22-alpine@sha256:1314fb30ec37592d5845e5a160d522c0e680972aa534549940b8d73bde88481a AS build
WORKDIR /app

# Install dependencies first (layer cached unless package files change)
COPY package.json package-lock.json ./
RUN npm ci

# Copy source code and build the Vite React application
COPY . .
RUN npm run build

# ---- Stage 2: Runtime stage ----
FROM nginx:alpine@sha256:0563c36f94a6d3ec3584161b77859f740f7e20b4853f52c796a864ce6908e8ea
WORKDIR /usr/share/nginx/html

# Non-root nginx: no `user` directive, pid and temp files in /tmp.
RUN rm -rf ./* \
    && sed -i -e '/^user /d' -e 's,^pid .*,pid /tmp/nginx.pid;,' /etc/nginx/nginx.conf \
    && sed -i '/^http {/a \    client_body_temp_path /tmp/client_temp;\n    proxy_temp_path /tmp/proxy_temp;\n    fastcgi_temp_path /tmp/fastcgi_temp;\n    uwsgi_temp_path /tmp/uwsgi_temp;\n    scgi_temp_path /tmp/scgi_temp;' /etc/nginx/nginx.conf \
    && chown -R nginx:nginx /var/cache/nginx

# Copy compiled static assets from build stage
COPY --from=build /app/dist ./

# Copy Nginx SPA + API reverse proxy configuration (listens on 8080)
COPY nginx.conf /etc/nginx/conf.d/default.conf

USER nginx
EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
