import { Link, NavLink } from 'react-router-dom'
import { activeRole, homeForRole, ROLE_CLIENTE, ROLE_VENDEDOR, ROLE_ADMIN } from '../../lib/roles.js'

export default function SiteHeader({ session, cartCount }) {
  const role = activeRole(session)
  const isShop = role === null || role === ROLE_CLIENTE || role === ROLE_ADMIN
  const isStaff = role === ROLE_VENDEDOR || role === ROLE_ADMIN

  return (
    <header className="site-header">
      <div className="shell topbar">
        <Link className="brand" to={homeForRole(role)} aria-label="HuertoHogar, inicio de mi área">
          <span className="brand-symbol" aria-hidden="true">✳</span>
          <span><strong>HuertoHogar</strong><small>Galaxym · Mercadería interplanetaria</small></span>
        </Link>
        <nav className="nav-links" aria-label="Navegación principal">
          {isShop && <>
            <NavLink to="/" end>Inicio</NavLink>
            <NavLink to="/productos">Productos</NavLink>
            <NavLink to="/nosotros">Nosotros</NavLink>
            {role !== ROLE_ADMIN && <NavLink to="/carrito">Carrito <span className="count">{cartCount}</span></NavLink>}
          </>}
          {isStaff && <NavLink to="/consulta">Consultar productos</NavLink>}
          {role === ROLE_ADMIN && <NavLink to="/administracion">Administrar productos</NavLink>}
          {role && <NavLink to="/pedidos">{role === ROLE_CLIENTE ? 'Mis pedidos' : 'Órdenes'}</NavLink>}
          <NavLink to="/acceso">{role ? 'Mi cuenta' : 'Ingresar'}</NavLink>
        </nav>
      </div>
    </header>
  )
}
