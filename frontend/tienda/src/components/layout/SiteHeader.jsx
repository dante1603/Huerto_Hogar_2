import { Link, NavLink } from 'react-router-dom'

export default function SiteHeader({ session, cartCount }) {
  return (
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
  )
}
