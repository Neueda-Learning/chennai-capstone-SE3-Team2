# =====================================================================
# The trading UI, built and served by nginx -- which also forwards the API
# paths to the services, so the browser talks to one origin only.
#
#   docker compose build frontend          (from the repository root)
#   docker build -t frontend ./Frontend    (on its own)
#
# Where nginx forwards comes from the environment at start (ORDER_SERVICE_URL,
# AUTH_SERVICE_URL), never from the image: docker-compose.yml sets both.
# =====================================================================

# ---- 1. Build: the Angular app, in its same-origin configuration ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration docker

# ---- 2. Serve: nginx with the built page and the proxy ----
FROM nginx:1.27-alpine
# The image fills ${...} in templates/*.template from the environment at start.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/trading-ui/browser /usr/share/nginx/html
EXPOSE 80
