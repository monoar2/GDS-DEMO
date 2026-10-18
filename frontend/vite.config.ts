import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The Spring Boot backend runs on 8080; the dev server forwards API calls to it.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
})
