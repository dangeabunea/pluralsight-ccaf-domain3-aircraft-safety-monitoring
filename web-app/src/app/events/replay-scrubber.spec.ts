import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ReplayScrubberComponent } from './replay-scrubber';

function makeFixture(inputs: {
  maxIndex?: number;
  currentIndex?: number;
  eventStartIndex?: number;
  eventEndIndex?: number;
  startTime?: string;
  endTime?: string;
  currentTime?: string;
} = {}): ComponentFixture<ReplayScrubberComponent> {
  const fixture = TestBed.createComponent(ReplayScrubberComponent);
  const cmp = fixture.componentInstance;

  fixture.componentRef.setInput('maxIndex', inputs.maxIndex ?? 10);
  fixture.componentRef.setInput('currentIndex', inputs.currentIndex ?? 0);
  fixture.componentRef.setInput('eventStartIndex', inputs.eventStartIndex ?? 2);
  fixture.componentRef.setInput('eventEndIndex', inputs.eventEndIndex ?? 8);
  fixture.componentRef.setInput('startTime', inputs.startTime ?? '14:21:00');
  fixture.componentRef.setInput('endTime', inputs.endTime ?? '14:25:00');
  fixture.componentRef.setInput('currentTime', inputs.currentTime ?? '14:21:00');

  fixture.detectChanges();
  return fixture;
}

describe('ReplayScrubberComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReplayScrubberComponent]
    }).compileComponents();
  });

  describe('scrubbar range input', () => {
    it('should render scrubbar with correct max attribute', () => {
      const fixture = makeFixture({ maxIndex: 15 });
      const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type="range"]');
      expect(input).toBeTruthy();
      expect(input.max).toBe('15');
    });

    it('should set the range input value to currentIndex', () => {
      const fixture = makeFixture({ maxIndex: 10, currentIndex: 4 });
      const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type="range"]');
      expect(input.value).toBe('4');
    });
  });

  describe('indexChange output', () => {
    it('should emit indexChange when input event fires', () => {
      const fixture = makeFixture({ maxIndex: 10 });
      const emitted: number[] = [];
      fixture.componentInstance.indexChange.subscribe((v: number) => emitted.push(v));

      const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type="range"]');
      input.value = '6';
      input.dispatchEvent(new Event('input'));

      expect(emitted).toEqual([6]);
    });
  });

  describe('event tick marks', () => {
    it('should position event start tick at the correct percentage', () => {
      // maxIndex=10, eventStartIndex=2 → 20%
      const fixture = makeFixture({ maxIndex: 10, eventStartIndex: 2 });
      const ticks = Array.from(
        fixture.nativeElement.querySelectorAll('[data-tick]') as NodeListOf<HTMLElement>
      );
      const startTick = ticks.find(el => el.dataset['tick'] === 'start');

      expect(startTick).toBeTruthy();
      expect(startTick!.style.left).toBe('20%');
    });

    it('should position event end tick at the correct percentage', () => {
      // maxIndex=10, eventEndIndex=8 → 80%
      const fixture = makeFixture({ maxIndex: 10, eventEndIndex: 8 });
      const ticks = Array.from(
        fixture.nativeElement.querySelectorAll('[data-tick]') as NodeListOf<HTMLElement>
      );
      const endTick = ticks.find(el => el.dataset['tick'] === 'end');

      expect(endTick).toBeTruthy();
      expect(endTick!.style.left).toBe('80%');
    });
  });

  describe('time labels', () => {
    it('should render startTime and endTime labels', () => {
      const fixture = makeFixture({ startTime: '14:21:00', endTime: '14:25:00' });
      const text = fixture.nativeElement.textContent as string;
      expect(text).toContain('14:21:00');
      expect(text).toContain('14:25:00');
    });

    it('should not render currentTime as floating label (removed to avoid overlap)', () => {
      const fixture = makeFixture({ currentTime: '14:22:35' });
      const text = fixture.nativeElement.textContent as string;
      expect(text).not.toContain('14:22:35');
    });
  });
});
