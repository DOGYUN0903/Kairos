import type { Config } from "tailwindcss";
import animate from "tailwindcss-animate";

const config: Config = {
  darkMode: "class",
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    container: {
      center: true,
      padding: "24px",
      screens: { xl: "1280px" },
    },
    extend: {
      colors: {
        bg: "#FFFFFF",
        surface: "#F3F5F8",
        "surface-2": "#EEF0F4",
        border: "#E3E6EC",
        navy: "#1B2A4A",
        "navy-hover": "#15213B",
        "text-2": "#5B6577",
        "text-3": "#8A93A3",
        discount: "#D2401F",
        success: "#1F7A4D",
        warning: "#9A6700",
        danger: "#B42318",
        hot: "#E04A22",
        "hot-hover": "#C9401C",
        // shadcn/ui 변수 매핑
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        primary: { DEFAULT: "hsl(var(--primary))", foreground: "hsl(var(--primary-foreground))" },
        secondary: { DEFAULT: "hsl(var(--secondary))", foreground: "hsl(var(--secondary-foreground))" },
        destructive: { DEFAULT: "hsl(var(--destructive))", foreground: "hsl(var(--destructive-foreground))" },
        muted: { DEFAULT: "hsl(var(--muted))", foreground: "hsl(var(--muted-foreground))" },
        accent: { DEFAULT: "hsl(var(--accent))", foreground: "hsl(var(--accent-foreground))" },
        popover: { DEFAULT: "hsl(var(--popover))", foreground: "hsl(var(--popover-foreground))" },
        card: { DEFAULT: "hsl(var(--card))", foreground: "hsl(var(--card-foreground))" },
      },
      borderRadius: { DEFAULT: "4px", lg: "8px" },
      fontFamily: {
        sans: ['"Noto Sans KR"', "system-ui", "-apple-system", "sans-serif"],
      },
    },
  },
  plugins: [animate],
};

export default config;
