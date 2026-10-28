import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: '../api/openapi/openapi.yaml',
  output: 'src/api/generated',
  plugins: [
    '@hey-api/typescript',
    '@hey-api/sdk',
    // Relative URLs: the Vite dev server proxies /api to the backend.
    { name: '@hey-api/client-fetch', baseUrl: false },
  ],
});
