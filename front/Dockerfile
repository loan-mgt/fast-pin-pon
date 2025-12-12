FROM node:20-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci --ignore-scripts

COPY . .
RUN npm run build

FROM nginx:1.29.4-alpine3.23-slim AS runtime

# Remove default nginx content and copy the Vite build output.
RUN rm -rf /usr/share/nginx/html/*
COPY --from=builder --chmod=0555 /app/dist /usr/share/nginx/html
COPY --chown=nginx:nginx nginx.conf /etc/nginx/conf.d/default.conf

RUN mkdir -p /var/cache/nginx/client_temp /var/cache/nginx/proxy_temp /var/cache/nginx/fastcgi_temp /var/cache/nginx/uwsgi_temp /var/cache/nginx/scgi_temp && \
        mkdir -p /etc/nginx/ssl && \
        touch /var/run/nginx.pid && \
        chown -R nginx:nginx /var/cache/nginx /etc/nginx /etc/nginx/ssl /var/log/nginx /var/run/nginx.pid /run/nginx.pid && \
        chmod -R 755 /etc/nginx /etc/nginx/ssl
USER nginx

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]