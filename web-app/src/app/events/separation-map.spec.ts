import { TestBed } from '@angular/core/testing';
import { SeparationMapComponent, toOlCoord, toMapCoord, groundTrackRotation, buildExtent, findCycleIndex, buildLabelText } from './separation-map';
import type { AircraftPosition } from '../models/event-api.models';

function pos(x: number, y: number, altFeet = 35000, radarCycle = 1): AircraftPosition {
  return { radarCycle, timestampUTC: '2024-01-01T00:00:00Z', x, y, altFeet, lat: null, lon: null, speedKn: null };
}

describe('SeparationMapComponent — pure functions', () => {

  describe('toOlCoord', () => {
    it('should scale NM to meters by factor 1852', () => {
      expect(toOlCoord(1, 1)).toEqual([1852, 1852]);
    });

    it('should handle zero coordinates', () => {
      expect(toOlCoord(0, 0)).toEqual([0, 0]);
    });
  });

  describe('toMapCoord', () => {
    it('should fall back to NM-based coordinates when lat/lon are null', () => {
      expect(toMapCoord(pos(1, 1))).toEqual([1852, 1852]);
    });

    it('should use fromLonLat when lat/lon are present', () => {
      const p: AircraftPosition = { ...pos(0, 0), lat: 50.85, lon: 4.28 };
      const [x, y] = toMapCoord(p);
      expect(x).toBeCloseTo(476447, -1);
      expect(y).toBeCloseTo(6594803, -1);
    });
  });

  describe('groundTrackRotation', () => {
    it('should return π/2 for aircraft moving east (+x direction)', () => {
      const result = groundTrackRotation(pos(10, 0), pos(0, 0));
      expect(result).toBeCloseTo(Math.PI / 2, 5);
    });

    it('should return 0 for aircraft moving north (+y direction)', () => {
      const result = groundTrackRotation(pos(0, 10), pos(0, 0));
      expect(result).toBeCloseTo(0, 5);
    });

    it('should return -π/2 for aircraft moving west (-x direction)', () => {
      const result = groundTrackRotation(pos(-10, 0), pos(0, 0));
      expect(result).toBeCloseTo(-Math.PI / 2, 5);
    });

    it('should return π for aircraft moving south (-y direction)', () => {
      const result = groundTrackRotation(pos(0, -10), pos(0, 0));
      expect(result).toBeCloseTo(Math.PI, 5);
    });
  });

  describe('buildExtent', () => {
    it('should return bounding box in meters for two trajectories', () => {
      const t1 = [pos(0, 0), pos(10, 10)];
      const t2 = [pos(-5, -5), pos(5, 5)];
      const [minX, minY, maxX, maxY] = buildExtent(t1, t2);
      expect(minX).toBeCloseTo(-5 * 1852, 0);
      expect(minY).toBeCloseTo(-5 * 1852, 0);
      expect(maxX).toBeCloseTo(10 * 1852, 0);
      expect(maxY).toBeCloseTo(10 * 1852, 0);
    });
  });

  describe('findCycleIndex', () => {
    it('should return the index of the position with the matching radar cycle', () => {
      const traj = [pos(0, 0, 35000, 5), pos(1, 1, 35000, 6), pos(2, 2, 35000, 7)];
      expect(findCycleIndex(traj, 6)).toBe(1);
    });

    it('should return -1 when cycle is not present in the trajectory', () => {
      const traj = [pos(0, 0, 35000, 5), pos(1, 1, 35000, 6)];
      expect(findCycleIndex(traj, 99)).toBe(-1);
    });

    it('should return 0 for the first position cycle', () => {
      const traj = [pos(0, 0, 35000, 10), pos(1, 1, 35000, 11)];
      expect(findCycleIndex(traj, 10)).toBe(0);
    });

    it('should return the last index for the final position cycle', () => {
      const traj = [pos(0, 0, 35000, 10), pos(1, 1, 35000, 11), pos(2, 2, 35000, 12)];
      expect(findCycleIndex(traj, 12)).toBe(2);
    });
  });
});

describe('buildLabelText', () => {
  it('should return two rows (callsign + FL) when no trend and no speed', () => {
    expect(buildLabelText('BAW123', 350, '', null)).toBe('BAW123\nFL350');
  });

  it('should append ▲ on the FL row when trend is ascending', () => {
    expect(buildLabelText('BAW123', 350, '▲', null)).toBe('BAW123\nFL350 ▲');
  });

  it('should append ▼ on the FL row when trend is descending', () => {
    expect(buildLabelText('BAW123', 350, '▼', null)).toBe('BAW123\nFL350 ▼');
  });

  it('should put speed on a third row when speedKn is provided', () => {
    expect(buildLabelText('BAW123', 350, '', 450)).toBe('BAW123\nFL350\n450kt');
  });

  it('should show trend on FL row and speed on third row', () => {
    expect(buildLabelText('EZY456', 280, '▲', 420)).toBe('EZY456\nFL280 ▲\n420kt');
  });

  it('should handle descending trend with speed on third row', () => {
    expect(buildLabelText('AFR789', 310, '▼', 380)).toBe('AFR789\nFL310 ▼\n380kt');
  });
});

describe('SeparationMapComponent — component', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SeparationMapComponent]
    }).compileComponents();
  });

  it('should create without error (ngAfterViewInit not called in unit test)', () => {
    const fixture = TestBed.createComponent(SeparationMapComponent);
    fixture.componentRef.setInput('trajectory1', [pos(0, 0), pos(10, 10)]);
    fixture.componentRef.setInput('trajectory2', [pos(5, 0), pos(15, 10)]);
    fixture.componentRef.setInput('currentIndex', 0);
    fixture.componentRef.setInput('callsign1', 'BAW123');
    fixture.componentRef.setInput('callsign2', 'EZY456');
    fixture.componentRef.setInput('eventStartCycle', 1);
    fixture.componentRef.setInput('eventEndCycle', 1);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should not throw when ngOnDestroy is called before ngAfterViewInit', () => {
    const fixture = TestBed.createComponent(SeparationMapComponent);
    fixture.componentRef.setInput('trajectory1', [pos(0, 0)]);
    fixture.componentRef.setInput('trajectory2', [pos(5, 0)]);
    fixture.componentRef.setInput('currentIndex', 0);
    fixture.componentRef.setInput('callsign1', 'BAW123');
    fixture.componentRef.setInput('callsign2', 'EZY456');
    fixture.componentRef.setInput('eventStartCycle', 1);
    fixture.componentRef.setInput('eventEndCycle', 1);
    expect(() => fixture.componentInstance.ngOnDestroy()).not.toThrow();
  });
});
