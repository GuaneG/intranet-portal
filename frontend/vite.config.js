import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  preview: {
    headers: {
      // prod build servis edilirken CSP header'ı olarak gider (gerçek deploy'da bu aynı header nginx/host'a konur)
      "Content-Security-Policy":
        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self' http://localhost:8080; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'",
    }
  },
});
