import { Injectable, NgZone, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { SimulationState } from '../models';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly api = inject(ApiService);
  private readonly zone = inject(NgZone);
  private source?: EventSource;
  private readonly stateSubject = new BehaviorSubject<SimulationState | null>(null);
  private readonly statusSubject = new BehaviorSubject<'CONECTANDO' | 'EN_LINEA' | 'RECONECTANDO' | 'SIN_CONEXION'>('CONECTANDO');

  readonly state$ = this.stateSubject.asObservable();
  readonly status$ = this.statusSubject.asObservable();

  connect(): void {
    if (this.source) return;
    this.api.state().subscribe({
      next: state => this.stateSubject.next(state),
      error: () => this.statusSubject.next('RECONECTANDO')
    });
    this.source = new EventSource('/api/events');
    this.source.onopen = () => this.zone.run(() => this.statusSubject.next('EN_LINEA'));
    this.source.onmessage = event => {
      this.zone.run(() => {
        try {
          this.stateSubject.next(JSON.parse(event.data) as SimulationState);
          this.statusSubject.next('EN_LINEA');
        } catch {
          this.statusSubject.next('RECONECTANDO');
        }
      });
    };
    this.source.onerror = () => this.zone.run(() => this.statusSubject.next('RECONECTANDO'));
  }

  refresh(): void {
    this.api.state().subscribe({
      next: state => this.stateSubject.next(state),
      error: () => this.statusSubject.next('SIN_CONEXION')
    });
  }

  disconnect(): void {
    this.source?.close();
    this.source = undefined;
    this.statusSubject.next('SIN_CONEXION');
  }
}
