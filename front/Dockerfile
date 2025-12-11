FROM node:20-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.29.4-alpine3.23-slim AS runtime

# Remove default nginx content and copy the Vite build output.
RUN rm -rf /usr/share/nginx/html/*
COPY --from=builder --chmod=0555 /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]