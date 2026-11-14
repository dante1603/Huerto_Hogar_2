import { Link, Route, Routes } from 'react-router-dom'
import Inicio from '../pages/Inicio.jsx'
import Productos from '../pages/Productos.jsx'
import Carrito from '../pages/Carrito.jsx'
import MisPedidos from '../pages/MisPedidos.jsx'
import Nosotros from '../pages/Nosotros.jsx'
import Acceso from '../pages/Acceso.jsx'

export default function AppRoutes({
  products, loading, error, reload, cart, add, remove, total,
  session, onOrdered, onAuthenticated, onLogout,
}) {
  return (
    <Routes>
      <Route path="/" element={<Inicio products={products} loading={loading} error={error} add={add} retry={() => reload()} />} />
      <Route path="/productos" element={<Productos products={products} loading={loading} error={error} add={add} retry={() => reload()} />} />
      <Route path="/carrito" element={<Carrito cart={cart} products={products} remove={remove} total={total} session={session} onOrdered={onOrdered} />} />
      <Route path="/pedidos" element={<MisPedidos session={session} />} />
      <Route path="/nosotros" element={<Nosotros />} />
      <Route path="/acceso" element={<Acceso session={session} onAuthenticated={onAuthenticated} onLogout={onLogout} />} />
      <Route path="*" element={<section className="panel"><h1>Esta ruta no existe</h1><Link to="/">Volver al inicio</Link></section>} />
    </Routes>
  )
}
