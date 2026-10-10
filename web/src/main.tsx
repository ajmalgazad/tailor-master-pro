import { createRoot } from 'react-dom/client'
import { App } from './App'
import './styles.css'
import { startPageTranslation } from './i18n'

startPageTranslation()
createRoot(document.getElementById('root')!).render(<App />)
