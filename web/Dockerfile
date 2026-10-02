# syntax=docker/dockerfile:1
# The web client: static files served by nginx, which also passes /api on to the backend.
# Build:  docker build -t wtm-web web

FROM node:20-alpine AS build
WORKDIR /web
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# Files in /etc/nginx/templates are filled in with the environment variables below when the container starts.
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /web/dist /usr/share/nginx/html
ENV BACKEND_URL=http://backend:8080
EXPOSE 80
