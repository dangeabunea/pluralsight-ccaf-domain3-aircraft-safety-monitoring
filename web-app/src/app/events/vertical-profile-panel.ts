import {
  Component, HostListener, ChangeDetectionStrategy, ElementRef,
  input, output, signal, computed, inject
} from '@angular/core';
import type { AircraftPosition } from '../models/event-api.models';

const AC1 = '#f5c842';
const AC2 = '#ff5599';
const PAD_L = 46;
const PAD_R = 14;
const PAD_T = 14;
const PAD_B = 13;
const CHART_W = 360;
const CHART_H = 185;
const SVG_W = PAD_L + CHART_W + PAD_R;   // 420
const SVG_H = PAD_T + CHART_H + PAD_B;   // 212
const STATUS_H = 32;
const HEADER_H = 30;
const PANEL_H = HEADER_H + SVG_H + STATUS_H;

let savedX = 16;
let savedY = 16;

interface GridLine { alt: number; label: string; rvsm: boolean }
interface SegmentedPaths { before: string; infringement: string; after: string }
interface Derived {
  yMin: number; yMax: number;
  gridLines: Array<GridLine & { y: number }>;
  seg1: SegmentedPaths; seg2: SegmentedPaths;
  showDot1: boolean; dot1x: number; dot1y: number;
  showDot2: boolean; dot2x: number; dot2y: number;
  sepLabel: string | null;
}

function xAt(i: number, n: number): number {
  return n <= 1 ? PAD_L : PAD_L + (i / (n - 1)) * CHART_W;
}

function niceGridLines(yMin: number, yMax: number): GridLine[] {
  const range = yMax - yMin;
  const rawStep = range / 5;
  const niceSteps = [500, 1000, 2000, 5000];
  const step = niceSteps.find(s => rawStep <= s) ?? 5000;
  const first = Math.ceil(yMin / step) * step;
  const lines: GridLine[] = [];
  for (let alt = first; alt <= yMax; alt += step) {
    lines.push({ alt, label: `FL${String(Math.round(alt / 100)).padStart(3, '0')}`, rvsm: false });
  }
  if (29000 >= yMin && 29000 <= yMax) {
    const existing = lines.find(l => l.alt === 29000);
    if (existing) { existing.rvsm = true; }
    else { lines.push({ alt: 29000, label: 'FL290', rvsm: true }); lines.sort((a, b) => a.alt - b.alt); }
  }
  return lines;
}

export { niceGridLines };

function segmentedPaths(
  traj: readonly AircraftPosition[],
  eventStartCycle: number,
  eventEndCycle: number,
  yAt: (alt: number) => number
): SegmentedPaths {
  const n = traj.length;
  const build = (pts: Array<{ i: number; p: AircraftPosition }>) => {
    if (pts.length < 2) return '';
    return pts.map(({ i, p }, idx) =>
      `${idx === 0 ? 'M' : 'L'}${xAt(i, n).toFixed(1)},${yAt(p.altFeet).toFixed(1)}`
    ).join('');
  };
  const indexed = traj.map((p, i) => ({ i, p }));
  const beforePts = indexed.filter(({ i }) => i <= eventStartCycle);
  const infrPts   = indexed.filter(({ i }) => i >= eventStartCycle && i <= eventEndCycle);
  const afterPts  = indexed.filter(({ i }) => i >= eventEndCycle);
  return { before: build(beforePts), infringement: build(infrPts), after: build(afterPts) };
}

@Component({
  selector: 'app-vertical-profile-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { style: 'position:absolute;inset:0;pointer-events:none;z-index:20;' },
  styles: [`
    .vp-panel {
      position: absolute; pointer-events: all;
      border: 1px solid rgba(0,200,220,0.22); border-radius: 6px; overflow: hidden;
      box-shadow: 0 12px 40px rgba(0,0,0,0.78), 0 0 0 1px rgba(0,200,220,0.07);
      background: rgba(5,12,32,0.94); backdrop-filter: blur(12px);
    }
    .vp-header {
      display: flex; align-items: center; justify-content: space-between;
      padding: 0 12px; height: 30px;
      background: rgba(0,180,220,0.07); border-bottom: 1px solid rgba(0,200,220,0.15);
      cursor: move; user-select: none;
    }
    .vp-title { font-family: 'Courier New', monospace; font-size: 10px; font-weight: 700; letter-spacing: 0.12em; color: rgba(0,220,255,0.8); }
    .vp-hint  { font-family: 'Courier New', monospace; font-size: 8.5px; color: rgba(255,255,255,0.18); }
    .vp-close { background: none; border: none; cursor: pointer; padding: 0 2px; font-size: 18px; line-height: 1; color: rgba(255,255,255,0.32); transition: color 0.15s; }
    .vp-close:hover { color: rgba(255,255,255,0.8); }
    .vp-status { display: flex; align-items: center; gap: 12px; padding: 0 12px; height: 32px; background: rgba(0,10,28,0.7); border-top: 1px solid rgba(0,200,220,0.12); }
  `],
  template: `
    <div class="vp-panel" [style.left.px]="panelX()" [style.top.px]="panelY()" [style.width.px]="SVG_W">

      <div class="vp-header" (pointerdown)="startDrag($event)">
        <span class="vp-title">▲ VERTICAL PROFILE</span>
        <div style="display:flex;align-items:center;gap:10px;">
          <span class="vp-hint">DRAG TO REPOSITION</span>
          <button class="vp-close" (click)="closed.emit()">×</button>
        </div>
      </div>

      <svg [attr.width]="SVG_W" [attr.height]="SVG_H" style="display:block;overflow:visible">

        <rect [attr.x]="PAD_L" [attr.y]="PAD_T" [attr.width]="CHART_W" [attr.height]="CHART_H" fill="rgba(0,10,28,0.5)"/>

        @for (g of d().gridLines; track g.alt) {
          <line [attr.x1]="PAD_L" [attr.x2]="PAD_L + CHART_W" [attr.y1]="g.y" [attr.y2]="g.y"
            [attr.stroke]="g.rvsm ? 'rgba(255,180,40,0.55)' : 'rgba(0,200,220,0.15)'"
            [attr.stroke-dasharray]="g.rvsm ? '5,4' : ''" stroke-width="1"/>
          <text [attr.x]="PAD_L - 4" [attr.y]="g.y + 3.5" text-anchor="end"
            fill="rgba(0,200,220,0.45)" style="font-size:8px;font-family:'Courier New',monospace;">{{ g.label }}</text>
          @if (g.rvsm) {
            <text [attr.x]="PAD_L + CHART_W + 3" [attr.y]="g.y + 3.5"
              fill="rgba(255,180,40,0.5)" style="font-size:7.5px;font-family:'Courier New',monospace;">RVSM</text>
          }
        }

        @if (d().seg1.before) {
          <path [attr.d]="d().seg1.before" fill="none" [attr.stroke]="AC1"
            stroke-width="1.5" stroke-opacity="0.35" stroke-dasharray="5,4" stroke-linejoin="round"/>
        }
        @if (d().seg1.infringement) {
          <path [attr.d]="d().seg1.infringement" fill="none" [attr.stroke]="AC1"
            stroke-width="1.5" stroke-opacity="0.9" stroke-linejoin="round"/>
        }
        @if (d().seg1.after) {
          <path [attr.d]="d().seg1.after" fill="none" [attr.stroke]="AC1"
            stroke-width="1.5" stroke-opacity="0.35" stroke-dasharray="5,4" stroke-linejoin="round"/>
        }

        @if (d().seg2.before) {
          <path [attr.d]="d().seg2.before" fill="none" [attr.stroke]="AC2"
            stroke-width="1.5" stroke-opacity="0.35" stroke-dasharray="5,4" stroke-linejoin="round"/>
        }
        @if (d().seg2.infringement) {
          <path [attr.d]="d().seg2.infringement" fill="none" [attr.stroke]="AC2"
            stroke-width="1.5" stroke-opacity="0.9" stroke-linejoin="round"/>
        }
        @if (d().seg2.after) {
          <path [attr.d]="d().seg2.after" fill="none" [attr.stroke]="AC2"
            stroke-width="1.5" stroke-opacity="0.35" stroke-dasharray="5,4" stroke-linejoin="round"/>
        }

        @if (d().showDot1) {
          <circle [attr.cx]="d().dot1x" [attr.cy]="d().dot1y" r="4.5" [attr.fill]="AC1"
            stroke="rgba(0,0,0,0.55)" stroke-width="1.5"/>
        }
        @if (d().showDot2) {
          <circle [attr.cx]="d().dot2x" [attr.cy]="d().dot2y" r="4.5" [attr.fill]="AC2"
            stroke="rgba(0,0,0,0.55)" stroke-width="1.5"/>
        }

        <text [attr.x]="PAD_L + CHART_W / 2" [attr.y]="SVG_H - 2"
          text-anchor="middle" fill="rgba(255,255,255,0.18)"
          style="font-size:8.5px;font-family:'Courier New',monospace;">RADAR CYCLES →</text>

      </svg>

      <div class="vp-status">
        <span style="display:flex;align-items:center;gap:5px;color:#f5c842;font-family:'Courier New',monospace;font-size:10px;font-weight:700;">
          <svg width="8" height="8" style="flex-shrink:0;"><circle cx="4" cy="4" r="4" fill="#f5c842"/></svg>
          {{ callsign1() }} FL{{ fl1() }}{{ trend1() ? ' ' + trend1() : '' }}
        </span>
        <span style="display:flex;align-items:center;gap:5px;color:#ff5599;font-family:'Courier New',monospace;font-size:10px;font-weight:700;">
          <svg width="8" height="8" style="flex-shrink:0;"><circle cx="4" cy="4" r="4" fill="#ff5599"/></svg>
          {{ callsign2() }} FL{{ fl2() }}{{ trend2() ? ' ' + trend2() : '' }}
        </span>
        @if (d().sepLabel) {
          <span style="margin-left:auto;font-family:'Courier New',monospace;font-size:10px;color:rgba(255,255,255,0.55);">
            V-SEP {{ d().sepLabel }}
          </span>
        }
      </div>

    </div>
  `
})
export class VerticalProfilePanelComponent {
  private readonly elementRef = inject(ElementRef);

  readonly trajectory1 = input.required<readonly AircraftPosition[]>();
  readonly trajectory2 = input.required<readonly AircraftPosition[]>();
  readonly currentIndex = input.required<number>();
  readonly callsign1 = input.required<string>();
  readonly callsign2 = input.required<string>();
  readonly eventStartCycle = input.required<number>();
  readonly eventEndCycle = input.required<number>();
  readonly closed = output<void>();

  readonly SVG_W = SVG_W;
  readonly SVG_H = SVG_H;
  readonly PAD_L = PAD_L;
  readonly PAD_T = PAD_T;
  readonly CHART_W = CHART_W;
  readonly CHART_H = CHART_H;
  readonly AC1 = AC1;
  readonly AC2 = AC2;

  readonly panelX = signal(savedX);
  readonly panelY = signal(savedY);

  private dragging = false;
  private lastX = 0;
  private lastY = 0;

  readonly d = computed<Derived>(() => {
    const t1 = this.trajectory1();
    const t2 = this.trajectory2();
    const idx = this.currentIndex();
    const startCycle = this.eventStartCycle();
    const endCycle = this.eventEndCycle();

    const allAlts = [...t1, ...t2].map(p => p.altFeet);
    const rawMin = allAlts.length > 0 ? Math.min(...allAlts) : 0;
    const rawMax = allAlts.length > 0 ? Math.max(...allAlts) : 10000;
    const pad = Math.max((rawMax - rawMin) * 0.12, 1000);
    const yMin = Math.max(0, rawMin - pad);
    const yMax = rawMax + pad;

    const yAt = (alt: number) => PAD_T + CHART_H * (1 - (alt - yMin) / (yMax - yMin));

    const rawGrid = niceGridLines(yMin, yMax);
    const gridLines = rawGrid.map(g => ({ ...g, y: yAt(g.alt) }));

    const seg1 = segmentedPaths(t1, startCycle, endCycle, yAt);
    const seg2 = segmentedPaths(t2, startCycle, endCycle, yAt);

    const n1 = t1.length;
    const n2 = t2.length;
    const showDot1 = n1 > 0 && idx < n1;
    const dot1x = showDot1 ? xAt(idx, n1) : 0;
    const dot1y = showDot1 ? yAt(t1[idx].altFeet) : 0;
    const showDot2 = n2 > 0 && idx < n2;
    const dot2x = showDot2 ? xAt(idx, n2) : 0;
    const dot2y = showDot2 ? yAt(t2[idx].altFeet) : 0;

    const sepFt = showDot1 && showDot2 ? Math.abs(t1[idx].altFeet - t2[idx].altFeet) : null;

    return {
      yMin, yMax, gridLines, seg1, seg2,
      showDot1, dot1x, dot1y,
      showDot2, dot2x, dot2y,
      sepLabel: sepFt !== null ? `${Math.round(sepFt)} ft` : null,
    };
  });

  readonly fl1 = computed(() => {
    const t = this.trajectory1(); const i = this.currentIndex();
    return t.length > 0 ? Math.round(t[Math.min(i, t.length - 1)].altFeet / 100) : 0;
  });

  readonly fl2 = computed(() => {
    const t = this.trajectory2(); const i = this.currentIndex();
    return t.length > 0 ? Math.round(t[Math.min(i, t.length - 1)].altFeet / 100) : 0;
  });

  readonly trend1 = computed(() => {
    const t = this.trajectory1(); const i = this.currentIndex();
    if (i < 1 || t.length < 2) return '';
    const delta = t[Math.min(i, t.length - 1)].altFeet - t[Math.min(i - 1, t.length - 1)].altFeet;
    return delta > 50 ? '▲' : delta < -50 ? '▼' : '';
  });

  readonly trend2 = computed(() => {
    const t = this.trajectory2(); const i = this.currentIndex();
    if (i < 1 || t.length < 2) return '';
    const delta = t[Math.min(i, t.length - 1)].altFeet - t[Math.min(i - 1, t.length - 1)].altFeet;
    return delta > 50 ? '▲' : delta < -50 ? '▼' : '';
  });

  startDrag(evt: PointerEvent): void {
    this.dragging = true;
    this.lastX = evt.clientX;
    this.lastY = evt.clientY;
    (evt.currentTarget as Element).setPointerCapture(evt.pointerId);
    evt.preventDefault();
  }

  @HostListener('document:pointermove', ['$event'])
  onMove(evt: PointerEvent): void {
    if (!this.dragging) return;
    const dx = evt.clientX - this.lastX;
    const dy = evt.clientY - this.lastY;
    this.lastX = evt.clientX;
    this.lastY = evt.clientY;
    const host = (this.elementRef.nativeElement as HTMLElement).parentElement;
    const maxX = host ? host.clientWidth - SVG_W : 9999;
    const maxY = host ? host.clientHeight - PANEL_H : 9999;
    this.panelX.update(x => Math.max(0, Math.min(x + dx, maxX)));
    this.panelY.update(y => Math.max(0, Math.min(y + dy, maxY)));
    savedX = this.panelX();
    savedY = this.panelY();
  }

  @HostListener('document:pointerup')
  onUp(): void { this.dragging = false; }
}
