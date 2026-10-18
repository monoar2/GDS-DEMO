import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { HashRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import { AirportsProvider, AuthProvider, BasketProvider } from './state.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <HashRouter>
      <AuthProvider>
        <AirportsProvider>
          <BasketProvider>
            <App />
          </BasketProvider>
        </AirportsProvider>
      </AuthProvider>
    </HashRouter>
  </StrictMode>,
)
