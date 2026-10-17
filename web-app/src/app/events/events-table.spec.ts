import { TestBed } from '@angular/core/testing';
import { EventsTableComponent } from './events-table';

const START = '2025-01-01T10:00:00Z';

function endAfter(seconds: number): string {
  return new Date(new Date(START).getTime() + seconds * 1000).toISOString();
}

describe('EventsTableComponent.computeDuration', () => {
  let component: EventsTableComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [EventsTableComponent] });
    component = TestBed.createComponent(EventsTableComponent).componentInstance;
  });

  it('should return "< 1 min" when duration is 50 seconds', () => {
    // arrange
    const endedAt = endAfter(50);

    // act
    const actual = component.computeDuration(START, endedAt);

    // assert
    expect(actual).toBe('< 1 min');
  });

  it('should return "3 min" when duration is 3 minutes', () => {
    // arrange
    const endedAt = endAfter(3 * 60);

    // act
    const actual = component.computeDuration(START, endedAt);

    // assert
    expect(actual).toBe('3 min');
  });

  it('should return "3 min" when duration is 3 minutes 30 seconds', () => {
    // arrange
    const endedAt = endAfter(3 * 60 + 30);

    // act
    const actual = component.computeDuration(START, endedAt);

    // assert
    expect(actual).toBe('3 min');
  });

  it('should return "1h 3m" when duration is 1 hour 3 minutes', () => {
    // arrange
    const endedAt = endAfter(63 * 60);

    // act
    const actual = component.computeDuration(START, endedAt);

    // assert
    expect(actual).toBe('1h 3m');
  });
});
