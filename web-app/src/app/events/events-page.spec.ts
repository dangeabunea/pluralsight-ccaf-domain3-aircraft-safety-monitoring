import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { ReplaySubject } from 'rxjs';
import { EventsPageComponent } from './events-page';
import type { PagedResponse, InfringementListItem, EventSummary } from '../models/event-api.models';

function makeEvent(overrides: Partial<InfringementListItem> = {}): InfringementListItem {
  return {
    id: 'e1',
    firstAircraftCallsign: 'BAW123',
    secondAircraftCallsign: 'AFR456',
    startedAt: '2026-04-03T14:22:00Z',
    endedAt: '2026-04-03T14:24:00Z',
    status: 'PENDING_REVIEW',
    commentCount: 2,
    escalationReason: null,
    escalatedAt: null,
    summary: 'BAW123 / AFR456 — Min sep: 850 ft / 3.2 NM',
    type: 'SMI',
    minHorizontalSeparationNm: 3.2,
    minVerticalSeparationFt: 850,
    ...overrides
  };
}

function makePagedResponse(items: InfringementListItem[], total = items.length): PagedResponse<InfringementListItem> {
  return { content: items, totalElements: total, totalPages: Math.ceil(total / 20), page: 0, size: 20 };
}

const SUMMARY: EventSummary = { pendingCount: 12, escalatedCount: 3 };

describe('EventsPageComponent', () => {
  let controller: HttpTestingController;
  let queryParams$: ReplaySubject<Record<string, string>>;

  function flushInitialRequests(events: InfringementListItem[] = [makeEvent()], total = 1): void {
    controller.expectOne('/api/v1/events/summary').flush(SUMMARY);
    controller.expectOne(r => r.url === '/api/v1/events').flush(makePagedResponse(events, total));
  }

  beforeEach(async () => {
    queryParams$ = new ReplaySubject(1);
    queryParams$.next({});

    await TestBed.configureTestingModule({
      imports: [EventsPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParams: queryParams$.asObservable() } }
      ]
    }).compileComponents();

    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  describe('on initialisation', () => {
    it('should default to the pending tab', () => {
      const fixture = TestBed.createComponent(EventsPageComponent);
      fixture.detectChanges();
      flushInitialRequests();

      expect(fixture.componentInstance.tab()).toBe('pending');
    });

    it('should call GET /api/v1/events/summary for tab counts', () => {
      const fixture = TestBed.createComponent(EventsPageComponent);
      fixture.detectChanges();

      controller.expectOne('/api/v1/events/summary').flush(SUMMARY);
      controller.expectOne(r => r.url === '/api/v1/events').flush(makePagedResponse([]));
    });

    it('should call the events API with PENDING_REVIEW status by default', () => {
      const fixture = TestBed.createComponent(EventsPageComponent);
      fixture.detectChanges();

      controller.expectOne('/api/v1/events/summary').flush(SUMMARY);
      const req = controller.expectOne(r => r.url === '/api/v1/events');
      expect(req.request.params.get('status')).toBe('PENDING_REVIEW');
      req.flush(makePagedResponse([]));
    });

    it('should show a loading indicator before the HTTP response arrives', () => {
      const fixture = TestBed.createComponent(EventsPageComponent);
      fixture.detectChanges();

      expect(fixture.componentInstance.loading()).toBe(true);

      flushInitialRequests();
    });

    it('should display events returned by the API', () => {
      const fixture = TestBed.createComponent(EventsPageComponent);
      fixture.detectChanges();
      flushInitialRequests([makeEvent({ id: 'e1', firstAircraftCallsign: 'BAW123' })]);
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('BAW123');
    });

    it('should populate tab counts from the summary response', () => {
      const fixture = TestBed.createComponent(EventsPageComponent);
      fixture.detectChanges();
      flushInitialRequests();
      fixture.detectChanges();

      expect(fixture.componentInstance.pendingCount()).toBe(12);
      expect(fixture.componentInstance.escalatedCount()).toBe(3);
    });
  });

  describe('when the tab query param is "escalated"', () => {
    it('should call the events API with ESCALATED status', () => {
      queryParams$.next({ tab: 'escalated' });
      const fixture = TestBed.createComponent(EventsPageComponent);
      fixture.detectChanges();

      controller.expectOne('/api/v1/events/summary').flush(SUMMARY);
      const req = controller.expectOne(r => r.url === '/api/v1/events');
      expect(req.request.params.get('status')).toBe('ESCALATED');
      req.flush(makePagedResponse([]));
    });

    it('should set the tab signal to "escalated"', () => {
      queryParams$.next({ tab: 'escalated' });
      const fixture = TestBed.createComponent(EventsPageComponent);
      fixture.detectChanges();

      controller.expectOne('/api/v1/events/summary').flush(SUMMARY);
      controller.expectOne(r => r.url === '/api/v1/events').flush(makePagedResponse([]));

      expect(fixture.componentInstance.tab()).toBe('escalated');
    });
  });

  describe('when navigating to the next page', () => {
    it('should call the API with the updated page number', () => {
      const fixture = TestBed.createComponent(EventsPageComponent);
      fixture.detectChanges();
      flushInitialRequests([makeEvent()], 40);
      fixture.detectChanges();

      fixture.componentInstance.goToPage(1);

      controller.expectOne(r => {
        return r.url === '/api/v1/events' && r.params.get('page') === '1';
      }).flush(makePagedResponse([makeEvent({ id: 'e2' })], 40));
    });
  });
});
