import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription, firstValueFrom } from 'rxjs';
import {
  Client,
  ComparisonResult,
  Order,
  Product,
  Resource,
  ServiceMetric,
  SimulationState,
  WarehouseCell
} from './models';
import { ApiService } from './services/api.service';
import { RealtimeService } from './services/realtime.service';

interface DraftLine {
  productId: number;
  units: number;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly realtime = inject(RealtimeService);
  private readonly subscriptions = new Subscription();

  readonly services = ['Expres', 'Prioritario', 'Estandar', 'Programado', 'Economico'];
  readonly navigation = [
    ['dashboard', 'Panel general'],
    ['pedidos', 'Pedidos'],
    ['clientes', 'Clientes'],
    ['recursos', 'Recursos'],
    ['recepcion', 'Recepcion'],
    ['inventario', 'Inventario'],
    ['almacen', 'Almacen'],
    ['conflicto', 'Interbloqueo'],
    ['politicas', 'Politicas'],
    ['comparacion', 'Comparacion']
  ];

  state: SimulationState | null = null;
  connection = 'CONECTANDO';
  tab = 'dashboard';
  menuOpen = false;
  busy = false;
  error = '';
  notice = '';

  selectedClientId = 0;
  clientName = '';
  clientEmail = '';
  clientLevel = 3;
  lines: DraftLine[] = [{ productId: 1, units: 5 }];

  receiptProductId = 1;
  receiptUnits = 20;
  selectedCellId: number | null = null;

  clockRate = 60;
  agingSeconds = 30;
  comparisonSeed = 201930761;
  comparisonCount = 40;
  comparisonPolicy = 'PRIORIDAD';

  ngOnInit(): void {
    this.subscriptions.add(this.realtime.state$.subscribe(state => {
      this.state = state;
      if (state) {
        if (!this.selectedClientId && state.clients.length) this.selectedClientId = state.clients[0].id;
        if (!state.products.some(product => product.id === this.receiptProductId) && state.products.length) {
          this.receiptProductId = state.products[0].id;
        }
        this.clockRate = state.clockRate;
        this.agingSeconds = state.agingSeconds;
        if (state.comparison?.results?.length && !state.comparison.results.some(result => result.policy === this.comparisonPolicy)) {
          this.comparisonPolicy = state.comparison.results[0].policy;
        }
      }
    }));
    this.subscriptions.add(this.realtime.status$.subscribe(status => this.connection = status));
    this.realtime.connect();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.realtime.disconnect();
  }

  get blocked(): boolean {
    return this.busy || !this.state || !!this.state.persistenceError;
  }

  get tabTitle(): string {
    return this.navigation.find(item => item[0] === this.tab)?.[1] ?? 'REDXela';
  }

  get selectedClient(): Client | undefined {
    return this.state?.clients.find(client => client.id === Number(this.selectedClientId));
  }

  get selectedCell(): WarehouseCell | undefined {
    return this.state?.warehouse.cells.find(cell => cell.id === this.selectedCellId);
  }

  get comparisonResults(): ComparisonResult[] {
    return this.state?.comparison?.results ?? [];
  }

  get comparisonResult(): ComparisonResult | undefined {
    return this.comparisonResults.find(result => result.policy === this.comparisonPolicy);
  }

  get overallOccupancy(): number {
    const resources = this.state?.resources ?? [];
    if (!resources.length) return 0;
    return resources.reduce((total, resource) => total + resource.occupancyPercent, 0) / resources.length;
  }

  get warehousePercent(): number {
    const warehouse = this.state?.warehouse;
    return warehouse?.capacity ? warehouse.occupied * 100 / warehouse.capacity : 0;
  }

  get recentActivity(): Order[] {
    return [...(this.state?.active ?? []), ...(this.state?.waiting ?? []), ...(this.state?.history ?? [])]
      .sort((a, b) => b.id - a.id)
      .slice(0, 8);
  }

  setTab(tab: string): void {
    this.tab = tab;
    this.menuOpen = false;
    this.error = '';
    this.notice = '';
  }

  serviceName(level: number): string {
    return this.services[level - 1] ?? `Nivel ${level}`;
  }

  readable(value: string | null | undefined): string {
    return (value ?? '').replaceAll('_', ' ');
  }

  seconds(ms: number | null | undefined): string {
    return `${((ms ?? 0) / 1000).toFixed(1)} s`;
  }

  minutes(ms: number | null | undefined): string {
    return `${((ms ?? 0) / 60000).toFixed(2)} min`;
  }

  percent(value: number | null | undefined): string {
    return `${Math.max(0, Math.min(100, value ?? 0)).toFixed(1)}%`;
  }

  resourceUsage(resource: Resource): string {
    return `${resource.capacity - resource.available} / ${resource.capacity}`;
  }

  productName(id: number): string {
    return this.state?.products.find(product => product.id === id)?.name ?? `Producto #${id}`;
  }

  productType(id: number): string {
    return this.state?.products.find(product => product.id === id)?.type ?? '';
  }

  addLine(): void {
    if (this.lines.length >= 10) return;
    this.lines = [...this.lines, { productId: this.state?.products[0]?.id ?? 1, units: 1 }];
  }

  removeLine(index: number): void {
    if (this.lines.length <= 1) return;
    this.lines = this.lines.filter((_, current) => current !== index);
  }

  async registerClient(): Promise<void> {
    const result = await this.execute<any>('/clients', {
      name: this.clientName.trim(),
      email: this.clientEmail.trim(),
      level: this.clientLevel
    }, 'Cliente registrado correctamente');
    if (result?.id) this.selectedClientId = result.id;
    if (result) {
      this.clientName = '';
      this.clientEmail = '';
    }
  }

  async changeClientService(client: Client, level: number): Promise<void> {
    await this.execute('/clients/service', { clientId: client.id, level }, 'Servicio actualizado para pedidos nuevos');
  }

  async createOrder(): Promise<void> {
    if (!this.selectedClientId) {
      this.error = 'Registra y selecciona un cliente antes de crear el pedido';
      return;
    }
    const items = this.lines.map(line => `${line.productId}:${line.units}`).join(',');
    const result = await this.execute('/orders', { clientId: this.selectedClientId, items }, 'Pedido registrado correctamente');
    if (result) this.lines = [{ productId: this.state?.products[0]?.id ?? 1, units: 5 }];
  }

  async receiveStock(): Promise<void> {
    await this.execute('/receipts', { productId: this.receiptProductId, units: this.receiptUnits }, 'Recepcion registrada');
  }

  async toggleGenerator(): Promise<void> {
    await this.execute('/generator', { enabled: !this.state?.automatic }, this.state?.automatic ? 'Generador detenido' : 'Generador iniciado');
  }

  async changePolicy(policy: string): Promise<void> {
    await this.execute('/policy', { value: policy }, `Politica aplicada: ${policy}`);
  }

  async applyAging(): Promise<void> {
    await this.execute('/aging', { seconds: this.agingSeconds }, 'Intervalo de envejecimiento actualizado');
  }

  async applyClock(): Promise<void> {
    await this.execute('/clock', { rate: this.clockRate }, 'Escala de reloj actualizada');
  }

  async startConflict(): Promise<void> {
    if (!this.selectedClientId) {
      this.error = 'Selecciona un cliente para crear el escenario de interbloqueo';
      return;
    }
    await this.execute('/conflict/start', {
      clientId: this.selectedClientId,
      productId: this.receiptProductId,
      units: this.receiptUnits
    }, 'Escenario de interbloqueo iniciado');
  }

  async resolveConflict(orderId: number): Promise<void> {
    await this.execute('/conflict/resolve', { winner: orderId }, `Pedido #${orderId} seleccionado para continuar`);
  }

  async runComparison(): Promise<void> {
    await this.execute('/comparison', {
      seed: this.comparisonSeed,
      count: this.comparisonCount
    }, 'Comparacion finalizada');
  }

  private async execute<T = any>(path: string, params: Record<string, string | number | boolean>, message: string): Promise<T | null> {
    this.busy = true;
    this.error = '';
    this.notice = '';
    try {
      const result = await firstValueFrom(this.api.post<T>(path, params));
      this.notice = message;
      this.realtime.refresh();
      return result;
    } catch (error: any) {
      this.error = error?.error?.error ?? error?.message ?? 'Operacion rechazada por el backend';
      return null;
    } finally {
      this.busy = false;
    }
  }

  trackById(_: number, item: { id: number }): number {
    return item.id;
  }

  trackByKey(_: number, item: { key: string }): string {
    return item.key;
  }

  trackByProduct(_: number, item: Product): number {
    return item.id;
  }

  trackByMetric(_: number, item: ServiceMetric): number {
    return item.level;
  }
}
