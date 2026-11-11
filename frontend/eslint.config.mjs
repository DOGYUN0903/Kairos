import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { FlatCompat } from "@eslint/eslintrc";
import tseslint from "typescript-eslint";

const __dirname = dirname(fileURLToPath(import.meta.url));
const compat = new FlatCompat({ baseDirectory: __dirname });

const moneyMessage = "금액 나눗셈은 src/lib/money.ts 에서만";

const config = [
  { ignores: [".next/**", "node_modules/**", "out/**", "next-env.d.ts", "src/api/schema.d.ts", "playwright-report/**", "test-results/**"] },
  ...compat.extends("next/core-web-vitals"),
  ...tseslint.configs.recommended,
  {
    rules: {
      "no-restricted-syntax": [
        "error",
        { selector: "BinaryExpression[operator='/']", message: moneyMessage },
        { selector: "BinaryExpression[operator='%']", message: moneyMessage },
        { selector: "AssignmentExpression[operator='/=']", message: moneyMessage },
        { selector: "AssignmentExpression[operator='%=']", message: moneyMessage },
      ],
    },
  },
  {
    files: ["src/lib/money.ts"],
    rules: { "no-restricted-syntax": "off" },
  },
];

export default config;
