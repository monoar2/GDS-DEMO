export const today = () => new Date().toLocaleDateString('en-CA') // YYYY-MM-DD in local time

export const addDays = (isoDate: string, days: number) => {
  const d = new Date(isoDate + 'T12:00:00')
  d.setDate(d.getDate() + days)
  return d.toLocaleDateString('en-CA')
}

export const time = (iso: string) => iso.slice(11, 16)

export const shortDate = (iso: string) =>
  new Date(iso.length === 10 ? iso + 'T12:00:00' : iso).toLocaleDateString('en-US', { day: 'numeric', month: 'short' })

export const dateTime = (iso: string) =>
  new Date(iso).toLocaleString('en-US', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })

export const duration = (minutes: number) => `${Math.floor(minutes / 60)}h ${String(minutes % 60).padStart(2, '0')}m`

export const money = (amount: number, currency = 'USD') =>
  new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: 2 }).format(amount)

export const plusDays = (departure: string, arrival: string) => {
  const days = Math.round(
    (new Date(arrival.slice(0, 10) + 'T00:00:00').getTime() - new Date(departure.slice(0, 10) + 'T00:00:00').getTime()) /
      86_400_000,
  )
  return days > 0 ? ` +${days}` : ''
}

/** "12m 30s left" until a deadline, or "passed". */
export const countdown = (deadline: string, now: number) => {
  const ms = new Date(deadline).getTime() - now
  if (ms <= 0) return 'passed'
  const minutes = Math.floor(ms / 60_000)
  const seconds = Math.floor((ms % 60_000) / 1000)
  return minutes >= 60 ? `${Math.floor(minutes / 60)}h ${minutes % 60}m left` : `${minutes}m ${seconds}s left`
}

export const cabinLabel: Record<string, string> = {
  FIRST: 'First',
  BUSINESS: 'Business',
  PREMIUM_ECONOMY: 'Premium economy',
  ECONOMY: 'Economy',
}
