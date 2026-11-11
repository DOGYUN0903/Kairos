import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// vitest globals 를 끄면 Testing Library 자동 cleanup 이 걸리지 않는다.
afterEach(() => {
  cleanup();
});
