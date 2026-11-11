#!/bin/sh
# 운영 번들에 데모 코드가 없고, 데모 빌드에는 있음을 확인한다.
PATTERN='kairos\.demo|DemoBar|/demo/reseed'

rm -rf .next
env -u NEXT_PUBLIC_DEMO_MODE -u NEXT_PUBLIC_DEMO_PASSWORD pnpm exec next build || exit 1
if grep -rqE "$PATTERN" .next/static .next/server; then
  echo "운영 번들에 데모 코드가 있습니다"
  exit 1
fi

if [ "$1" = "--skip-demo-check" ]; then
  echo "데모 빌드 검사를 건너뜁니다(--skip-demo-check)"
  exit 0
fi

rm -rf .next
NEXT_PUBLIC_DEMO_MODE=true pnpm exec next build || exit 1
if ! grep -rqE "$PATTERN" .next/static .next/server; then
  echo "데모 빌드에 DemoBar 가 없습니다"
  exit 1
fi
