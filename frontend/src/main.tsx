import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { z } from 'zod'
import './index.css'
import './lib/i18n'
import App from './app/App'

// The CSP (nginx.conf, security plan S-06) forbids eval. Zod 4 otherwise probes
// `new Function` for its JIT parser on first use, which the browser blocks and
// reports; jitless mode uses the plain interpreter from the start.
z.config({ jitless: true })

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)