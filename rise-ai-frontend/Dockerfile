# FE-06: reproducible SPA build; Backend/Monitoring are separate services.
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Same-origin /api proxy in the runtime container; no localhost API URL baked into assets.
ENV VITE_API_BASE_URL=""
RUN npm run verify

FROM nginx:1.27-alpine
ENV NGINX_ENVSUBST_FILTER="BACKEND_UPSTREAM"
COPY deploy/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/ /usr/share/nginx/html/
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
