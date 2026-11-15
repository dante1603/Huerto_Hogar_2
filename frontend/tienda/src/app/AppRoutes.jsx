import { Link, Navigate, Route, Routes } from 'react-router-dom'
import Inicio from '../pages/Inicio.jsx'
import Productos from '../pages/Productos.jsx'
import Carrito from '../pages/Carrito.jsx'
import MisPedidos from '../pages/MisPedidos.jsx'
import PedidoDetalle from '../pages/PedidoDetalle.jsx'
import Nosotros from '../pages/Nosotros.jsx'
import Acceso from '../pages/Acceso.jsx'
import ConsultaProductos from '../pages/ConsultaProductos.jsx'
import AdministracionProductos from '../pages/AdministracionProductos.jsx'
import { activeRole, canAccessRoute, canAddToCart, homeForRole, ROLE_ADMIN, ROLE_VENDEDOR } from '../lib/roles.js'

function Restricted({ role, path, children }) {
  if (canAccessRoute(role, path)) return children
  return <section className="panel" role="alert">
    <h1>Acceso restringido</h1>
    <p>Esta función no está disponible para tu perfil. No se ejecutó ninguna operación.</p>
    <Link className="button button-primary" to={homeForRole(role)}>Volver a mi área</Link>
  </section>
}

export default function AppRoutes({
  products, loading, error, reload, cart, add, remove, total,
  session, onOrdered, onAuthenticated, onLogout,
}) {
  const role = activeRole(session)
  const allowedAdd = canAddToCart(role) ? add : null
  const catalogProps = { products, loading, error, add: allowedAdd, retry: () => reload() }

  return (
    <Routes>
      <Route path="/" element={role === ROLE_VENDEDOR || role === ROLE_ADMIN
        ? <Navigate replace to={homeForRole(role)} />
        : <Inicio {...catalogProps} />} />
      <Route path="/productos" element={
        <Restricted role={role} path="/productos"><Productos {...catalogProps} /></Restricted>
      } />
      <Route path="/nosotros" element={
        <Restricted role={role} path="/nosotros"><Nosotros /></Restricted>
      } />
      <Route path="/carrito" element={
        <Restricted role={role} path="/carrito">
          <Carrito cart={cart} products={products} remove={remove} total={total}
            session={session} onOrdered={onOrdered} />
        </Restricted>
      } />
      <Route path="/pedidos" element={
        <Restricted role={role} path="/pedidos"><MisPedidos session={session} /></Restricted>
      } />
      <Route path="/pedidos/:id" element={
        <Restricted role={role} path="/pedidos"><PedidoDetalle session={session} /></Restricted>
      } />
      <Route path="/consulta" element={
        <Restricted role={role} path="/consulta">
          <ConsultaProductos products={products} loading={loading} error={error} retry={() => reload()} />
        </Restricted>
      } />
      <Route path="/administracion" element={
        <Restricted role={role} path="/administracion">
          <AdministracionProductos session={session} products={products} loading={loading}
            error={error} reload={reload} />
        </Restricted>
      } />
      <Route path="/acceso" element={
        <Acceso session={session} onAuthenticated={onAuthenticated} onLogout={onLogout} />
      } />
      <Route path="*" element={
        <section className="panel"><h1>Esta ruta no existe</h1>
          <Link className="button button-quiet" to={homeForRole(role)}>Volver a mi área</Link></section>
      } />
    </Routes>
  )
}
