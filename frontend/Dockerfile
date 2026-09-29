# syntax=docker/dockerfile:1
#
# Frontend image: builds the production React bundle, serves it with nginx and
# proxies /api/ to the backend (see nginx.conf). Build context is ./frontend.

# --- build ---------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# --- serve: unprivileged nginx (non-root, listens on 8080) -----------------------
FROM nginxinc/nginx-unprivileged:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
