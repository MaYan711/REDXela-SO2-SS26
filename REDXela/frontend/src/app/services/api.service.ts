import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SimulationState } from '../models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  state(): Observable<SimulationState> {
    return this.http.get<SimulationState>(`${this.base}/state`);
  }

  post<T = any>(path: string, params: Record<string, string | number | boolean> = {}): Observable<T> {
    let query = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      query = query.set(key, String(value));
    }
    return this.http.post<T>(`${this.base}${path}`, null, { params: query });
  }
}
