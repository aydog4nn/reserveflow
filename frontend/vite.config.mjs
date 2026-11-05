import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const proxy = {
    "/inventory-api": {
      target: env.INVENTORY_API_URL || "http://127.0.0.1:8080",
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/inventory-api/, ""),
    },
    "/order-api": {
      target: env.ORDER_API_URL || "http://127.0.0.1:8081",
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/order-api/, ""),
    },
  };
  return {
    plugins: [react()],
    server: { port: 5173, strictPort: true, proxy },
    preview: { port: 4173, strictPort: true, proxy },
  };
});
