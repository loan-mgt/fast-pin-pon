FROM --platform=$BUILDPLATFORM node:20-alpine AS builder

ARG MAP_TILE_URL="https://fast-pin-pon.4loop.org/maps/lyon/{z}/{x}/{y}.pbf"
ENV MAP_TILE_URL=${MAP_TILE_URL}

ARG API_BASE_URL="https://api.fast-pin-pon.4loop.org/v1"
ENV VITE_API_BASE_URL=${API_BASE_URL}

ARG KEYCLOAK_URL="https://auth.fast-pin-pon.4loop.org"
ENV VITE_KEYCLOAK_URL=${KEYCLOAK_URL}

WORKDIR /app

COPY package*.json ./
RUN npm ci --ignore-scripts

COPY . .

# Replace placeholder before building
RUN sed -i "s|__TILE_URL__|${MAP_TILE_URL}|g" public/style.json

RUN npm run build

FROM nginx:1.29.4-alpine3.23-slim AS runtime

RUN rm -rf /usr/share/nginx/html/*
COPY --from=builder --chmod=0555 /app/dist /usr/share/nginx/html
COPY --chmod=0555 nginx.conf /etc/nginx/conf.d/default.conf

RUN mkdir -p /var/cache/nginx/client_temp /var/cache/nginx/proxy_temp /var/cache/nginx/fastcgi_temp /var/cache/nginx/uwsgi_temp /var/cache/nginx/scgi_temp && \
        mkdir -p /etc/nginx/ssl && \
        touch /var/run/nginx.pid && \
        chown -R nginx:nginx /var/cache/nginx /etc/nginx /etc/nginx/ssl /var/log/nginx /var/run/nginx.pid /run/nginx.pid && \
        chmod -R 755 /etc/nginx /etc/nginx/ssl

USER nginx

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
