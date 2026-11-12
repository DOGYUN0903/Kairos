#!/bin/sh
# 운영 번들에 데모 코드가 없고, 데모 빌드에는 있음을 확인한다.
PATTERN='kairos\.demo|DemoBar|/demo/reseed'
# 실제 관리자 계정 값(환경변수 이름)은 어떤 번들에도 있으면 안 된다.
SECRET_PATTERN='KAIROS_ADMIN'
# 모의 결제의 실패 접두사(MockPgClient 규칙)는 데모 빌드에만 있어야 한다. 식별자는 압축 때 이름이 바뀌고
# 한글은 이스케이프될 수 있어 문자열 리터럴만 기준으로 삼는다.
MOCK_FAIL_PATTERN='"(fail|unknown|lost)-"'
# 토스 시크릿 키(환경변수 이름과 키 형식)는 어떤 번들에도 있으면 안 된다. 프론트는 공개용 클라이언트 키만 쓴다.
TOSS_SECRET_PATTERN='TOSS_SECRET|(test|live)_g?sk_[0-9A-Za-z]'

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
if grep -rqE "$MOCK_FAIL_PATTERN" .next/static .next/server; then
  echo "운영 번들에 모의 결제 실패 접두사가 있습니다"
  exit 1
fi
if grep -rqE "$TOSS_SECRET_PATTERN" .next/static .next/server; then
  echo "운영 번들에 토스 시크릿 키 문자열이 있습니다"
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
if ! grep -rqE "$MOCK_FAIL_PATTERN" .next/static .next/server; then
  echo "데모 빌드에 모의 결제 결과 선택지가 없습니다"
  exit 1
fi
if grep -rqE "$TOSS_SECRET_PATTERN" .next/static .next/server; then
  echo "데모 번들에 토스 시크릿 키 문자열이 있습니다"
  exit 1
fi
echo "check:bundle OK"
