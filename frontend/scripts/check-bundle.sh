#!/bin/sh
# 운영 번들에 데모 코드가 없고, 데모 빌드에는 있음을 확인한다.
PATTERN='kairos\.demo|DemoBar|/demo/reseed'
# 실제 관리자 계정 값(환경변수 이름)은 어떤 번들에도 있으면 안 된다.
SECRET_PATTERN='KAIROS_ADMIN'

rm -rf .next
env -u NEXT_PUBLIC_DEMO_MODE -u NEXT_PUBLIC_DEMO_PASSWORD pnpm exec next build || exit 1
if grep -rqE "$PATTERN" .next/static .next/server; then
  echo "운영 번들에 데모 코드가 있습니다"
  exit 1
fi
if grep -rqE "$SECRET_PATTERN" .next/static .next/server; then
  echo "운영 번들에 KAIROS_ADMIN 문자열이 있습니다"
  exit 1
fi

rm -rf .next
NEXT_PUBLIC_DEMO_MODE=true pnpm exec next build || exit 1
if ! grep -rqE "$PATTERN" .next/static .next/server; then
  echo "데모 빌드에 DemoBar 가 없습니다"
  exit 1
fi
if grep -rqE "$SECRET_PATTERN" .next/static .next/server; then
  echo "데모 번들에 KAIROS_ADMIN 문자열이 있습니다"
  exit 1
fi
echo "check:bundle OK"
