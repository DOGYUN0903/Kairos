#!/bin/sh
# E2E 전용 서버: .next-e2e 에 운영 모드로 빌드한 뒤 next start 로 띄운다.
# dev 모드와 달리 실행 중 타입 생성이 없고, 빌드가 고쳐 쓰는 next-env.d.ts 는 원래대로 돌려놓는다(커밋된 파일을 더럽히지 않게).
set -e
PORT="${1:-3100}"
export NEXT_DIST_DIR=.next-e2e NEXT_PUBLIC_DEMO_MODE=false NEXT_PUBLIC_PG_PROVIDER=mock
backup=$(mktemp)
cp next-env.d.ts "$backup"
trap 'cp "$backup" next-env.d.ts; rm -f "$backup"' EXIT
pnpm exec next build
cp "$backup" next-env.d.ts
exec pnpm exec next start --port "$PORT"
