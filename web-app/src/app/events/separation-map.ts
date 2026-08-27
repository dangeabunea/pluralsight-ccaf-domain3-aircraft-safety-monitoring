import {
  AfterViewInit, Component, ElementRef, NgZone, OnChanges,
  OnDestroy, SimpleChanges, ViewChild, inject, input
} from '@angular/core';
import Map from 'ol/Map';
import View from 'ol/View';
import TileLayer from 'ol/layer/Tile';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import XYZ from 'ol/source/XYZ';
import Feature from 'ol/Feature';
import { LineString, Point } from 'ol/geom';
import { Style, Fill, Stroke, RegularShape, Icon, Text } from 'ol/style';
import { fromLonLat } from 'ol/proj';
import { Zoom } from 'ol/control';
import { DragPan } from 'ol/interaction';
import type RenderEvent from 'ol/render/Event';
import type { AircraftPosition } from '../models/event-api.models';

const NM_TO_M = 1852;
const AC1_COLOR = '#f5c842';
const AC2_COLOR = '#ff5599';
const LABEL_OFFSET_X_M = 3 * NM_TO_M;
const LABEL_OFFSET_Y_M = 2 * NM_TO_M;
const DRAG_HIGHLIGHT_COLOR = '#90ee90';

/**
 * Converts a position to projected map coordinates (EPSG:3857).
 * Uses lat/lon when available, falls back to NM-based cartesian.
 */
export function toMapCoord(pos: AircraftPosition): [number, number] {
  if (pos.lat !== null && pos.lon !== null) {
    return fromLonLat([pos.lon, pos.lat]) as [number, number];
  }
  return [pos.x * NM_TO_M, pos.y * NM_TO_M];
}

/** Scales NM-based cartesian coordinates to metres. Kept for unit tests that use NM-only positions. */
export function toOlCoord(x: number, y: number): [number, number] {
  return [x * NM_TO_M, y * NM_TO_M];
}

export function groundTrackRotation(cur: AircraftPosition, prev: AircraftPosition): number {
  const dx = cur.x - prev.x;
  const dy = cur.y - prev.y;
  return Math.PI / 2 - Math.atan2(dy, dx);
}

export function buildExtent(t1: readonly AircraftPosition[], t2: readonly AircraftPosition[]): [number, number, number, number] {
  const coords = [...t1, ...t2].map(p => toMapCoord(p));
  const xs = coords.map(c => c[0]);
  const ys = coords.map(c => c[1]);
  return [Math.min(...xs), Math.min(...ys), Math.max(...xs), Math.max(...ys)];
}

export function findCycleIndex(traj: readonly AircraftPosition[], cycle: number): number {
  return traj.findIndex(p => p.radarCycle === cycle);
}

export function buildLabelText(callsign: string, fl: number, trend: string, speedKn: number | null): string {
  const flLine = trend ? `FL${fl} ${trend}` : `FL${fl}`;
  const speedLine = speedKn !== null ? `\n${speedKn}kt` : '';
  return `${callsign}\n${flLine}${speedLine}`;
}

function aircraftSvg(color: string): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32">`
    + `<path d="M16 1L17.8 11L28 15.5L17.8 17L19 23L24 28L16 25.5L8 28L13 23L14.2 17L4 15.5L14.2 11Z"`
    + ` fill="${color}" stroke="rgba(0,0,0,0.4)" stroke-width="0.8"/>`
    + `</svg>`;
  return 'data:image/svg+xml,' + encodeURIComponent(svg);
}

@Component({
  selector: 'app-separation-map',
  templateUrl: './separation-map.html',
  styles: [`
    :host { display: block; height: 100%; }
    .map-container { cursor: grab; }
    .map-container:active { cursor: grabbing; }
  `]
})
export class SeparationMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('mapContainer') private readonly mapContainer!: ElementRef<HTMLDivElement>;

  readonly trajectory1 = input.required<readonly AircraftPosition[]>();
  readonly trajectory2 = input.required<readonly AircraftPosition[]>();
  readonly currentIndex = input.required<number>();
  readonly callsign1 = input.required<string>();
  readonly callsign2 = input.required<string>();
  readonly eventStartCycle = input.required<number>();
  readonly eventEndCycle = input.required<number>();

  private readonly zone = inject(NgZone);

  private map: Map | null = null;
  private trajectorySource!: VectorSource;
  private trailSource!: VectorSource;
  private markerSource!: VectorSource;
  // Dedicated source for labels/leaders — survives updateMarkers clears
  private labelSource!: VectorSource;

  // Persistent label and leader features — geometry updated in place
  private leaderFeature1!: Feature;
  private labelFeature1!: Feature;
  private leaderFeature2!: Feature;
  private labelFeature2!: Feature;

  // Per-aircraft label offsets (metres) relative to aircraft position
  private labelOffset1: [number, number] = [LABEL_OFFSET_X_M, LABEL_OFFSET_Y_M];
  private labelOffset2: [number, number] = [LABEL_OFFSET_X_M, LABEL_OFFSET_Y_M];

  // Drag state
  private dragAircraft = 0; // 0=none, 1=ac1, 2=ac2
  private dragStartCoord: [number, number] | null = null;
  private dragStartOffset: [number, number] | null = null;

  ngAfterViewInit(): void {
    this.zone.runOutsideAngular(() => {
      this.trajectorySource = new VectorSource();
      this.trailSource = new VectorSource();
      this.markerSource = new VectorSource();
      this.labelSource = new VectorSource();

      this.initLabelFeatures();

      const tileLayer = new TileLayer({
        source: new XYZ({
          url: 'https://{a-d}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}@2x.png',
          attributions: '&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a> &copy; <a href="https://carto.com/">CARTO</a>'
        })
      });

      // Paint a dark navy overlay directly on the tile canvas after each render.
      // CSS filters via ::ng-deep don't reliably reach OL's dynamically created canvas.
      tileLayer.on('postrender', (evt: RenderEvent) => {
        const ctx = evt.context as CanvasRenderingContext2D | null;
        if (!ctx) return;
        ctx.save();
        ctx.fillStyle = 'rgba(12, 28, 64, 0.72)';
        ctx.fillRect(0, 0, ctx.canvas.width, ctx.canvas.height);
        ctx.restore();
      });

      const trajectoryLayer = new VectorLayer({ source: this.trajectorySource });
      const trailLayer = new VectorLayer({ source: this.trailSource });
      const markerLayer = new VectorLayer({ source: this.markerSource });
      const labelLayer = new VectorLayer({ source: this.labelSource });

      this.map = new Map({
        target: this.mapContainer.nativeElement,
        layers: [tileLayer, trajectoryLayer, trailLayer, markerLayer, labelLayer],
        controls: [new Zoom()],
        view: new View({ projection: 'EPSG:3857', center: [0, 0], zoom: 2 })
      });

      this.setupDragHandlers();
      this.drawTrajectories();
      this.updateMarkers(this.currentIndex());
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['trajectory1'] || changes['trajectory2']) && this.map) {
      this.zone.runOutsideAngular(() => {
        this.labelOffset1 = [LABEL_OFFSET_X_M, LABEL_OFFSET_Y_M];
        this.labelOffset2 = [LABEL_OFFSET_X_M, LABEL_OFFSET_Y_M];
      });
    }
    if (changes['currentIndex'] && this.map) {
      this.zone.runOutsideAngular(() => {
        this.updateMarkers(changes['currentIndex'].currentValue as number);
      });
    }
  }

  ngOnDestroy(): void {
    this.zone.runOutsideAngular(() => {
      this.map?.dispose();
      this.map = null;
    });
  }

  private initLabelFeatures(): void {
    this.leaderFeature1 = new Feature(new LineString([[0, 0], [0, 0]]));
    this.leaderFeature1.setStyle(new Style({ stroke: new Stroke({ color: AC1_COLOR, width: 1 }) }));

    this.labelFeature1 = new Feature(new Point([0, 0]));
    this.labelFeature1.set('aircraft', 1);

    this.leaderFeature2 = new Feature(new LineString([[0, 0], [0, 0]]));
    this.leaderFeature2.setStyle(new Style({ stroke: new Stroke({ color: AC2_COLOR, width: 1 }) }));

    this.labelFeature2 = new Feature(new Point([0, 0]));
    this.labelFeature2.set('aircraft', 2);

    this.labelSource.addFeatures([this.leaderFeature1, this.labelFeature1, this.leaderFeature2, this.labelFeature2]);
  }

  private setupDragHandlers(): void {
    const viewport = this.map!.getViewport();

    viewport.addEventListener('pointerdown', (evt: PointerEvent) => {
      const pixel = this.map!.getEventPixel(evt);
      this.map!.forEachFeatureAtPixel(pixel, (feature) => {
        if (feature === this.labelFeature1 || feature === this.labelFeature2) {
          this.dragAircraft = feature === this.labelFeature1 ? 1 : 2;
          this.dragStartCoord = this.map!.getCoordinateFromPixel(pixel) as [number, number];
          this.dragStartOffset = this.dragAircraft === 1 ? [...this.labelOffset1] : [...this.labelOffset2];
          viewport.style.cursor = 'grabbing';
          this.setDragPanActive(false);
          this.refreshLabelPositions(this.currentIndex());
          return true;
        }
        return false;
      });
    });

    viewport.addEventListener('pointermove', (evt: PointerEvent) => {
      if (this.dragAircraft !== 0 && this.dragStartCoord && this.dragStartOffset) {
        const curCoord = this.map!.getCoordinateFromPixel(this.map!.getEventPixel(evt)) as [number, number];
        const dx = curCoord[0] - this.dragStartCoord[0];
        const dy = curCoord[1] - this.dragStartCoord[1];
        if (this.dragAircraft === 1) {
          this.labelOffset1 = [this.dragStartOffset[0] + dx, this.dragStartOffset[1] + dy];
        } else {
          this.labelOffset2 = [this.dragStartOffset[0] + dx, this.dragStartOffset[1] + dy];
        }
        this.refreshLabelPositions(this.currentIndex());
        evt.preventDefault();
        return;
      }

      // Hover detection — show pointer cursor when over a label
      const pixel = this.map!.getEventPixel(evt);
      let overLabel = false;
      this.map!.forEachFeatureAtPixel(pixel, (feature) => {
        if (feature === this.labelFeature1 || feature === this.labelFeature2) {
          overLabel = true;
          return true;
        }
        return false;
      });
      viewport.style.cursor = overLabel ? 'pointer' : '';
    });

    viewport.addEventListener('pointerup', () => {
      const wasDragging = this.dragAircraft !== 0;
      this.dragAircraft = 0;
      this.dragStartCoord = null;
      this.dragStartOffset = null;
      if (wasDragging) {
        this.setDragPanActive(true);
        this.refreshLabelPositions(this.currentIndex());
        viewport.style.cursor = '';
      }
    });
  }

  private setDragPanActive(active: boolean): void {
    this.map!.getInteractions().forEach(interaction => {
      if (interaction instanceof DragPan) {
        interaction.setActive(active);
      }
    });
  }

  private drawTrajectories(): void {
    const t1 = this.trajectory1();
    const t2 = this.trajectory2();

    if (t1.length === 0 || t2.length === 0) return;

    this.drawSegmentedTrajectory(t1, AC1_COLOR);
    this.drawSegmentedTrajectory(t2, AC2_COLOR);

    const extent = buildExtent(t1, t2);
    this.map!.getView().fit(extent, { padding: [60, 60, 60, 60] });
  }

  private drawSegmentedTrajectory(traj: readonly AircraftPosition[], color: string): void {
    const startIdx = Math.max(0, Math.min(this.eventStartCycle(), traj.length - 1));
    const endIdx = Math.max(0, Math.min(this.eventEndCycle(), traj.length - 1));

    if (startIdx > 0) {
      const coords = traj.slice(0, startIdx + 1).map(p => toMapCoord(p));
      this.trajectorySource.addFeature(makeContextPathFeature(coords, color));
    }

    const infCoords = traj.slice(startIdx, endIdx + 1).map(p => toMapCoord(p));
    this.trajectorySource.addFeature(makeInfringementPathFeature(infCoords, color));

    if (endIdx < traj.length - 1) {
      const coords = traj.slice(endIdx).map(p => toMapCoord(p));
      this.trajectorySource.addFeature(makeContextPathFeature(coords, color));
    }
  }

  private updateMarkers(index: number): void {
    this.trailSource.clear();
    this.markerSource.clear();

    this.addMarkersForAircraft(this.trajectory1(), index, AC1_COLOR);
    this.addMarkersForAircraft(this.trajectory2(), index, AC2_COLOR);
    this.refreshLabelPositions(index);
  }

  private addMarkersForAircraft(
    traj: readonly AircraftPosition[],
    index: number,
    color: string
  ): void {
    if (index < 0 || index >= traj.length) return;

    const cur = traj[index];
    const prev = index > 0 ? traj[index - 1] : null;
    const curCoord = toMapCoord(cur);

    // Trailing circles (last two positions behind the aircraft)
    for (const [offset, opacity] of [[1, 0.85], [2, 0.65]] as const) {
      const trailIdx = index - offset;
      if (trailIdx >= 0) {
        const trailFeature = new Feature(new Point(toMapCoord(traj[trailIdx])));
        trailFeature.setStyle(new Style({
          image: new RegularShape({
            points: 32,
            radius: 4.25,
            fill: new Fill({ color: colorWithOpacity(color, opacity) })
          })
        }));
        this.trailSource.addFeature(trailFeature);
      }
    }

    // Aircraft icon oriented to ground track
    const rotation = prev
      ? groundTrackRotation(cur, prev)
      : (index + 1 < traj.length ? groundTrackRotation(traj[index + 1], cur) : 0);

    const markerFeature = new Feature(new Point(curCoord));
    markerFeature.setStyle(new Style({
      image: new Icon({
        src: aircraftSvg(color),
        anchor: [0.5, 0.5],
        scale: 1,
        rotation
      })
    }));
    this.markerSource.addFeature(markerFeature);
  }

  private refreshLabelPositions(index: number): void {
    this.updateLabelForAircraft(
      this.trajectory1(), index, AC1_COLOR, this.callsign1(),
      this.labelOffset1, this.leaderFeature1, this.labelFeature1
    );
    this.updateLabelForAircraft(
      this.trajectory2(), index, AC2_COLOR, this.callsign2(),
      this.labelOffset2, this.leaderFeature2, this.labelFeature2
    );
  }

  private updateLabelForAircraft(
    traj: readonly AircraftPosition[],
    index: number,
    color: string,
    callsign: string,
    offset: [number, number],
    leaderFeature: Feature,
    labelFeature: Feature
  ): void {
    if (index < 0 || index >= traj.length) return;

    const cur = traj[index];
    const prev = index > 0 ? traj[index - 1] : null;
    const curCoord = toMapCoord(cur);
    const labelCoord: [number, number] = [curCoord[0] + offset[0], curCoord[1] + offset[1]];
    const fl = Math.round(cur.altFeet / 100);
    const trend = prev === null || cur.altFeet === prev.altFeet ? '' : cur.altFeet > prev.altFeet ? '▲' : '▼';

    const isDragging = (this.dragAircraft === 1 && labelFeature === this.labelFeature1) ||
                       (this.dragAircraft === 2 && labelFeature === this.labelFeature2);
    const labelColor = isDragging ? DRAG_HIGHLIGHT_COLOR : color;

    (leaderFeature.getGeometry() as LineString).setCoordinates([curCoord, labelCoord]);
    (labelFeature.getGeometry() as Point).setCoordinates(labelCoord);
    labelFeature.setStyle(new Style({
      text: new Text({
        text: buildLabelText(callsign, fl, trend, cur.speedKn),
        fill: new Fill({ color: labelColor }),
        font: 'bold 14px monospace',
        textAlign: 'left',
        backgroundFill: new Fill({ color: 'rgba(12, 28, 64, 0.45)' }),
        padding: [4, 8, 4, 8]
      })
    }));
  }
}

function colorWithOpacity(hex: string, opacity: number): string {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r}, ${g}, ${b}, ${opacity})`;
}

function makeContextPathFeature(coords: Array<[number, number]>, color: string): Feature {
  const feature = new Feature(new LineString(coords));
  feature.setStyle(new Style({
    stroke: new Stroke({ color: colorWithOpacity(color, 0.35), width: 1.5, lineDash: [4, 4] })
  }));
  return feature;
}

function makeInfringementPathFeature(coords: Array<[number, number]>, color: string): Feature {
  const feature = new Feature(new LineString(coords));
  feature.setStyle(new Style({ stroke: new Stroke({ color, width: 1.5 }) }));
  return feature;
}
