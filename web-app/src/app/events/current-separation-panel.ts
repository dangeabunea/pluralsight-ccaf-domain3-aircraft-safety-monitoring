import { Component, computed, input } from '@angular/core';
import type { AircraftPosition } from '../models/event-api.models';

const METRES_PER_NAUTICAL_MILE = 1852;

@Component({
  selector: 'app-current-separation-panel',
  templateUrl: './current-separation-panel.html'
})
export class CurrentSeparationPanelComponent {
  readonly trajectory1 = input.required<AircraftPosition[]>();
  readonly trajectory2 = input.required<AircraftPosition[]>();
  readonly currentIndex = input.required<number>();

  readonly currentPosition1 = computed(() => {
    const t = this.trajectory1();
    const i = this.currentIndex();
    return i >= 0 && i < t.length ? t[i] : null;
  });

  readonly currentPosition2 = computed(() => {
    const t = this.trajectory2();
    const i = this.currentIndex();
    return i >= 0 && i < t.length ? t[i] : null;
  });

  readonly horizontalSeparationNm = computed(() => {
    const p1 = this.currentPosition1();
    const p2 = this.currentPosition2();
    if (!p1 || !p2) return null;
    const dx = p1.x - p2.x;
    const dy = p1.y - p2.y;
    return Math.sqrt(dx * dx + dy * dy) / METRES_PER_NAUTICAL_MILE;
  });

  readonly verticalSeparationFt = computed(() => {
    const p1 = this.currentPosition1();
    const p2 = this.currentPosition2();
    if (!p1 || !p2) return null;
    return Math.round(Math.abs(p1.altFeet - p2.altFeet));
  });

  // Composite ICAO infringement condition: H < 5 NM AND V < 1000 ft (below FL290, per ICAO RVSM)
  readonly isAmber = computed(() => {
    const h = this.horizontalSeparationNm();
    const v = this.verticalSeparationFt();
    if (h === null || v === null) return false;
    return h < 5 && v < 1000;
  });

  readonly currentTime = computed(() => {
    const p = this.currentPosition1();
    return p ? p.timestampUTC.slice(11, 19) : '--:--:--';
  });

  formatHSep(): string {
    const h = this.horizontalSeparationNm();
    return h !== null ? h.toFixed(2) : '--';
  }

  formatVSep(): string {
    const v = this.verticalSeparationFt();
    return v !== null ? String(v) : '--';
  }
}
