import { Component, EventEmitter, Input, Output } from '@angular/core';
import type { InfringementListItem } from '../models/event-api.models';

type Tab = 'pending' | 'escalated';

@Component({
  selector: 'app-events-table',
  templateUrl: './events-table.html'
})
export class EventsTableComponent {
  @Input({ required: true }) events!: InfringementListItem[];
  @Input({ required: true }) tab!: Tab;
  @Input({ required: true }) loading!: boolean;
  @Input({ required: true }) page!: number;
  @Input({ required: true }) totalPages!: number;
  @Input({ required: true }) totalElements!: number;
  @Input() pendingCount: number | null = null;
  @Input() escalatedCount: number | null = null;

  @Output() readonly tabChange = new EventEmitter<Tab>();
  @Output() readonly view = new EventEmitter<string>();
  @Output() readonly pageChange = new EventEmitter<number>();

  readonly pageSize = 20;

  get lastPage(): number { return Math.max(0, this.totalPages - 1); }
  get startRow(): number { return this.page * this.pageSize + 1; }
  get endRow(): number { return Math.min((this.page + 1) * this.pageSize, this.totalElements); }
  get sortLabel(): string { return this.tab === 'pending' ? 'Start' : 'Escalated'; }

  typeBadgeStyle(type: string): Record<string, string> {
    if (type === 'SMI') return { background: '#fce7f3', color: '#9d174d' };
    if (type === 'LVB') return { background: '#faf5ff', color: '#7e22ce' };
    return { background: '#f1f5f9', color: '#475569' };
  }

  // Formats an ISO timestamp as YYYY-MM-DD HH:MM to match the design mockup.
  formatDateTime(iso: string): string {
    const d = new Date(iso);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  // Duration is computed client-side per SAFM-68 spec.
  computeDuration(startedAt: string, endedAt: string): string {
    const diffMs = new Date(endedAt).getTime() - new Date(startedAt).getTime();
    const minutes = Math.floor(diffMs / 60_000);
    if (minutes < 1) return '< 1 min';
    if (minutes < 60) return `${minutes} min`;
    const hours = Math.floor(minutes / 60);
    return `${hours}h ${minutes % 60}m`;
  }
}
