import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../state'
import { ErrorNote, Field } from '../components/ui'

const demoLogins = [
  ['agent1', 'agent123', 'Agent, Hudson Travel'],
  ['supervisor', 'super123', 'Supervisor, Hudson Travel'],
  ['mexagent', 'agent123', 'Agent, Viajes Pirámide'],
  ['admin', 'admin123', 'GDS administrator'],
]

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('agent1')
  const [password, setPassword] = useState('agent123')
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(undefined)
    try {
      await login(username, password)
      navigate('/search')
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login">
      <form className="panel login-card" onSubmit={submit}>
        <h1>GDS<span>Console</span></h1>
        <p className="muted">Sign in with your agent ID.</p>
        <Field label="Username">
          <input value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" autoFocus />
        </Field>
        <Field label="Password">
          <input type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" />
        </Field>
        <ErrorNote error={error} />
        <button className="primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <div className="demo">
          <span className="muted">Demo logins</span>
          {demoLogins.map(([u, p, label]) => (
            <button type="button" key={u} className="chip" onClick={() => { setUsername(u); setPassword(p) }}>
              <b>{u}</b> {label}
            </button>
          ))}
        </div>
        <Link to="/dashboard" className="muted small">Open the live dashboard without signing in</Link>
      </form>
    </div>
  )
}
