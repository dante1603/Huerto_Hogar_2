import { useCallback, useState } from 'react'
import AppRoutes from './app/AppRoutes.jsx'
import SiteFooter from './components/layout/SiteFooter.jsx'
import SiteHeader from './components/layout/SiteHeader.jsx'
import { useCarrito } from './hooks/useCarrito.js'
import { useCatalogo } from './hooks/useCatalogo.js'
import { useSesion } from './hooks/useSesion.js'

export default function App() {
  const [notice, setNotice] = useState('')
  const { products, loading, error, reload } = useCatalogo()
  const notifyAdded = useCallback(() => setNotice('Producto añadido al carrito'), [])
  const { cart, add, remove, clear, cartCount, total } = useCarrito(notifyAdded)
  const { session, onAuthenticated, logout } = useSesion()

  const ordered = useCallback(() => {
    clear()
    setNotice('Reserva registrada. La entrega queda pendiente.')
    reload()
  }, [clear, reload])

  return (
    <div className="app">
      <SiteHeader session={session} cartCount={cartCount} />
      <main className="shell page">
        {notice && <div className="notice" role="status">{notice}<button type="button" onClick={() => setNotice('')} aria-label="Cerrar aviso">×</button></div>}
        <AppRoutes
          products={products}
          loading={loading}
          error={error}
          reload={reload}
          cart={cart}
          add={add}
          remove={remove}
          total={total}
          session={session}
          onOrdered={ordered}
          onAuthenticated={onAuthenticated}
          onLogout={logout}
        />
      </main>
      <SiteFooter />
    </div>
  )
}
