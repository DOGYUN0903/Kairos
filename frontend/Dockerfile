FROM node:22-alpine AS deps
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# BACKEND_URL 은 rewrites 로 next build 시점에 이미지에 고정된다(이미지마다 하나).
# 런타임 변경 가능한 프록시는 이후 과제. 빌드 시 --build-arg BACKEND_URL=... 로 넘긴다.
ARG BACKEND_URL
ENV BACKEND_URL=$BACKEND_URL
# check:api 는 ../docs/api/openapi.yaml 이 필요하므로 이미지 빌드에서는 next build 만 돈다.
# (타입 일치 검사는 CI 의 pnpm check:api 에서 한다)
RUN pnpm exec next build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
COPY --from=build /app/public ./public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
EXPOSE 3000
# 백엔드 주소는 빌드 시점에 이미지에 고정되어 있다(위 build 단계 ARG BACKEND_URL).
CMD ["node", "server.js"]
