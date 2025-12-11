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

RUN mkdir -p /var/cache/nginx/client_temp && \
        mkdir -p /var/cache/nginx/proxy_temp && \
        mkdir -p /var/cache/nginx/fastcgi_temp && \
        mkdir -p /var/cache/nginx/uwsgi_temp && \
        mkdir -p /var/cache/nginx/scgi_temp && \
        chown -R nginx:nginx /var/cache/nginx && \
        chown -R nginx:nginx /etc/nginx/ && \
        chmod -R 755 /etc/nginx/ && \
        chown -R nginx:nginx /var/log/nginx

RUN mkdir -p /etc/nginx/ssl/ && \
    chown -R nginx:nginx /etc/nginx/ssl/ && \
    chmod -R 755 /etc/nginx/ssl/

RUN touch /var/run/nginx.pid && \
        chown -R nginx:nginx /var/run/nginx.pid /run/nginx.pid
USER nginx

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]