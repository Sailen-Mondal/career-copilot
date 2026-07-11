# syntax=docker/dockerfile:1

# Use the official Playwright image which already has browsers pre-installed
FROM mcr.microsoft.com/playwright:v1.49.0-noble

WORKDIR /app

# Install dependencies first (layer cache friendly)
COPY package*.json ./
RUN npm ci

# Copy source and compile TypeScript
COPY tsconfig.json ./
COPY src/ ./src/

# Build to dist/
RUN npm run build

# Create screenshots directory
RUN mkdir -p screenshots

# Runtime: use compiled JS for production
# ts-node is available too, but compiled JS is faster and avoids ts-node overhead
CMD ["node", "dist/index.js"]
