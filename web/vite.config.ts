import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Relative base so the app works from https://<user>.github.io/tailor-master-pro/app/
export default defineConfig({
  base: './',
  plugins: [react()],
  build: { outDir: 'dist', chunkSizeWarningLimit: 1500 },
})
