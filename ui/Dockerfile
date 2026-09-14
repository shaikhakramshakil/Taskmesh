# Build context is the repo root (see compose.yaml); all paths below are
# root-relative. Expects a Vite-style app in ui/ (npm run build -> dist/).
FROM node:22-alpine AS build
WORKDIR /app
COPY ui/package*.json ./
RUN npm ci 2>/dev/null || npm install
COPY ui/ ./
RUN npm run build

FROM nginx:alpine
COPY ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
