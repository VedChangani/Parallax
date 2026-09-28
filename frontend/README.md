# Parallax frontend

The React/Vite single-page app for Parallax. See the [repository root
README](../README.md) for how to run the backend this talks to, and
[`docs/architecture.md`](../docs/architecture.md) for the overall design.

## Development

```
npm install
npm run dev
```

The dev server proxies `/api/*` requests to the backend on
`localhost:8080` (see `vite.config.js`), so the backend must also be
running for the app to work end to end.

## Testing and linting

```
npm test
npm run lint
```
