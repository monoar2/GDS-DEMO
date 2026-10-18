import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import type { AirOffer, AirSearch, Cabin, CarOffer, HotelOffer } from '../api'
import { useBasket } from '../state'
import { AirportSelect, Badge, Empty, ErrorNote, Field, Panel, Stepper } from '../components/ui'
import { addDays, cabinLabel, duration, money, plusDays, shortDate, time, today } from '../format'

type Tab = 'air' | 'hotels' | 'cars'

export default function Search() {
  const [tab, setTab] = useState<Tab>('air')
  const [added, setAdded] = useState<string>()
  const { count } = useBasket()

  const notify = (what: string) => {
    setAdded(what)
    window.setTimeout(() => setAdded(undefined), 4000)
  }

  return (
    <>
      <div className="tabs">
        <button className={tab === 'air' ? 'active' : ''} onClick={() => setTab('air')}>Flights</button>
        <button className={tab === 'hotels' ? 'active' : ''} onClick={() => setTab('hotels')}>Hotels</button>
        <button className={tab === 'cars' ? 'active' : ''} onClick={() => setTab('cars')}>Cars</button>
        {count > 0 && <Link to="/booking" className="tabs-cta">Review booking ({count}) →</Link>}
      </div>
      {added && <div className="toast" role="status">{added} added to the booking. <Link to="/booking">Review it</Link></div>}
      {tab === 'air' && <AirSearchPanel onAdded={notify} />}
      {tab === 'hotels' && <HotelSearchPanel onAdded={notify} />}
      {tab === 'cars' && <CarSearchPanel onAdded={notify} />}
    </>
  )
}

function useSearch<T>() {
  const [result, setResult] = useState<T>()
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const [elapsed, setElapsed] = useState(0)
  const run = async (load: () => Promise<T>) => {
    setBusy(true)
    setError(undefined)
    const started = performance.now()
    try {
      setResult(await load())
      setElapsed(performance.now() - started)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      setResult(undefined)
    } finally {
      setBusy(false)
    }
  }
  return { result, error, busy, elapsed, run }
}

// ---------------------------------------------------------------- flights

function AirSearchPanel({ onAdded }: { onAdded: (what: string) => void }) {
  const { addFlight } = useBasket()
  const [form, setForm] = useState<AirSearch>({
    origin: 'MEX', destination: 'LHR', date: addDays(today(), 1), returnDate: addDays(today(), 8),
    adults: 1, children: 0, infants: 0, cabin: 'ECONOMY',
  })
  const [roundTrip, setRoundTrip] = useState(true)
  const search = useSearch<{ outbound: AirOffer[]; inbound: AirOffer[] }>()
  const set = (patch: Partial<AirSearch>) => setForm(f => ({ ...f, ...patch }))

  const submit = (e: FormEvent) => {
    e.preventDefault()
    search.run(() => api.shopAir({ ...form, returnDate: roundTrip ? form.returnDate : undefined }))
  }
  const party = { adults: form.adults, children: form.children, infants: form.infants }
  const add = (offer: AirOffer) => {
    addFlight(offer, party)
    onAdded(offer.segments.map(s => s.flight).join(' + '))
  }

  return (
    <>
      <Panel>
        <form className="query" onSubmit={submit}>
          <Field label="From"><AirportSelect value={form.origin} onChange={e => set({ origin: e.target.value })} /></Field>
          <button type="button" className="swap" title="Swap"
                  onClick={() => set({ origin: form.destination, destination: form.origin })}>⇄</button>
          <Field label="To"><AirportSelect value={form.destination} onChange={e => set({ destination: e.target.value })} /></Field>
          <Field label="Depart"><input type="date" value={form.date} min={today()} onChange={e => set({ date: e.target.value })} /></Field>
          <Field label={<><input type="checkbox" checked={roundTrip} onChange={e => setRoundTrip(e.target.checked)} /> Return</>}>
            <input type="date" value={form.returnDate} min={form.date} disabled={!roundTrip}
                   onChange={e => set({ returnDate: e.target.value })} />
          </Field>
          <Field label="Adults"><Stepper value={form.adults} min={1} onChange={adults => set({ adults, infants: Math.min(form.infants, adults) })} /></Field>
          <Field label="Children"><Stepper value={form.children} max={8} onChange={children => set({ children })} /></Field>
          <Field label="Infants"><Stepper value={form.infants} max={form.adults} onChange={infants => set({ infants })} /></Field>
          <Field label="Cabin">
            <select value={form.cabin} onChange={e => set({ cabin: e.target.value as Cabin })}>
              {Object.entries(cabinLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </Field>
          <button className="primary" disabled={search.busy}>{search.busy ? 'Searching…' : 'Search flights'}</button>
        </form>
        <ErrorNote error={search.error} />
      </Panel>

      {search.result && (
        <div className={roundTrip && search.result.inbound.length + search.result.outbound.length > 0 ? 'columns' : ''}>
          <OfferList title={`${form.origin} → ${form.destination} · ${shortDate(form.date)}`}
                     offers={search.result.outbound} elapsed={search.elapsed} onAdd={add} />
          {roundTrip && (
            <OfferList title={`${form.destination} → ${form.origin} · ${shortDate(form.returnDate!)}`}
                       offers={search.result.inbound} onAdd={add} />
          )}
        </div>
      )}
    </>
  )
}

function OfferList({ title, offers, elapsed, onAdd }: {
  title: string; offers: AirOffer[]; elapsed?: number; onAdd: (o: AirOffer) => void
}) {
  return (
    <Panel title={title} actions={elapsed ? <span className="muted small">{offers.length} options in {elapsed.toFixed(0)} ms</span> : undefined}>
      {offers.length === 0 && <Empty>No nonstop or one-stop flights with seats in this cabin. Try another date or cabin.</Empty>}
      {offers.map((offer, i) => <OfferCard key={i} offer={offer} onAdd={() => onAdd(offer)} />)}
    </Panel>
  )
}

function OfferCard({ offer, onAdd }: { offer: AirOffer; onAdd: () => void }) {
  const first = offer.segments[0]
  const last = offer.segments[offer.segments.length - 1]
  const seatsLeft = Math.min(...offer.segments.map(s => s.seatsAvailable))
  const family = offer.segments[0].rules.fareFamily
  return (
    <article className="offer">
      <div className="offer-main">
        <div className="offer-times">
          <b>{time(first.departure)}</b>
          <span className="line">
            <span className="muted small">{duration(offer.totalDurationMinutes)}</span>
            <span className="track">{offer.segments.slice(1).map(s => <i key={s.flightId} title={s.origin} />)}</span>
            <span className="muted small">{offer.stops === 0 ? 'Nonstop' : `1 stop · ${offer.segments[1].origin}`}</span>
          </span>
          <b>{time(last.arrival)}<sup>{plusDays(first.departure, last.arrival)}</sup></b>
        </div>
        <div className="offer-legs">
          {offer.segments.map(s => (
            <span key={s.flightId} className="leg">
              <b>{s.flight}</b> {s.origin}–{s.destination} {time(s.departure)} · class {s.bookingClass} · {s.aircraft}
            </span>
          ))}
        </div>
      </div>
      <div className="offer-side">
        <span className="price">{money(offer.price.grandTotal, offer.price.currency)}</span>
        <span className="muted small">total incl. {money(offer.price.taxTotal)} taxes</span>
        <span className="tags">
          <Badge tone={family === 'BASIC' ? 'bad' : family === 'FLEX' ? 'ok' : 'muted'}>{family}</Badge>
          {seatsLeft <= 4 && <Badge tone="warn">{seatsLeft} left</Badge>}
        </span>
        <button onClick={onAdd}>Add</button>
      </div>
    </article>
  )
}

// ---------------------------------------------------------------- hotels

function HotelSearchPanel({ onAdded }: { onAdded: (what: string) => void }) {
  const { addHotel } = useBasket()
  const [city, setCity] = useState('LHR')
  const [checkIn, setCheckIn] = useState(addDays(today(), 1))
  const [checkOut, setCheckOut] = useState(addDays(today(), 4))
  const [rooms, setRooms] = useState(1)
  const [guests, setGuests] = useState(2)
  const search = useSearch<HotelOffer[]>()

  const submit = (e: FormEvent) => {
    e.preventDefault()
    search.run(() => api.shopHotels(city, checkIn, checkOut, rooms, guests))
  }

  return (
    <>
      <Panel>
        <form className="query" onSubmit={submit}>
          <Field label="City"><AirportSelect value={city} onChange={e => setCity(e.target.value)} /></Field>
          <Field label="Check-in"><input type="date" value={checkIn} min={today()} onChange={e => setCheckIn(e.target.value)} /></Field>
          <Field label="Check-out"><input type="date" value={checkOut} min={addDays(checkIn, 1)} onChange={e => setCheckOut(e.target.value)} /></Field>
          <Field label="Rooms"><Stepper value={rooms} min={1} onChange={setRooms} /></Field>
          <Field label="Guests"><Stepper value={guests} min={1} max={rooms * 4} onChange={setGuests} /></Field>
          <button className="primary" disabled={search.busy}>{search.busy ? 'Searching…' : 'Search hotels'}</button>
        </form>
        <ErrorNote error={search.error} />
      </Panel>
      {search.result && (
        <Panel title={`${search.result.length} hotels with rooms every night`}
               actions={<span className="muted small">{search.elapsed.toFixed(0)} ms</span>}>
          {search.result.length === 0 && <Empty>Nothing free for the whole stay.</Empty>}
          {search.result.map(h => (
            <article key={h.hotelId} className="card">
              <header>
                <strong>{h.name}</strong>
                <span className="stars">{'★'.repeat(h.stars)}</span>
                <span className="muted small">{h.propertyCode} · {h.address}</span>
              </header>
              <table>
                <tbody>
                  {h.rooms.map(r => (
                    <tr key={r.roomTypeId}>
                      <td><b>{r.code}</b></td>
                      <td>{r.description}</td>
                      <td className="muted">{r.roomsLeft} left</td>
                      <td className="num">{money(r.price.rate)} / night</td>
                      <td className="num"><b>{money(r.price.total)}</b> <span className="muted small">{r.price.nights} nights</span></td>
                      <td className="num">
                        <button onClick={() => {
                          addHotel({ hotel: h, room: r, checkIn, checkOut, rooms })
                          onAdded(`${h.name}, ${r.code}`)
                        }}>Add</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </article>
          ))}
        </Panel>
      )}
    </>
  )
}

// ---------------------------------------------------------------- cars

function CarSearchPanel({ onAdded }: { onAdded: (what: string) => void }) {
  const { addCar } = useBasket()
  const [location, setLocation] = useState('LHR')
  const [pickup, setPickup] = useState(addDays(today(), 1))
  const [days, setDays] = useState(3)
  const search = useSearch<CarOffer[]>()

  const submit = (e: FormEvent) => {
    e.preventDefault()
    search.run(() => api.shopCars(location, pickup, days))
  }

  return (
    <>
      <Panel>
        <form className="query" onSubmit={submit}>
          <Field label="Pick-up at"><AirportSelect value={location} onChange={e => setLocation(e.target.value)} /></Field>
          <Field label="Pick-up date"><input type="date" value={pickup} min={today()} onChange={e => setPickup(e.target.value)} /></Field>
          <Field label="Days"><Stepper value={days} min={1} max={30} onChange={setDays} /></Field>
          <button className="primary" disabled={search.busy}>{search.busy ? 'Searching…' : 'Search cars'}</button>
        </form>
        <ErrorNote error={search.error} />
      </Panel>
      {search.result && (
        <Panel title={`${search.result.length} cars available every day`}
               actions={<span className="muted small">{search.elapsed.toFixed(0)} ms</span>}>
          {search.result.length === 0 && <Empty>No cars free for every day of the rental.</Empty>}
          {search.result.length > 0 && (
            <table className="full">
              <thead><tr><th>Vendor</th><th>ACRISS</th><th>Car</th><th>Left</th><th className="num">Per day</th><th className="num">Total</th><th /></tr></thead>
              <tbody>
                {search.result.map(c => (
                  <tr key={c.vendor + c.sipp}>
                    <td>{c.vendor}</td>
                    <td><b>{c.sipp}</b></td>
                    <td>{c.sampleModel}</td>
                    <td className="muted">{c.unitsLeft}</td>
                    <td className="num">{money(c.price.rate)}</td>
                    <td className="num"><b>{money(c.price.total)}</b></td>
                    <td className="num"><button onClick={() => { addCar(c); onAdded(`${c.vendor} ${c.sipp}`) }}>Add</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Panel>
      )}
    </>
  )
}
