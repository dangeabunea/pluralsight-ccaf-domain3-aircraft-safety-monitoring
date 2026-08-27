import { Component, computed, input } from '@angular/core';
import type { AircraftPosition } from '../models/event-api.models';

@Component({
  selector: 'app-aircraft-info-box',
  templateUrl: './aircraft-info-box.html'
})
export class AircraftInfoBoxComponent {
  readonly trajectory = input.required<readonly AircraftPosition[]>();
  readonly currentIndex = input.required<number>();
  readonly colour = input.required<string>();
  readonly callsign = input.required<string>();

  readonly currentPos = computed(() => {
    const t = this.trajectory();
    const i = this.currentIndex();
    return i >= 0 && i < t.length ? t[i] : null;
  });

  readonly prevPos = computed(() => {
    const t = this.trajectory();
    const i = this.currentIndex();
    return i > 0 && i < t.length ? t[i - 1] : null;
  });

  readonly flightLevel = computed((): string => {
    const p = this.currentPos();
    return p ? `FL${Math.round(p.altFeet / 100)}` : '--';
  });

  readonly altitudeTrend = computed((): string => {
    const cur = this.currentPos();
    const prev = this.prevPos();
    if (!cur || !prev) return '—';
    if (cur.altFeet > prev.altFeet) return '↑';
    if (cur.altFeet < prev.altFeet) return '↓';
    return '—';
  });

  readonly groundTrack = computed((): string => {
    const cur = this.currentPos();
    const prev = this.prevPos();
    if (!cur || !prev) return '--';
    const deg = Math.atan2(cur.y - prev.y, cur.x - prev.x) * (180 / Math.PI);
    return `${Math.round(((deg % 360) + 360) % 360)}°`;
  });

  readonly speedKt = computed((): string => {
    const p = this.currentPos();
    if (!p || p.speedKn === null) return '--';
    return `${p.speedKn} kt`;
  });
}
