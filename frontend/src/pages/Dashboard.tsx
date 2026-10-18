import { useEffect, useRef, useState } from 'react'
import { api } from '../api'
import type { LiveMetrics, SystemStats } from '../api'
import { Badge, ErrorNote, Panel, Sparkline } from '../components/ui'

const POLL_MS = 2000
const HISTORY = 90 // three minutes of samples

interface Sample {
  rps: number
  latencyMs: number
  hitRate: number // percent, for the interval
  dbActive: number
  heapMb: number
  threads: number
}

/** Turns two counter snapshots into per-interval rates. */
function sample(prev: LiveMetrics, cur: LiveMetrics): Sample {
  const seconds = Math.max((cur.timestamp - prev.timestamp) / 1000, 0.001)
  const requests = Math.max(cur.requests - prev.requests, 0)
  const hits = Math.max(cur.cacheHits - prev.cacheHits, 0)
  const lookups = hits + Math.max(cur.cacheMisses - prev.cacheMisses, 0)
  return {
    rps: requests / seconds,
    latencyMs: requests ? ((cur.requestSeconds - prev.requestSeconds) / requests) * 1000 : 0,
    hitRate: lookups ? (hits / lookups) * 100 : NaN,
    dbActive: cur.dbActive,
    heapMb: cur.heapUsedMb,
    threads: cur.platformThreads,
  }
}

const fmt = (n: number, digits = 0) =>
  Number.isFinite(n) ? n.toLocaleString('en-US', { maximumFractionDigits: digits, minimumFractionDigits: digits }) : '–'

const uptime = (s: number) => {
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  return h ? `${h}h ${m}m` : `${m}m ${Math.floor(s % 60)}s`
}

export default function Dashboard() {
  const [stats, setStats] = useState<SystemStats>()
  const [latest, setLatest] = useState<LiveMetrics>()
  const [history, setHistory] = useState<Sample[]>([])
  const [error, setError] = useState<string>()
  const previous = useRef<LiveMetrics>(undefined)

  useEffect(() => {
    api.stats().then(setStats).catch(e => setError(String(e.message ?? e)))
  }, [])

  useEffect(() => {
    let stopped = false
    const poll = () => {
      api.metrics()
        .then(m => {
          if (stopped) return
          setError(undefined)
          const prev = previous.current
          if (prev) setHistory(h => [...h, sample(prev, m)].slice(-HISTORY))
          previous.current = m
          setLatest(m)
        })
        .catch(e => !stopped && setError(`Backend unreachable: ${e.message ?? e}`))
    }
    poll()
    const timer = setInterval(poll, POLL_MS)
    return () => { stopped = true; clearInterval(timer) }
  }, [])

  const now = history[history.length - 1]
  const totalLookups = latest ? latest.cacheHits + latest.cacheMisses : 0
  const overallHitRate = latest && totalLookups ? (latest.cacheHits / totalLookups) * 100 : NaN
  const series = (pick: (s: Sample) => number) => history.map(s => (Number.isFinite(pick(s)) ? pick(s) : 0))

  return (
    <>
      <div className="dash-head">
        <h1>Live system</h1>
        <span className="muted small">
          Polling every {POLL_MS / 1000}s{latest && <> · up {uptime(latest.uptimeSeconds)}</>}
        </span>
        {stats && (
          <span className="push inline">
            <Badge tone="accent">Java {stats.runtime.javaVersion.split(/[+-]/)[0]}</Badge>
            <Badge tone={stats.runtime.virtualThread ? 'ok' : 'warn'}>
              {stats.runtime.virtualThread ? 'Virtual threads' : 'Platform threads'}
            </Badge>
            <Badge tone={stats.runtime.redisUp ? 'ok' : 'bad'}>{stats.runtime.redisUp ? 'Redis up' : 'Redis down'}</Badge>
          </span>
        )}
      </div>
      <ErrorNote error={error} />

      <div className="tiles">
        <Tile label="Requests per second" value={fmt(now?.rps ?? 0, 1)} values={series(s => s.rps)}
              foot={latest && `${fmt(latest.requests)} total`} />
        <Tile label="Average latency" value={`${fmt(now?.latencyMs ?? 0, 1)} ms`} values={series(s => s.latencyMs)}
              foot={latest && latest.requests ? `${fmt((latest.requestSeconds / latest.requests) * 1000, 1)} ms since start` : undefined} />
        <Tile label="Cache hit rate" value={`${fmt(now?.hitRate ?? NaN, 1)}%`} values={series(s => s.hitRate)}
              foot={latest && `${fmt(overallHitRate, 1)}% overall · ${fmt(totalLookups)} lookups`} />
        <Tile label="DB connections in use" value={fmt(latest?.dbActive ?? 0)} values={series(s => s.dbActive)}
              foot={latest && `${latest.dbPending} waiting for a connection`} />
        <Tile label="Heap" value={`${fmt(latest?.heapUsedMb ?? 0)} MB`} values={series(s => s.heapMb)}
              foot={latest && `of ${fmt(latest.heapMaxMb)} MB`} />
        <Tile label="Platform threads" value={fmt(latest?.platformThreads ?? 0)} values={series(s => s.threads)}
              foot="Virtual threads are not counted here" />
      </div>

      <div className="columns">
        <Panel title="PNRs by status">
          {latest && (
            <dl>
              {(['HELD', 'TICKETED', 'CANCELLED', 'EXPIRED'] as const).map(s => (
                <Row key={s} label={<Badge>{s}</Badge>} value={fmt(latest.pnrs[s] ?? 0)} />
              ))}
              <Row label="Request errors" value={fmt(latest.errors)} />
            </dl>
          )}
        </Panel>
        <Panel title="Inventory loaded">
          {stats && (
            <dl>
              {Object.entries(stats.rows).map(([k, v]) => <Row key={k} label={k.replace(/_/g, " ")} value={fmt(v)} />)}
              <Row label="Dates" value={`${stats.inventoryFrom} to ${stats.inventoryTo}`} />
              <Row label="CPUs" value={String(stats.runtime.cpus)} />
            </dl>
          )}
        </Panel>
      </div>
    </>
  )
}

function Tile({ label, value, values, foot }: { label: string; value: string; values: number[]; foot?: string }) {
  return (
    <div className="tile">
      <span className="label">{label}</span>
      <span className="value">{value}</span>
      <Sparkline values={values} />
      <span className="foot muted small">{foot ?? ' '}</span>
    </div>
  )
}

function Row({ label, value }: { label: React.ReactNode; value: string }) {
  return <><dt>{label}</dt><dd className="num">{value}</dd></>
}
