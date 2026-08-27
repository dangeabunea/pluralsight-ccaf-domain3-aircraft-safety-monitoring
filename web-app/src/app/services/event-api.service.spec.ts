import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { EventApiService } from './event-api.service';
import type {
  EventSummary,
  InfringementListItem,
  InfringementDetail,
  EventComment,
  PagedResponse,
  UpdateStatusResponse
} from '../models/event-api.models';

describe('EventApiService', () => {
  let service: EventApiService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [EventApiService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(EventApiService);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    controller.verify();
  });

  describe('getSummary', () => {
    it('should issue GET /api/v1/events/summary and return Observable<EventSummary>', () => {
      const expected: EventSummary = { pendingCount: 3, escalatedCount: 1 };
      let actual: EventSummary | undefined;

      service.getSummary().subscribe(s => (actual = s));

      const req = controller.expectOne('/api/v1/events/summary');
      expect(req.request.method).toBe('GET');
      req.flush(expected);
      expect(actual).toEqual(expected);
    });
  });

  describe('listEvents', () => {
    it('should issue GET /api/v1/events with status, page, and size params', () => {
      const expected: PagedResponse<InfringementListItem> = {
        content: [],
        totalElements: 0,
        totalPages: 0,
        page: 0,
        size: 20
      };
      let actual: PagedResponse<InfringementListItem> | undefined;

      service.listEvents('PENDING_REVIEW', 0, 20).subscribe(r => (actual = r));

      const req = controller.expectOne(
        r =>
          r.url === '/api/v1/events' &&
          r.params.get('status') === 'PENDING_REVIEW' &&
          r.params.get('page') === '0' &&
          r.params.get('size') === '20' &&
          r.params.get('sort') === null
      );
      expect(req.request.method).toBe('GET');
      req.flush(expected);
      expect(actual).toEqual(expected);
    });

    it('should append combined sort query param when sort and order are provided', () => {
      service.listEvents('ESCALATED', 1, 10, 'startedAt', 'desc').subscribe();

      const req = controller.expectOne(
        r => r.url === '/api/v1/events' && r.params.get('sort') === 'startedAt,desc'
      );
      expect(req.request.method).toBe('GET');
      req.flush({ content: [], totalElements: 0, totalPages: 0, page: 1, size: 10 });
    });

    it('should omit the sort param when only a sort field is provided without order', () => {
      service.listEvents('DISMISSED', 0, 20, 'startedAt').subscribe();

      const req = controller.expectOne(
        r => r.url === '/api/v1/events' && r.params.get('sort') === 'startedAt'
      );
      req.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });
    });
  });

  describe('getEventDetail', () => {
    it('should issue GET /api/v1/events/{id} and return Observable<InfringementDetail>', () => {
      const id = 'abc-123';
      const expected = { id } as unknown as InfringementDetail;
      let actual: InfringementDetail | undefined;

      service.getEventDetail(id).subscribe(d => (actual = d));

      const req = controller.expectOne(`/api/v1/events/${id}`);
      expect(req.request.method).toBe('GET');
      req.flush(expected);
      expect(actual).toEqual(expected);
    });
  });

  describe('addComment', () => {
    it('should issue POST /api/v1/events/{id}/comments with text and author in the body', () => {
      const id = 'evt-1';
      const expected: EventComment = {
        id: 'cmt-1',
        text: 'looks fine',
        author: 'Jane',
        createdAt: '2026-01-01T00:00:00Z'
      };
      let actual: EventComment | undefined;

      service.addComment(id, 'looks fine', 'Jane').subscribe(c => (actual = c));

      const req = controller.expectOne(`/api/v1/events/${id}/comments`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ text: 'looks fine', author: 'Jane' });
      req.flush(expected);
      expect(actual).toEqual(expected);
    });
  });

  describe('changeStatus', () => {
    it('should issue PATCH /api/v1/events/{id}/status with a DISMISSED request body', () => {
      const id = 'evt-2';
      const expected: UpdateStatusResponse = {
        id,
        status: 'DISMISSED',
        escalationReason: null,
        escalatedAt: null,
        dismissedAt: '2026-01-02T00:00:00Z'
      };
      let actual: UpdateStatusResponse | undefined;

      service.changeStatus(id, { status: 'DISMISSED' }).subscribe(r => (actual = r));

      const req = controller.expectOne(`/api/v1/events/${id}/status`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ status: 'DISMISSED' });
      req.flush(expected);
      expect(actual).toEqual(expected);
    });

    it('should send escalationReason when the request status is ESCALATED', () => {
      const id = 'evt-3';

      service
        .changeStatus(id, { status: 'ESCALATED', escalationReason: 'proximity breach' })
        .subscribe();

      const req = controller.expectOne(`/api/v1/events/${id}/status`);
      expect(req.request.body).toEqual({
        status: 'ESCALATED',
        escalationReason: 'proximity breach'
      });
      req.flush({
        id,
        status: 'ESCALATED',
        escalationReason: 'proximity breach',
        escalatedAt: '2026-01-03T00:00:00Z',
        dismissedAt: null
      });
    });
  });
});
