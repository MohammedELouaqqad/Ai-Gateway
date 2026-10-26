FROM node:22-alpine
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

ENV API_PROXY_TARGET=http://backend:8080
ENV KEYCLOAK_PROXY_TARGET=http://host.docker.internal:8180
ENV KEYCLOAK_PUBLIC_HOST=localhost:8180
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]
