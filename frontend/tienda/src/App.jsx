import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, NavLink, Route, Routes } from 'react-router-dom'
import { fetchProductos } from './lib/api.js'
import { addToCart, readCart, removeFromCart, totalCart, writeCart } from './lib/cart.js'
import { clearSession, readSession, saveSession } from './lib/session.js'
import Inicio from './pages/Inicio.jsx'
import Productos from './pages/Productos.jsx'
import Carrito from './pages/Carrito.jsx'
import MisPedidos from './pages/MisPedidos.jsx'
import Nosotros from './pages/Nosotros.jsx'
import Acceso from './pages/Acceso.jsx'

export default function App() {
  const [products, setProducts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [cart, setCart] = useState(() => readCart(window.localStorage))
  const [session, setSession] = useState(() => readSession())
  const [notice, setNotice] = useState('')

  const reload = useCallback(async (signal) => {
    setLoading(true)
    setError('')
    try {
      const data = await fetchProductos({ signal })
      if (signal?.aborted) return
      setProducts(data)
    } catch (err) {
      if (signal?.aborted || err?.name === 'AbortError') return
      setError(err.message || 'No se pudo cargar el catálogo.')
      setProducts([])
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    reload(controller.signal)
    return () => controller.abort()
  }, [reload])

  useEffect(() => {
    writeCart(window.localStorage, cart)
  }, [cart])

  const add = useCallback((product) => {
    if (!(Number(product.stock) > 0)) return
    setCart((old) => addToCart(old, product))
    setNotice('Producto añadido al carrito')
  }, [])

  const remove = useCallback((id) => setCart((old) => removeFromCart(old, id)), [])
  const cartCount = useMemo(() => cart.reduce((sum, item) => sum + item.cantidad, 0), [cart])
  const logout = () => { clearSession(); setSession(null) }
  const ordered = () => {
    setCart([])
    setNotice('Reserva registrada. La entrega queda pendiente.')
    reload()
  }

  return (
    <div className="app">
      <header className="site-header">
        <div className="shell topbar">
          <Link className="brand" to="/" aria-label="HuertoHogar Galaxym, inicio">
            <span className="brand-symbol" aria-hidden="true">✳</span>
            <span><strong>HuertoHogar</strong><small>Galaxym · Mercadería interplanetaria</small></span>
          </Link>
          <nav className="nav-links" aria-label="Navegación principal">
            <NavLink to="/" end>Inicio</NavLink>
            <NavLink to="/productos">Productos</NavLink>
            <NavLink to="/nosotros">Nosotros</NavLink>
            <NavLink to="/carrito">Carrito <span className="count">{cartCount}</span></NavLink>
            {session && <NavLink to="/pedidos">Pedidos</NavLink>}
            <NavLink to="/acceso">{session ? 'Mi cuenta' : 'Ingresar'}</NavLink>
          </nav>
        </div>
      </header>

      <main className="shell page">
        {notice && <div className="notice" role="status">{notice}<button type="button" onClick={() => setNotice('')} aria-label="Cerrar aviso">×</button></div>}
        <Routes>
          <Route path="/" element={<Inicio products={products} loading={loading} error={error} add={add} retry={() => reload()} />} />
          <Route path="/productos" element={<Productos products={products} loading={loading} error={error} add={add} retry={() => reload()} />} />
          <Route path="/carrito" element={<Carrito cart={cart} products={products} remove={remove} total={totalCart(cart)} session={session} onOrdered={ordered} />} />
          <Route path="/pedidos" element={<MisPedidos session={session} />} />
          <Route path="/nosotros" element={<Nosotros />} />
          <Route path="/acceso" element={<Acceso session={session} onAuthenticated={(value) => setSession(saveSession(value))} onLogout={logout} />} />
          <Route path="*" element={<section className="panel"><h1>Esta ruta no existe</h1><Link to="/">Volver al inicio</Link></section>} />
        </Routes>
      </main>

      <footer className="site-footer">
        <div className="shell footer-inner">
          <span>✳ HuertoHogar · Galaxym</span>
          <span>Proyecto ficticio de Fullstack · Pedidos pendientes de entrega</span>
        </div>
      </footer>
    </div>
  )
}
