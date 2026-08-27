import { TestBed, ComponentFixture } from '@angular/core/testing';
import { CurrentSeparationPanelComponent } from './current-separation-panel';
import type { AircraftPosition } from '../models/event-api.models';

// 1 NM = 1852 metres. Aircraft positions are in metres on x,y axes.
// Helper: build a minimal AircraftPosition at the given x,y,alt.
function makePosition(x: number, y: number, altFeet: number, timestampUTC = '2024-03-15T14:22:35Z'): AircraftPosition {
  return { radarCycle: 1, timestampUTC, x, y, altFeet, lat: null, lon: null, speedKn: null };
}

// Build two-position trajectories separated by the given horizontal distance (NM) and vertical (ft).
// Aircraft 1 is always at origin. Aircraft 2 is placed along the x-axis.
function makeTrajectories(hNm: number, vFt: number, timestamp = '2024-03-15T14:22:35Z'): {
  t1: AircraftPosition[];
  t2: AircraftPosition[];
} {
  const hMetres = hNm * 1852;
  return {
    t1: [makePosition(0, 0, 10000, timestamp)],
    t2: [makePosition(hMetres, 0, 10000 + vFt, timestamp)]
  };
}

function makeFixture(inputs: {
  trajectory1?: AircraftPosition[];
  trajectory2?: AircraftPosition[];
  currentIndex?: number;
} = {}): ComponentFixture<CurrentSeparationPanelComponent> {
  const { t1, t2 } = makeTrajectories(3.4, 875);
  const fixture = TestBed.createComponent(CurrentSeparationPanelComponent);

  fixture.componentRef.setInput('trajectory1', inputs.trajectory1 ?? t1);
  fixture.componentRef.setInput('trajectory2', inputs.trajectory2 ?? t2);
  fixture.componentRef.setInput('currentIndex', inputs.currentIndex ?? 0);

  fixture.detectChanges();
  return fixture;
}

describe('CurrentSeparationPanelComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CurrentSeparationPanelComponent]
    }).compileComponents();
  });

  describe('horizontal separation display', () => {
    it('should display horizontal separation in NM with 2 decimal places', () => {
      // 3.4 NM horizontal — aircraft 2 at (3.4 * 1852) metres on x-axis
      const { t1, t2 } = makeTrajectories(3.4, 875);
      const fixture = makeFixture({ trajectory1: t1, trajectory2: t2 });

      const text: string = fixture.nativeElement.textContent;
      expect(text).toContain('3.40');
    });
  });

  describe('vertical separation display', () => {
    it('should display vertical separation in whole feet', () => {
      const { t1, t2 } = makeTrajectories(3.4, 875);
      const fixture = makeFixture({ trajectory1: t1, trajectory2: t2 });

      const text: string = fixture.nativeElement.textContent;
      expect(text).toContain('875');
    });
  });

  describe('amber alert state', () => {
    it('should apply amber background when both H < 5 NM and V < 1000 ft', () => {
      // Both thresholds breached: 3 NM and 800 ft
      const { t1, t2 } = makeTrajectories(3, 800);
      const fixture = makeFixture({ trajectory1: t1, trajectory2: t2 });

      const panel: HTMLElement = fixture.nativeElement.querySelector('[data-panel]');
      expect(panel).toBeTruthy();
      expect(panel.style.background).toBe('rgb(255, 247, 237)'); // #fff7ed
    });

    it('should NOT apply amber when only horizontal threshold is breached (H < 5, V >= 1000)', () => {
      const { t1, t2 } = makeTrajectories(3, 1200);
      const fixture = makeFixture({ trajectory1: t1, trajectory2: t2 });

      const panel: HTMLElement = fixture.nativeElement.querySelector('[data-panel]');
      expect(panel).toBeTruthy();
      expect(panel.style.background).not.toBe('rgb(255, 247, 237)');
    });

    it('should NOT apply amber when only vertical threshold is breached (H >= 5, V < 1000)', () => {
      const { t1, t2 } = makeTrajectories(6, 800);
      const fixture = makeFixture({ trajectory1: t1, trajectory2: t2 });

      const panel: HTMLElement = fixture.nativeElement.querySelector('[data-panel]');
      expect(panel).toBeTruthy();
      expect(panel.style.background).not.toBe('rgb(255, 247, 237)');
    });
  });

  describe('currentIndex tracking', () => {
    it('should update displayed values when currentIndex changes', () => {
      // Index 0: 2 NM, 500 ft   →  index 1: 7 NM, 500 ft
      const t1: AircraftPosition[] = [
        makePosition(0, 0, 10000, '2024-03-15T14:22:35Z'),
        makePosition(0, 0, 10000, '2024-03-15T14:22:40Z')
      ];
      const t2: AircraftPosition[] = [
        makePosition(2 * 1852, 0, 10500, '2024-03-15T14:22:35Z'),
        makePosition(7 * 1852, 0, 10500, '2024-03-15T14:22:40Z')
      ];

      const fixture = makeFixture({ trajectory1: t1, trajectory2: t2, currentIndex: 0 });
      expect(fixture.nativeElement.textContent).toContain('2.00');

      fixture.componentRef.setInput('currentIndex', 1);
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('7.00');
    });
  });

  describe('initial render', () => {
    it('should render without error at currentIndex 0', () => {
      const { t1, t2 } = makeTrajectories(3.4, 875);
      expect(() => makeFixture({ trajectory1: t1, trajectory2: t2, currentIndex: 0 })).not.toThrow();
    });
  });

  describe('time display', () => {
    it('should display the HH:MM:SS portion of the current position timestamp', () => {
      const { t1, t2 } = makeTrajectories(3.4, 875, '2024-03-15T14:22:35Z');
      const fixture = makeFixture({ trajectory1: t1, trajectory2: t2 });

      const text: string = fixture.nativeElement.textContent;
      expect(text).toContain('14:22:35');
    });
  });
});
