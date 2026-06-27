import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/auth': 'http://localhost:8080',
      '/api': 'http://localhost:8080',
      '/users': 'http://localhost:8080',
      '/roles': 'http://localhost:8080',
      '/permissions': 'http://localhost:8080'
    }
  },
  test: {
    environment: 'jsdom',
    globals: true
  }
})
