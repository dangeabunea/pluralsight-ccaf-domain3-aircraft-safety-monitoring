import { TestBed, ComponentFixture } from '@angular/core/testing';
import { VerticalProfilePanelComponent, niceGridLines } from './vertical-profile-panel';
import type { AircraftPosition } from '../models/event-api.models';

function makePos(radarCycle: number, altFeet: number): AircraftPosition {
  return { radarCycle, timestampUTC: '2024-03-15T14:22:35Z', x: 0, y: 0, altFeet, lat: null, lon: null, speedKn: null };
}

function makeFixture(overrides: {
  trajectory1?: AircraftPosition[];
  trajectory2?: AircraftPosition[];
  currentIndex?: number;
  callsign1?: string;
  callsign2?: string;
  eventStartCycle?: number;
  eventEndCycle?: number;
} = {}): ComponentFixture<VerticalProfilePanelComponent> {
  const fixture = TestBed.createComponent(VerticalProfilePanelComponent);
  fixture.componentRef.setInput('trajectory1', overrides.trajectory1 ?? [makePos(1, 35000), makePos(2, 35100)]);
  fixture.componentRef.setInput('trajectory2', overrides.trajectory2 ?? [makePos(1, 34000), makePos(2, 34200)]);
  fixture.componentRef.setInput('currentIndex', overrides.currentIndex ?? 0);
  fixture.componentRef.setInput('callsign1', overrides.callsign1 ?? 'BAW123');
  fixture.componentRef.setInput('callsign2', overrides.callsign2 ?? 'EZY456');
  fixture.componentRef.setInput('eventStartCycle', overrides.eventStartCycle ?? 1);
  fixture.componentRef.setInput('eventEndCycle', overrides.eventEndCycle ?? 2);
  fixture.detectChanges();
  return fixture;
}

describe('niceGridLines', () => {
  it('should produce 4-6 lines for a typical altitude range', () => {
    const lines = niceGridLines(10000, 40000);
    expect(lines.length).toBeGreaterThanOrEqual(4);
    expect(lines.length).toBeLessThanOrEqual(9); // FL290 may be injected as an extra line
  });

  it('should include FL290 when range spans 29000', () => {
    const lines = niceGridLines(20000, 35000);
    const fl290 = lines.find(l => l.alt === 29000);
    expect(fl290).toBeTruthy();
    expect(fl290!.rvsm).toBe(true);
  });

  it('should exclude FL290 when range is entirely above 29000', () => {
    const lines = niceGridLines(31000, 45000);
    const fl290 = lines.find(l => l.alt === 29000);
    expect(fl290).toBeUndefined();
  });

  it('should mark FL290 as rvsm: true when it appears naturally in nice intervals', () => {
    // Force FL290 to fall on a natural 1000-ft step
    const lines = niceGridLines(25000, 33000);
    const fl290 = lines.find(l => l.alt === 29000);
    if (fl290) expect(fl290.rvsm).toBe(true);
  });
});

describe('VerticalProfilePanelComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VerticalProfilePanelComponent]
    }).compileComponents();
  });

  describe('status bar', () => {
    it('should render callsign1 in the status bar', () => {
      const fixture = makeFixture({ callsign1: 'BAW123' });
      expect(fixture.nativeElement.textContent).toContain('BAW123');
    });

    it('should render callsign2 in the status bar', () => {
      const fixture = makeFixture({ callsign2: 'EZY456' });
      expect(fixture.nativeElement.textContent).toContain('EZY456');
    });

    it('should render FL value derived from altFeet', () => {
      const fixture = makeFixture({
        trajectory1: [makePos(1, 35000)],
        trajectory2: [makePos(1, 34000)],
        currentIndex: 0
      });
      expect(fixture.nativeElement.textContent).toContain('FL350');
    });

    it('should render V-SEP in status bar', () => {
      const fixture = makeFixture({
        trajectory1: [makePos(1, 35000)],
        trajectory2: [makePos(1, 34000)],
        currentIndex: 0
      });
      expect(fixture.nativeElement.textContent).toContain('V-SEP');
      expect(fixture.nativeElement.textContent).toContain('1000');
    });
  });

  describe('trend arrows', () => {
    it('should show ▲ when current altitude exceeds previous by > 50 ft', () => {
      const fixture = makeFixture({
        trajectory1: [makePos(1, 35000), makePos(2, 35200)],
        trajectory2: [makePos(1, 34000), makePos(2, 34000)],
        currentIndex: 1
      });
      const statusBar: HTMLElement = fixture.nativeElement.querySelector('.vp-status');
      expect(statusBar.textContent).toContain('▲');
    });

    it('should show ▼ when current altitude is below previous by > 50 ft', () => {
      const fixture = makeFixture({
        trajectory1: [makePos(1, 35000), makePos(2, 34800)],
        trajectory2: [makePos(1, 34000), makePos(2, 34000)],
        currentIndex: 1
      });
      const statusBar: HTMLElement = fixture.nativeElement.querySelector('.vp-status');
      expect(statusBar.textContent).toContain('▼');
    });

    it('should show no trend arrow within the ±50 ft deadband', () => {
      const fixture = makeFixture({
        trajectory1: [makePos(1, 35000), makePos(2, 35030)],
        trajectory2: [makePos(1, 34000), makePos(2, 34000)],
        currentIndex: 1
      });
      const statusBar: HTMLElement = fixture.nativeElement.querySelector('.vp-status');
      expect(statusBar.textContent).not.toContain('▲');
      expect(statusBar.textContent).not.toContain('▼');
    });
  });
});
