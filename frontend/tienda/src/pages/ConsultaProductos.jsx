import { useState } from 'react'
import { fetchProducto } from '../lib/api.js'
import { formatGalatiCoins } from '../lib/money.js'
import './admin.css'

/** Consulta de solo lectura para VENDEDOR y ADMINISTRADOR. */
export default function ConsultaProductos({ products, loading, error, retry }) {
  const [selected, setSelected] = useState(null)
  const [pendingId, setPendingId] = useState(null)
  const [detailError, setDetailError] = useState('')

  async function inspect(id) {
    if (pendingId !== null) return
    setPendingId(id)
    setDetailError('')
    try {
      const detail = await fetchProducto(id)
      setSelected(detail)
    } catch (err) {
      setDetailError(err.message || 'No se pudo consultar el detalle.')
      setSelected(null)
    } finally {
      setPendingId(null)
    }
  }

  return <section className="admin-page">
    <div className="page-title">
      <span className="eyebrow">Consulta de inventario · solo lectura</span>
      <h1>Productos disponibles</h1>
      <p>Esta sección permite consultar la lista y el detalle, pero no comprar, crear ni modificar productos.</p>
    </div>
    {loading && <div className="panel" role="status">Cargando catálogo desde Spring Boot…</div>}
    {error && <div className="panel error-panel" role="alert"><p>{error}</p><button className="button button-quiet" type="button" onClick={retry}>Reintentar</button></div>}
    {!loading && !error && <div className="panel">
      {products.length === 0 && <p>Sin productos registrados.</p>}
      <div className="admin-product-list">
        {products.map(product => <article className="admin-product-row" key={product.id}>
          <div className="admin-product-main">
            <strong>{product.nombre}</strong>
            <small>{product.sku} · Origen: {product.planetaOrigen}</small>
            <span>Stock: {product.stock}</span>
          </div>
          <button type="button" className="button button-quiet" onClick={() => inspect(product.id)} disabled={pendingId !== null}>{pendingId === product.id ? 'Consultando…' : 'Ver detalle'}</button>
        </article>)}
      </div>
    </div>}
    {detailError && <div className="panel error-panel" role="alert">{detailError}</div>}
    {selected && <section className="panel admin-detail" aria-live="polite">
      <div className="admin-list-top"><h2>Detalle de {selected.nombre}</h2><button className="button button-quiet" type="button" onClick={() => setSelected(null)}>Cerrar</button></div>
      <p><strong>SKU:</strong> {selected.sku}</p>
      <p><strong>Origen:</strong> {selected.planetaOrigen}</p>
      <p><strong>Precio:</strong> {formatGalatiCoins(Number(selected.precio))}</p>
      <p><strong>Existencias:</strong> {selected.stock}</p>
      <p>{selected.descripcion}</p>
    </section>}
  </section>
}
