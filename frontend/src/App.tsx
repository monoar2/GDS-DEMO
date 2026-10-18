import { Link, NavLink, Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { useAuth, useBasket } from './state'
import { Badge } from './components/ui'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Search from './pages/Search'
import Booking from './pages/Booking'
import PnrList from './pages/PnrList'
import PnrDetail from './pages/PnrDetail'
import Queues from './pages/Queues'
import Admin from './pages/Admin'
import Inventory from './pages/Inventory'

export default function App() {
  const { session, logout } = useAuth()
  const { count } = useBasket()
  const navigate = useNavigate()

  if (!session) {
    return (
      <Routes>
        <Route path="/dashboard" element={<Shell user={<Link className="user" to="/">Sign in</Link>}><Dashboard /></Shell>} />
        <Route path="*" element={<Login />} />
      </Routes>
    )
  }

  return (
    <Shell
      nav={
        <>
          <NavLink to="/search">Search</NavLink>
          <NavLink to="/booking">Booking{count > 0 && <span className="count">{count}</span>}</NavLink>
          <NavLink to="/pnrs">PNRs</NavLink>
          <NavLink to="/queues">Queues</NavLink>
          <NavLink to="/dashboard">Dashboard</NavLink>
          <NavLink to="/inventory">Inventory</NavLink>
          {session.role === 'ADMIN' && <NavLink to="/admin">Admin</NavLink>}
        </>
      }
      user={
        <div className="user">
          <span><b>{session.fullName}</b> · {session.pcc}</span>
          <Badge>{session.role}</Badge>
          <button className="link" onClick={() => { logout(); navigate('/') }}>Sign out</button>
        </div>
      }
    >
      <Routes>
        <Route path="/search" element={<Search />} />
        <Route path="/booking" element={<Booking />} />
        <Route path="/pnrs" element={<PnrList />} />
        <Route path="/pnrs/:locator" element={<PnrDetail />} />
        <Route path="/queues" element={<Queues />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/inventory" element={<Inventory />} />
        {session.role === 'ADMIN' && <Route path="/admin" element={<Admin />} />}
        <Route path="*" element={<Navigate to="/search" replace />} />
      </Routes>
    </Shell>
  )
}

function Shell({ nav, user, children }: { nav?: React.ReactNode; user?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="shell">
      <header className="topbar">
        <span className="brand">GDS<span>Console</span></span>
        <nav>{nav}</nav>
        {user}
      </header>
      <main>{children}</main>
    </div>
  )
}
