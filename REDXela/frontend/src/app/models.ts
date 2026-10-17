export interface Product {
  id: number;
  name: string;
  type: string;
}

export interface Client {
  id: number;
  name: string;
  email: string;
  level: number;
  orders: number;
  completed: number;
}

export interface OrderLine {
  productId: number;
  name: string;
  type: string;
  units: number;
}

export interface Order {
  id: number;
  clientId: number;
  clientName: string;
  clientEmail: string;
  level: number;
  units: number;
  lines: OrderLine[];
  state: string;
  stage: string;
  reason: string;
  progress: number;
  remainingMs: number;
  waitMs: number;
  effectiveLevel: number;
  agingPromotions: number;
  nextPromotionMs: number;
  slaRate: number;
  virtualWaitMs: number;
  slaLimitMs: number;
  slaStatus: string;
  heldResources: string[];
}

export interface ResourceSlot {
  slot: number;
  owner: number;
  state: string;
  ownerLabel: string;
}

export interface Resource {
  key: string;
  name: string;
  capacity: number;
  available: number;
  occupancyPercent: number;
  slots: ResourceSlot[];
}

export interface Receipt {
  id: number;
  productId: number;
  product: string;
  units: number;
  state: string;
  reason: string;
  lotId: number;
}

export interface WarehouseStock {
  productId: number;
  name: string;
  type: string;
  stock: number;
  reserved: number;
  available: number;
}

export interface WarehouseCellContent {
  allocationId: number;
  lotId: number;
  productId: number;
  units: number;
  reserved: number;
}

export interface WarehouseCell {
  id: number;
  label: string;
  capacity: number;
  used: number;
  contents: WarehouseCellContent[];
}

export interface WarehouseLotLocation {
  label: string;
  units: number;
  reserved: number;
}

export interface WarehouseLot {
  id: number;
  receiptId: number;
  productId: number;
  receivedUnits: number;
  locations: WarehouseLotLocation[];
}

export interface Warehouse {
  capacity: number;
  occupied: number;
  free: number;
  stocks: WarehouseStock[];
  cells: WarehouseCell[];
  lots: WarehouseLot[];
}

export interface MerchandiseType {
  name: string;
  needs: string[];
  description: string;
}

export interface ServiceMetric {
  level: number;
  limitMinutes: number;
  measured: number;
  completed: number;
  met: number;
  missed: number;
  overduePending: number;
  averageWaitMinutes: number;
  unmeasured: number;
}

export interface Conflict {
  state: string;
  winner: number;
  orders: Order[];
  events: string[];
}

export interface ChannelState {
  capacity: number;
  queued: number;
  durableBacklog: number;
}

export interface ComparisonResult {
  policy: string;
  completed: number;
  averageWaitMinutes: number;
  maxWaitMinutes: number;
  durationMinutes: number;
  missed: number;
  services: Array<Record<string, any>>;
  orders: Array<Record<string, any>>;
  occupancy: Array<Record<string, any>>;
}

export interface ComparisonData {
  seed?: number;
  count?: number;
  rate?: number;
  agingSeconds?: number;
  stockAssumption?: string;
  workload?: Array<Record<string, any>>;
  results?: ComparisonResult[];
}

export interface SimulationState {
  version: string;
  persistence: string;
  persistenceError: string;
  recovered: boolean;
  policy: string;
  automatic: boolean;
  agingSeconds: number;
  clockRate: number;
  comparison: ComparisonData;
  serviceMetrics: ServiceMetric[];
  total: number;
  completed: number;
  activeCount: number;
  waitingCount: number;
  averageWaitMs: number;
  averageProcessMs: number;
  resources: Resource[];
  conflict: Conflict;
  clients: Client[];
  products: Product[];
  types: MerchandiseType[];
  active: Order[];
  waiting: Order[];
  history: Order[];
  warehouse: Warehouse;
  receipts: Receipt[];
  channel: ChannelState;
}
