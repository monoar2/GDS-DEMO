// Typed client for the GDS backend. Every call goes through `request`, which adds the agent's token
// and turns problem responses ({"detail": "..."}) into readable errors.

export type Cabin = 'FIRST' | 'BUSINESS' | 'PREMIUM_ECONOMY' | 'ECONOMY'
export type PassengerType = 'ADT' | 'CHD' | 'INF'
export type Role = 'AGENT' | 'SUPERVISOR' | 'ADMIN'
export type PnrStatus = 'HELD' | 'TICKETED' | 'CANCELLED' | 'EXPIRED'
export type SegmentStatus = 'HK' | 'XX'
export type QueueName = 'TICKETING' | 'EXPIRED' | 'REFUNDS' | 'GENERAL'
export type SegmentType = 'AIR' | 'HOTEL' | 'CAR'

// ---------------------------------------------------------------- reference and raw inventory

export interface SystemStats {
  rows: Record<string, number>
  inventoryFrom: string
  inventoryTo: string
  runtime: { javaVersion: string; virtualThread: boolean; thread: string; cpus: number; redisUp: boolean }
}

export interface LiveMetrics {
  timestamp: number; uptimeSeconds: number; requests: number; requestSeconds: number; errors: number
  cacheHits: number; cacheMisses: number; dbActive: number; dbPending: number
  heapUsedMb: number; heapMaxMb: number; platformThreads: number; pnrs: Record<PnrStatus, number>
}

export interface Airport { code: string; name: string; city: string; country: string; timezone: string }

export interface BookingClass {
  code: string; cabin: Cabin; baseFare: number; currency: string; seatsAvailable: number; seatsTotal: number
}

export interface FlightInventory {
  flightId: number; flight: string; origin: string; destination: string; departure: string; arrival: string
  durationMinutes: number; aircraft: string; classes: BookingClass[]
}

export interface Room {
  roomTypeId: number
  code: string; description: string; maxOccupancy: number; nightlyRate: number; currency: string
  roomsAvailable: number; roomsTotal: number
}

export interface HotelInventory {
  hotelId: number; propertyCode: string; chain: string; name: string; city: string; stars: number
  address: string; rooms: Room[]
}

export interface CarInventory {
  vendor: string; location: string; pickupDate: string; sipp: string; sampleModel: string
  dailyRate: number; currency: string; unitsAvailable: number; unitsTotal: number
}

// ---------------------------------------------------------------- auth

export interface Session {
  token: string; expiresAt: string; username: string; fullName: string; role: Role; pcc: string; agencyName: string
}

// ---------------------------------------------------------------- shopping

export interface FareRules { fareFamily: string; refundable: boolean; cancelPenalty: number; changeFee: number }

export interface PaxPrice { type: PassengerType; count: number; baseFare: number; taxes: number; totalPerPax: number }

export interface AirPrice {
  passengers: PaxPrice[]; baseTotal: number; taxTotal: number; grandTotal: number; currency: string
}

export interface SegmentOffer {
  flightId: number; flight: string; origin: string; destination: string; departure: string; arrival: string
  durationMinutes: number; aircraft: string; bookingClass: string; cabin: Cabin; fareBasis: string
  seatsAvailable: number; baseFare: number; rules: FareRules
}

export interface AirOffer { segments: SegmentOffer[]; stops: number; totalDurationMinutes: number; price: AirPrice }

export interface StayPrice {
  units: number; nights: number; rate: number; base: number; taxes: number; total: number; currency: string
}

export interface RoomOffer {
  roomTypeId: number; code: string; description: string; maxOccupancy: number; roomsLeft: number; price: StayPrice
}

export interface HotelOffer {
  hotelId: number; propertyCode: string; chain: string; name: string; city: string; stars: number; address: string
  rooms: RoomOffer[]
}

export interface CarOffer {
  vendor: string; location: string; pickupDate: string; returnDate: string; sipp: string; sampleModel: string
  unitsLeft: number; price: StayPrice
}

export interface AirSearch {
  origin: string; destination: string; date: string; returnDate?: string
  adults: number; children: number; infants: number; cabin: Cabin
}

// ---------------------------------------------------------------- PNRs

export interface PassengerInput { type: PassengerType; firstName: string; lastName: string; dateOfBirth?: string }

export interface SegmentsInput {
  air?: { flightId: number; bookingClass: string }[]
  hotels?: { roomTypeId: number; checkIn: string; checkOut: string; rooms: number }[]
  cars?: { vendor: string; location: string; sipp: string; pickupDate: string; days: number }[]
}

export interface Pnr {
  recordLocator: string; status: PnrStatus; pcc: string; createdBy: string; createdAt: string
  ticketingDeadline: string | null; contactEmail: string | null; contactPhone: string | null
  passengers: { id: number; type: PassengerType; name: string; dateOfBirth: string | null }[]
  air: {
    id: number; flight: string; origin: string; destination: string; departure: string; arrival: string
    bookingClass: string; cabin: Cabin; fareBasis: string; seats: number; baseFare: number; rules: FareRules
    status: SegmentStatus
  }[]
  hotels: {
    id: number; propertyCode: string; hotelName: string; city: string; roomCode: string; checkIn: string
    checkOut: string; rooms: number; nightlyRate: number; total: number; confirmation: string; status: SegmentStatus
  }[]
  cars: {
    id: number; vendor: string; location: string; sipp: string; model: string; pickupDate: string; returnDate: string
    dailyRate: number; total: number; confirmation: string; status: SegmentStatus
  }[]
  airPrice: AirPrice | null; hotelTotal: number; carTotal: number; grandTotal: number; currency: string
  tickets: {
    number: string; passenger: string; fare: number; taxes: number; total: number; formOfPayment: string
    issuedAt: string; status: 'ISSUED' | 'VOIDED' | 'REFUNDED'; refundAmount: number | null
  }[]
  history: { at: string; actor: string; action: string; detail: string }[]
}

export interface PnrSummary {
  recordLocator: string; status: PnrStatus; leadPassenger: string; passengers: number; itinerary: string
  firstDeparture: string | null; createdAt: string; ticketingDeadline: string | null; createdBy: string
}

export interface Page<T> { content: T[]; totalElements: number; totalPages: number; number: number }

export interface QueueItem { id: number; queue: QueueName; recordLocator: string; remark: string; createdAt: string }

export interface Agency { id: number; pcc: string; name: string; country: string; active: boolean }
export interface AgentAccount {
  id: number; username: string; fullName: string; role: Role; agencyId: number; active: boolean
}

// ---------------------------------------------------------------- transport

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

let token: string | null = null
let onUnauthorized: () => void = () => {}

export function setToken(value: string | null) {
  token = value
}

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  if (response.status === 401 && token) {
    onUnauthorized()
  }
  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`
    try {
      const problem = await response.json()
      message = problem.detail ?? problem.message ?? message
    } catch {
      // not JSON, keep the status line
    }
    throw new ApiError(response.status, message)
  }
  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T
  }
  return response.json() as Promise<T>
}

const get = <T>(path: string) => request<T>('GET', path)
const qs = (params: Record<string, string | number | undefined>) =>
  new URLSearchParams(
    Object.entries(params).filter(([, v]) => v !== undefined && v !== '').map(([k, v]) => [k, String(v)]),
  ).toString()

export const api = {
  // public
  login: (username: string, password: string) => request<Session>('POST', '/api/auth/login', { username, password }),
  stats: () => get<SystemStats>('/api/system/stats'),
  metrics: () => get<LiveMetrics>('/api/system/metrics'),
  airports: () => get<Airport[]>('/api/reference/airports'),
  flights: (origin: string, destination: string, date: string) =>
    get<FlightInventory[]>(`/api/inventory/flights?${qs({ origin, destination, date })}`),
  hotels: (city: string, date: string) => get<HotelInventory[]>(`/api/inventory/hotels?${qs({ city, date })}`),
  cars: (location: string, date: string) => get<CarInventory[]>(`/api/inventory/cars?${qs({ location, date })}`),

  // shopping
  shopAir: (search: AirSearch) =>
    get<{ outbound: AirOffer[]; inbound: AirOffer[] }>(`/api/shopping/air?${qs({ ...search })}`),
  shopHotels: (city: string, checkIn: string, checkOut: string, rooms: number, guests: number) =>
    get<HotelOffer[]>(`/api/shopping/hotels?${qs({ city, checkIn, checkOut, rooms, guests })}`),
  shopCars: (location: string, pickup: string, days: number) =>
    get<CarOffer[]>(`/api/shopping/cars?${qs({ location, pickup, days })}`),

  // PNRs
  createPnr: (body: { passengers: PassengerInput[]; contactEmail?: string; contactPhone?: string; segments: SegmentsInput }) =>
    request<Pnr>('POST', '/api/pnrs', body),
  searchPnrs: (params: { status?: PnrStatus; lastName?: string; page?: number }) =>
    get<Page<PnrSummary>>(`/api/pnrs?${qs({ ...params, size: 20 })}`),
  pnr: (locator: string) => get<Pnr>(`/api/pnrs/${locator}`),
  ticket: (locator: string, formOfPayment: string, cardLast4?: string) =>
    request<Pnr>('POST', `/api/pnrs/${locator}/ticket`, { formOfPayment, cardLast4 }),
  cancel: (locator: string) => request<Pnr>('POST', `/api/pnrs/${locator}/cancel`),
  cancelSegment: (locator: string, type: SegmentType, id: number) =>
    request<Pnr>('DELETE', `/api/pnrs/${locator}/segments/${type}/${id}`),
  updateContact: (locator: string, contactEmail: string, contactPhone: string) =>
    request<Pnr>('PATCH', `/api/pnrs/${locator}/contact`, { contactEmail: contactEmail || null, contactPhone }),

  // queues
  queueCounts: () => get<Record<QueueName, number>>('/api/queues'),
  queue: (name: QueueName) => get<QueueItem[]>(`/api/queues/${name}`),
  placeOnQueue: (name: QueueName, recordLocator: string, remark: string) =>
    request<void>('POST', `/api/queues/${name}`, { recordLocator, remark }),
  workQueueItem: (id: number) => request<void>('DELETE', `/api/queues/items/${id}`),

  // admin
  agencies: () => get<Agency[]>('/api/admin/agencies'),
  createAgency: (pcc: string, name: string, country: string) =>
    request<Agency>('POST', '/api/admin/agencies', { pcc, name, country }),
  setAgencyActive: (id: number, active: boolean) => request<Agency>('PATCH', `/api/admin/agencies/${id}`, { active }),
  agents: (agencyId: number) => get<AgentAccount[]>(`/api/admin/agencies/${agencyId}/agents`),
  createAgent: (body: { username: string; password: string; fullName: string; role: Role; agencyId: number }) =>
    request<AgentAccount>('POST', '/api/admin/agents', body),
  setAgentActive: (id: number, active: boolean) => request<AgentAccount>('PATCH', `/api/admin/agents/${id}`, { active }),
}
