#!/bin/sh
tmp=$(mktemp)
pnpm exec openapi-typescript ../docs/api/openapi.yaml -o "$tmp" >/dev/null
diff -q "$tmp" src/api/schema.d.ts || { echo "openapi.yaml 이 바뀌었습니다. pnpm gen:api 를 실행해 src/api/schema.d.ts 를 같은 커밋에 넣으세요."; rm -f "$tmp"; exit 1; }
rm -f "$tmp"
