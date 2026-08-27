import { TestBed, ComponentFixture } from '@angular/core/testing';
import { AircraftInfoBoxComponent } from './aircraft-info-box';
import type { AircraftPosition } from '../models/event-api.models';

function makePosition(
  x: number,
  y: number,
  altFeet: number,
  speedKn: number | null = null,
  timestampUTC = '2024-03-15T14:22:35Z'
): AircraftPosition {
  return { radarCycle: 1, timestampUTC, x, y, altFeet, lat: null, lon: null, speedKn };
}

function makeFixture(inputs: {
  trajectory?: AircraftPosition[];
  currentIndex?: number;
  colour?: string;
  callsign?: string;
}): ComponentFixture<AircraftInfoBoxComponent> {
  const fixture = TestBed.createComponent(AircraftInfoBoxComponent);
  fixture.componentRef.setInput('trajectory', inputs.trajectory ?? [makePosition(0, 0, 35000, 481)]);
  fixture.componentRef.setInput('currentIndex', inputs.currentIndex ?? 0);
  fixture.componentRef.setInput('colour', inputs.colour ?? '#f5c842');
  fixture.componentRef.setInput('callsign', inputs.callsign ?? 'BAW123');
  fixture.detectChanges();
  return fixture;
}

describe('AircraftInfoBoxComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AircraftInfoBoxComponent]
    }).compileComponents();
  });

  describe('callsign display', () => {
    it('should display the callsign', () => {
      const fixture = makeFixture({ callsign: 'BAW123' });
      expect(fixture.nativeElement.textContent).toContain('BAW123');
    });
  });

  describe('flight level display', () => {
    it('should format altitude as FL notation (35000 ft → FL350)', () => {
      const fixture = makeFixture({
        trajectory: [makePosition(0, 0, 35000)],
        currentIndex: 0
      });
      expect(fixture.nativeElement.textContent).toContain('FL350');
    });
  });

  describe('altitude trend', () => {
    it('should show ascending arrow when altFeet increases from previous position', () => {
      const trajectory = [
        makePosition(0, 0, 34000),
        makePosition(10, 10, 35000)
      ];
      const fixture = makeFixture({ trajectory, currentIndex: 1 });
      expect(fixture.nativeElement.textContent).toContain('↑');
    });

    it('should show descending arrow when altFeet decreases from previous position', () => {
      const trajectory = [
        makePosition(0, 0, 35000),
        makePosition(10, 10, 34000)
      ];
      const fixture = makeFixture({ trajectory, currentIndex: 1 });
      expect(fixture.nativeElement.textContent).toContain('↓');
    });

    it('should show level dash when altFeet is equal to previous position', () => {
      const trajectory = [
        makePosition(0, 0, 35000),
        makePosition(10, 10, 35000)
      ];
      const fixture = makeFixture({ trajectory, currentIndex: 1 });
      expect(fixture.nativeElement.textContent).toContain('—');
    });
  });

  describe('initial position (currentIndex === 0)', () => {
    it('should show level dash and no track when currentIndex is 0', () => {
      const fixture = makeFixture({
        trajectory: [makePosition(0, 0, 35000)],
        currentIndex: 0
      });
      const text: string = fixture.nativeElement.textContent;
      // No previous position — trend is — and track is --
      expect(text).toContain('—');
      expect(text).toContain('--');
    });
  });

  describe('ground track', () => {
    it('should display ground track in degrees when currentIndex >= 1', () => {
      // Aircraft moves east (+x), so track should be 0°
      const trajectory = [
        makePosition(0, 0, 35000),
        makePosition(100, 0, 35000)
      ];
      const fixture = makeFixture({ trajectory, currentIndex: 1 });
      expect(fixture.nativeElement.textContent).toContain('°');
    });
  });

  describe('speed display', () => {
    it('should show speed formatted as "481 kt"', () => {
      const fixture = makeFixture({
        trajectory: [makePosition(0, 0, 35000, 481)],
        currentIndex: 0
      });
      expect(fixture.nativeElement.textContent).toContain('481 kt');
    });

    it('should show "--" when speedKn is null', () => {
      const fixture = makeFixture({
        trajectory: [makePosition(0, 0, 35000, null)],
        currentIndex: 0
      });
      expect(fixture.nativeElement.textContent).toContain('--');
    });
  });
});
