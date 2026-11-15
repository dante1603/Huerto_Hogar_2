import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { consultarPedido, emitirBoleta, generarDetalleBoleta } from '../lib/api.js'
import { formatGalatiCoins } from '../lib/money.js'

export default function PedidoDetalle({ session }) {
  const { id } = useParams()
  const [order, setOrder] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [boleta, setBoleta] = useState(null)
  const [detalles, setDetalles] = useState([])
  const [cargandoComprobante, setCargandoComprobante] = useState(false)
  const [errorComprobante, setErrorComprobante] = useState('')

  useEffect(() => {
    let cancelled = false
    setOrder(null)
    setError('')
    setBoleta(null)
    setDetalles([])
    setErrorComprobante('')
    setLoading(true)
    consultarPedido(id, session?.accessToken)
      .then(data => { if (!cancelled) setOrder(data) })
      .catch(cause => { if (!cancelled) setError(cause.message || 'No se pudo consultar esta orden.') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [id, session?.accessToken])

  async function obtenerComprobanteAcademico() {
    if (!order || cargandoComprobante || !session?.accessToken) return
    setCargandoComprobante(true)
    setErrorComprobante('')
    try {
      // Paso 1: POST /api/v1/boletas usando pedidoId (idempotente)
      const boletaEmitida = await emitirBoleta(order.id, session.accessToken)
      setBoleta(boletaEmitida)

      // Paso 2: POST /api/v1/detalles-boleta usando boletaId (idempotente)
      const lineas = await generarDetalleBoleta(boletaEmitida.id, session.accessToken)
      setDetalles(Array.isArray(lineas) ? lineas : [])
    } catch (cause) {
      setErrorComprobante(cause.message || 'No se pudo generar el comprobante académico.')
    } finally {
      setCargandoComprobante(false)
    }
  }

  const esPropietario = session?.rolUsuario === 'CLIENTE'

  return <section>
    <div className="page-title">
      <span className="eyebrow">Detalle desde el backend independiente</span>
      <h1>Pedido #{id}</h1>
      <p>Servicio Pedidos (:8082) valida que el cliente sea propietario o que su rol tenga permiso de lectura.</p>
    </div>
    <p><Link className="text-link" to="/pedidos">← Volver a órdenes</Link></p>
    {loading && <div className="panel" role="status">Consultando pedido…</div>}
    {error && <div className="panel error-panel" role="alert">{error}</div>}
    {!loading && !error && order && <>
      <article className="panel order-card">
        <span className="eyebrow">Estado actual: {order.estado}</span>
        <h2>Destino: {order.planetaDestino}</h2>
        <p>Fecha registrada: {order.creadoEn ? new Date(order.creadoEn).toLocaleString('es-CL') : 'No informada'}</p>
        {session?.rolUsuario !== 'CLIENTE' && <p>Cliente #{order.clienteId}</p>}
        <ul className="order-items">{order.productos.map(item =>
          <li key={item.productoId}>{item.nombreProducto} — {item.cantidad} × {formatGalatiCoins(item.precioUnitario)} = {formatGalatiCoins(item.subtotal)}</li>
        )}</ul>
        <p><strong>Total: {formatGalatiCoins(order.total)}</strong></p>
        <p className="small">La reserva está registrada en Pedidos (:8082). No representa pago o entrega.</p>
      </article>

      {esPropietario && (
        <article className="panel comprobante-section" style={{ marginTop: '24px' }}>
          <span className="eyebrow">Comprobante académico independiente</span>
          <h2>Comprobante de compra</h2>
          <p className="small">
            Emisión académica inmutable vinculada a microservicios autónomos (Boleta :8083 y Detalle Boleta :8084).
            <br />
            <strong>Documento estrictamente académico (no tributario). No constituye boleta fiscal ni acredita pago o entrega.</strong>
          </p>

          {!boleta && (
            <div style={{ marginTop: '16px' }}>
              <button
                type="button"
                className="button button-primary"
                onClick={obtenerComprobanteAcademico}
                disabled={cargandoComprobante}
              >
                {cargandoComprobante ? 'Emitiendo comprobante…' : 'Obtener comprobante académico'}
              </button>
            </div>
          )}

          {cargandoComprobante && <p className="small" role="status" style={{ marginTop: '12px' }}>Contactando microservicios Boleta y Detalle…</p>}
          {errorComprobante && <div className="panel error-panel" role="alert" style={{ marginTop: '16px' }}>{errorComprobante}</div>}

          {boleta && (
            <div className="panel" style={{ marginTop: '16px', background: 'rgba(255,255,255,0.7)', border: '1px solid #c9d8c5' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '8px' }}>
                <div>
                  <span className="eyebrow" style={{ color: '#4a6b4e' }}>{boleta.tipoDocumento}</span>
                  <h3 style={{ margin: '4px 0 8px 0', fontFamily: 'Georgia, serif' }}>Boleta #{boleta.id}</h3>
                  <p className="small" style={{ margin: '2px 0' }}>Pedido asociado: #{boleta.pedidoId} · Cliente #{boleta.clienteId}</p>
                  <p className="small" style={{ margin: '2px 0' }}>Emitida en: {boleta.emitidaEn ? new Date(boleta.emitidaEn).toLocaleString('es-CL') : 'No informada'}</p>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <span className="eyebrow">Total Boleta</span>
                  <span style={{ fontSize: '20px', fontWeight: 'bold', color: '#2c4d34', display: 'block' }}>{formatGalatiCoins(boleta.total)}</span>
                  <p className="small" style={{ margin: '2px 0' }}>Moneda: {boleta.moneda}</p>
                </div>
              </div>

              <div style={{ marginTop: '16px', borderTop: '1px solid #dce3d6', paddingTop: '12px' }}>
                <h4 style={{ margin: '0 0 8px 0', fontSize: '14px' }}>Líneas de Detalle Verificadas ({detalles.length})</h4>
                {detalles.length === 0 ? (
                  <p className="small">No se registraron líneas de detalle.</p>
                ) : (
                  <ul className="order-items" style={{ margin: 0 }}>
                    {detalles.map((linea) => (
                      <li key={linea.id || linea.numeroLinea}>
                        Línea #{linea.numeroLinea}: {linea.nombreProducto} (ID #{linea.productoId}) — {linea.cantidadProducto} × {formatGalatiCoins(linea.precioUnitario)} = <strong>{formatGalatiCoins(linea.totalVenta)}</strong>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <div style={{ marginTop: '16px', display: 'flex', gap: '8px', alignItems: 'center' }}>
                <button
                  type="button"
                  className="button button-quiet"
                  onClick={obtenerComprobanteAcademico}
                  disabled={cargandoComprobante}
                >
                  {cargandoComprobante ? 'Actualizando…' : 'Reverificar comprobante'}
                </button>
              </div>

              <p className="small" style={{ marginTop: '14px', fontStyle: 'italic', color: '#556b57' }}>
                Aviso académico: Comprobante registrado en la base de datos `huertohogar_boleta` y líneas en `huertohogar_detalle_boleta`. Simulación docente de facturación sin valor fiscal.
              </p>
            </div>
          )}
        </article>
      )}
    </>}
  </section>
}
