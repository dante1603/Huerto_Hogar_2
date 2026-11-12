import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { consultarPedidos } from '../lib/api.js'
import { formatGalatiCoins } from '../lib/money.js'

export default function MisPedidos({ session }) {
  const [items, setItems] = useState([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const role = session?.rolUsuario || ''
  const allowed = ['CLIENTE', 'VENDEDOR', 'ADMINISTRADOR'].includes(role)
    && Date.now() < (session?.expiresAt || 0)

  useEffect(() => {
    if (!allowed) { setItems([]); return }
    let cancelled = false
    setLoading(true)
    setError('')
    consultarPedidos(session.accessToken, role)
      .then(data => { if (!cancelled) setItems(Array.isArray(data) ? data : []) })
      .catch(err => { if (!cancelled) setError(err.message || 'Error consultando pedidos') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [session?.accessToken, role, allowed])

  return <section>
    <div className="page-title"><span className="eyebrow">Control de cargamentos</span>
      <h1>{role === 'CLIENTE' ? 'Mis pedidos' : 'Pedidos pendientes'}</h1>
      <p>{role === 'CLIENTE' ? 'Reservas registradas para tu cuenta.' : 'Vista de lectura para vendedor y administración.'}</p>
    </div>
    {!allowed && <div className="panel"><p>Necesitas una sesión válida para consultar esta sección.</p><Link className="button button-primary" to="/acceso">Iniciar sesión</Link></div>}
    {allowed && loading && <div className="panel" role="status">Consultando pedidos al backend…</div>}
    {allowed && error && <div className="panel" role="alert">{error}</div>}
    {allowed && !loading && !error && items.length === 0 && <div className="panel">No hay pedidos para mostrar.</div>}
    {allowed && !loading && !error && items.length > 0 &&
      <div className="order-list">{items.map(pedido =>
        <article key={pedido.id} className="panel order-card">
          <span className="eyebrow">Pedido #{pedido.id} · {pedido.estado}</span>
          <h2>Destino: {pedido.planetaDestino}</h2>
          {role !== 'CLIENTE' && <p className="small">Cliente #{pedido.clienteId}</p>}
          <ul className="order-items">{pedido.productos.map(item =>
            <li key={item.productoId}>{item.nombreProducto} × {item.cantidad} · {formatGalatiCoins(item.subtotal)}</li>)}</ul>
          <p><strong>Total: {formatGalatiCoins(pedido.total)}</strong></p>
        </article>)}</div>}
  </section>
}
