# Auth service. Multi-stage: the runtime stage carries a Node runtime and the
# built output, and nothing that could compile or install anything.

# --------------------------------------------------------------- build
FROM node:20-alpine AS build
WORKDIR /build

# package files first, so editing a .ts file reuses the cached install.
COPY services/auth/package.json services/auth/package-lock.json ./
RUN npm ci

COPY services/auth/tsconfig*.json services/auth/nest-cli.json ./
COPY services/auth/src ./src
RUN npm run build && npm prune --omit=dev

# ------------------------------------------------------------- runtime
FROM node:20-alpine AS runtime
RUN apk add --no-cache curl

# Not root: this process reads its own code and opens sockets.
RUN addgroup -S auth && adduser -S -G auth auth

WORKDIR /app
COPY --from=build --chown=auth:auth /build/node_modules ./node_modules
COPY --from=build --chown=auth:auth /build/dist ./dist
COPY --from=build --chown=auth:auth /build/package.json ./package.json

USER auth
EXPOSE 3000

HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:3000/docs/json > /dev/null || exit 1

# exec form so node is PID 1 and receives SIGTERM directly.
ENTRYPOINT ["node", "dist/main.js"]
