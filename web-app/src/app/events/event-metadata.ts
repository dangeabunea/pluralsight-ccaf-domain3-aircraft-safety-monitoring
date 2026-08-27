import { Component, Input } from '@angular/core';
import type { InfringementDetail } from '../models/event-api.models';

@Component({
  selector: 'app-event-metadata',
  templateUrl: './event-metadata.html'
})
export class EventMetadataComponent {
  @Input({ required: true }) detail!: InfringementDetail;

  formatDateTime(iso: string): string {
    const d = new Date(iso);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  formatType(type: string): string {
    switch (type) {
      case 'SMI': return 'Separation Infringement';
      case 'LVB': return 'Level Bust';
      default: return type || 'Unknown';
    }
  }
}
