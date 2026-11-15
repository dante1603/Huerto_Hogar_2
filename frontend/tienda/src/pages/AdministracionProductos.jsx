import { useState } from 'react'
import { crearProducto, actualizarProducto, eliminarProducto } from '../lib/api.js'
import { formatGalatiCoins } from '../lib/money.js'
import './admin.css'

const EMPTY = Object.freeze({
  sku: '', nombre: '', descripcion: '', precio: '0', stock: '0', planetaOrigen: 'Bruma',
})
function toDraft(product) {
  return Object.fromEntries(Object.keys(EMPTY).map(key => [key, String(product[key] ?? '')]))
}
function payloadOf(draft) {
  return {
    ...draft,
    sku: draft.sku.trim(), nombre: draft.nombre.trim(),
    descripcion: draft.descripcion.trim(), planetaOrigen: draft.planetaOrigen.trim(),
    precio: Number(draft.precio), stock: Number(draft.stock),
  }
}

export default function AdministracionProductos({ session, products, loading, error, reload }) {
  const [editingId, setEditingId] = useState(null)
  const [draft, setDraft] = useState({ ...EMPTY })
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [failure, setFailure] = useState('')

  function reset() {
    setEditingId(null)
    setDraft({ ...EMPTY })
    setFailure('')
  }

  function edit(product) {
    setEditingId(product.id)
    setDraft(toDraft(product))
    setFailure('')
    setNotice('')
  }

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setFailure('')
    setNotice('')
    try {
      const payload = payloadOf(draft)
      if (editingId === null) {
        await crearProducto(payload, session.accessToken)
        setNotice('Producto creado en el backend.')
      } else {
        await actualizarProducto(editingId, payload, session.accessToken)
        setNotice('Producto actualizado en el backend.')
      }
      reset()
      await reload()
    } catch (cause) {
      setFailure(cause.message || 'No se pudo guardar el producto.')
    } finally {
      setBusy(false)
    }
  }

  async function remove(product) {
    if (busy || !window.confirm('¿Eliminar ' + product.nombre + ' del catálogo? Si tiene pedidos históricos, el backend lo impedirá.')) return
    setBusy(true)
    setFailure('')
    setNotice('')
    try {
      await eliminarProducto(product.id, session.accessToken)
      setNotice('Producto eliminado del catálogo.')
      if (editingId === product.id) reset()
      await reload()
    } catch (cause) {
      setFailure(cause.message || 'No se pudo eliminar el producto.')
    } finally {
      setBusy(false)
    }
  }

  const field = (key) => (event) => setDraft(current => ({ ...current, [key]: event.target.value }))
  return <section className="admin-page">
    <div className="page-title">
      <span className="eyebrow">Panel de administración · acceso JWT</span>
      <h1>Gestión del catálogo</h1>
      <p>Las altas, modificaciones y bajas se envían a Spring Boot. No se simulan escrituras ni se modifica directamente MySQL.</p>
    </div>
    {notice && <div className="notice" role="status">{notice}</div>}
    {failure && <div className="panel error-panel" role="alert">{failure}</div>}
    <div className="admin-layout">
      <div className="panel admin-list">
        <div className="admin-list-top">
          <h2>Productos registrados</h2>
          <button className="button button-quiet" type="button" disabled={busy || loading} onClick={() => reload()}>Actualizar</button>
        </div>
        {loading && <p role="status">Consultando productos…</p>}
        {error && <p role="alert">{error}</p>}
        {!loading && !error && !products.length && <p>No hay productos. Crea el primer registro.</p>}
        {!loading && !error && <div className="admin-product-list">
          {products.map(product => <article key={product.id} className="admin-product-row">
            <div className="admin-product-main">
              <strong>{product.nombre}</strong>
              <small>#{product.id} · {product.sku} · {product.planetaOrigen}</small>
              <span>{formatGalatiCoins(Number(product.precio))} · Stock: {product.stock}</span>
            </div>
            <div className="admin-row-actions">
              <button className="button button-quiet" type="button" onClick={() => edit(product)} disabled={busy}>Editar</button>
              <button className="button button-danger" type="button" onClick={() => remove(product)} disabled={busy}>Eliminar</button>
            </div>
          </article>)}
        </div>}
      </div>
      <form className="panel admin-form" onSubmit={submit}>
        <span className="eyebrow">CRUD · Administrador</span>
        <h2>{editingId === null ? 'Nuevo producto' : 'Editar producto #' + editingId}</h2>
        <label>SKU
          <input required maxLength={40} value={draft.sku} onChange={field('sku')} disabled={busy} />
        </label>
        <label>Nombre
          <input required maxLength={120} value={draft.nombre} onChange={field('nombre')} disabled={busy} />
        </label>
        <label>Descripción
          <textarea maxLength={500} value={draft.descripcion} onChange={field('descripcion')} disabled={busy} rows={3} />
        </label>
        <div className="admin-form-inline">
          <label>Precio (GalatiCoins)
            <input type="number" required min="0" step="1" value={draft.precio} onChange={field('precio')} disabled={busy} />
          </label>
          <label>Unidades
            <input type="number" required min="0" step="1" value={draft.stock} onChange={field('stock')} disabled={busy} />
          </label>
        </div>
        <label>Planeta de origen
          <input required maxLength={60} value={draft.planetaOrigen} onChange={field('planetaOrigen')} disabled={busy} />
        </label>
        <div className="admin-form-actions">
          <button type="submit" className="button button-primary" disabled={busy}>{busy ? 'Guardando…' : editingId === null ? 'Crear producto' : 'Guardar cambios'}</button>
          <button type="button" className="button button-quiet" onClick={reset} disabled={busy}>Limpiar</button>
        </div>
        <p className="small">Eliminar un producto que figure en pedidos históricos devuelve HTTP 409. El servidor conserva el historial.</p>
      </form>
    </div>
  </section>
}
