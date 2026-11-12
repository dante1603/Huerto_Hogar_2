import { useState } from 'react'
import { loginUsuario, registrarUsuario } from '../lib/api.js'

export default function Acceso({ session, onAuthenticated, onLogout }) {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ email: '', contrasena: '', nombreUsuario: '', rutUsuario: '', direccionUsuario: '' })
  const [status, setStatus] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    setStatus('')
    try {
      if (mode === 'login') {
        const data = await loginUsuario(form.email, form.contrasena)
        onAuthenticated(data)
        setForm((v) => ({ ...v, contrasena: '' }))
      } else {
        await registrarUsuario({
          nombreUsuario: form.nombreUsuario, rutUsuario: form.rutUsuario,
          direccionUsuario: form.direccionUsuario,
          emailUsuario: form.email, contrasena: form.contrasena,
        })
        setStatus('Cuenta creada. Ya puedes iniciar sesión.')
        setMode('login')
        setForm((v) => ({ ...v, contrasena: '' }))
      }
    } catch (error) {
      setStatus(error.message || 'No se pudo completar la operación.')
    } finally {
      setBusy(false)
    }
  }

  const update = (field) => (event) => setForm((v) => ({ ...v, [field]: event.target.value }))
  return <section className="auth-layout">
    <div className="panel auth-panel">
      <span className="eyebrow">Estación de acceso</span>
      {session ? <>
        <h1>Sesión iniciada</h1>
        <p>Usuario #{session.idUsuario} · {session.rolUsuario}</p>
        <p className="small">El token permanece en esta pestaña mientras la sesión esté vigente. Ya puedes reservar productos mediante pedidos; el repartidor todavía no recibe misiones en Galaxym.</p>
        <button type="button" className="button button-primary" onClick={onLogout}>Cerrar sesión</button>
      </> : <>
        <h1>{mode === 'login' ? 'Iniciar sesión' : 'Crear cuenta'}</h1>
        <div className="filter-bar"><button className={mode === 'login' ? 'chip selected' : 'chip'} type="button" onClick={() => { setMode('login'); setStatus('') }}>Ingresar</button><button className={mode === 'registro' ? 'chip selected' : 'chip'} type="button" onClick={() => { setMode('registro'); setStatus('') }}>Registrarse</button></div>
        <form className="auth-form" onSubmit={submit}>
          {mode === 'registro' && <>
            <label>Nombre completo<input required maxLength={120} autoComplete="name" value={form.nombreUsuario} onChange={update('nombreUsuario')} /></label>
            <label>RUT<input required placeholder="12345678-5" value={form.rutUsuario} onChange={update('rutUsuario')} /></label>
            <label>Dirección<input required maxLength={240} autoComplete="street-address" value={form.direccionUsuario} onChange={update('direccionUsuario')} /></label>
          </>}
          <label>Correo electrónico<input type="email" required maxLength={180} autoComplete="email" value={form.email} onChange={update('email')} /></label>
          <label>Contraseña<input type="password" required minLength={mode === 'registro' ? 8 : undefined} autoComplete={mode === 'registro' ? 'new-password' : 'current-password'} value={form.contrasena} onChange={update('contrasena')} /></label>
          <button className="button button-primary" type="submit" disabled={busy}>{busy ? 'Conectando…' : mode === 'login' ? 'Ingresar' : 'Crear cuenta'}</button>
          {status && <p role="status" className="form-status">{status}</p>}
        </form>
        <p className="small">Este formulario habla directamente con el microservicio Usuarios; no existe inicio de sesión simulado.</p>
      </>}
    </div>
    <aside className="auth-aside"><span aria-hidden="true">☘</span><h2>Desde tu planeta hasta tu puerta.</h2><p>Los mejores cultivos de Bruma, Árido y Yermo en una sola estación.</p></aside>
  </section>
}
