import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api'
import type { Agency, AgentAccount, Role } from '../api'
import { Badge, Empty, ErrorNote, Field, Panel } from '../components/ui'

export default function Admin() {
  const [agencies, setAgencies] = useState<Agency[]>([])
  const [selected, setSelected] = useState<number>()
  const [error, setError] = useState<string>()
  const fail = (e: unknown) => setError(e instanceof Error ? e.message : String(e))

  const loadAgencies = useCallback(() => {
    api.agencies().then(list => {
      setAgencies(list)
      setSelected(current => current ?? list[0]?.id)
    }).catch(fail)
  }, [])

  useEffect(() => { loadAgencies() }, [loadAgencies])

  const [pcc, setPcc] = useState('')
  const [name, setName] = useState('')
  const [country, setCountry] = useState('US')
  const addAgency = (e: FormEvent) => {
    e.preventDefault()
    setError(undefined)
    api.createAgency(pcc.toUpperCase(), name, country.toUpperCase())
      .then(a => { setPcc(''); setName(''); setSelected(a.id); loadAgencies() })
      .catch(fail)
  }

  const toggleAgency = (a: Agency) => api.setAgencyActive(a.id, !a.active).then(loadAgencies).catch(fail)
  const agency = agencies.find(a => a.id === selected)

  return (
    <div className="columns">
      <div className="stack">
        <Panel title="Agencies">
          <table className="full clickable">
            <thead><tr><th>PCC</th><th>Name</th><th>Country</th><th>Status</th><th /></tr></thead>
            <tbody>
              {agencies.map(a => (
                <tr key={a.id} className={a.id === selected ? 'selected' : ''} onClick={() => setSelected(a.id)}>
                  <td className="mono"><b>{a.pcc}</b></td>
                  <td>{a.name}</td>
                  <td>{a.country}</td>
                  <td><Badge tone={a.active ? 'ok' : 'muted'}>{a.active ? 'Active' : 'Inactive'}</Badge></td>
                  <td className="num">
                    <button className="link" onClick={e => { e.stopPropagation(); toggleAgency(a) }}>
                      {a.active ? 'Deactivate' : 'Activate'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>
        <Panel title="New agency">
          <form className="query" onSubmit={addAgency}>
            <Field label="PCC"><input required pattern="[A-Za-z0-9]{4,9}" title="4 to 9 letters or digits" maxLength={9} size={8} value={pcc} onChange={e => setPcc(e.target.value)} /></Field>
            <Field label="Name"><input required value={name} onChange={e => setName(e.target.value)} /></Field>
            <Field label="Country"><input required maxLength={2} size={3} value={country} onChange={e => setCountry(e.target.value)} /></Field>
            <button className="primary">Create agency</button>
          </form>
        </Panel>
        <ErrorNote error={error} />
      </div>
      {agency && <Agents key={agency.id} agency={agency} onError={fail} />}
    </div>
  )
}

function Agents({ agency, onError }: { agency: Agency; onError: (e: unknown) => void }) {
  const [agents, setAgents] = useState<AgentAccount[]>()
  const load = useCallback(() => { api.agents(agency.id).then(setAgents).catch(onError) }, [agency.id, onError])
  useEffect(() => { load() }, [load])

  const [username, setUsername] = useState('')
  const [fullName, setFullName] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('AGENT')

  const add = (e: FormEvent) => {
    e.preventDefault()
    api.createAgent({ username, password, fullName, role, agencyId: agency.id })
      .then(() => { setUsername(''); setFullName(''); setPassword(''); load() })
      .catch(onError)
  }

  const toggle = (a: AgentAccount) => api.setAgentActive(a.id, !a.active).then(load).catch(onError)

  return (
    <div className="stack">
      <Panel title={`Agents at ${agency.name} (${agency.pcc})`}>
        {agents && agents.length === 0 && <Empty>No agents yet.</Empty>}
        {agents && agents.length > 0 && (
          <table className="full">
            <thead><tr><th>Username</th><th>Name</th><th>Role</th><th>Status</th><th /></tr></thead>
            <tbody>
              {agents.map(a => (
                <tr key={a.id}>
                  <td className="mono">{a.username}</td>
                  <td>{a.fullName}</td>
                  <td><Badge>{a.role}</Badge></td>
                  <td><Badge tone={a.active ? 'ok' : 'muted'}>{a.active ? 'Active' : 'Inactive'}</Badge></td>
                  <td className="num"><button className="link" onClick={() => toggle(a)}>{a.active ? 'Deactivate' : 'Activate'}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
      <Panel title="New agent">
        <form className="query" onSubmit={add}>
          <Field label="Username"><input required minLength={3} value={username} onChange={e => setUsername(e.target.value)} /></Field>
          <Field label="Full name"><input required value={fullName} onChange={e => setFullName(e.target.value)} /></Field>
          <Field label="Password"><input required type="password" minLength={6} value={password} onChange={e => setPassword(e.target.value)} /></Field>
          <Field label="Role">
            <select value={role} onChange={e => setRole(e.target.value as Role)}>
              <option>AGENT</option><option>SUPERVISOR</option><option>ADMIN</option>
            </select>
          </Field>
          <button className="primary">Create agent</button>
        </form>
      </Panel>
    </div>
  )
}
