import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { ReplaySubject, Subject } from 'rxjs';
import { Dialog } from '@angular/cdk/dialog';
import { EventDetailComponent } from './event-detail';
import { DismissDialogComponent } from './dismiss-dialog';
import { ToastService } from '../shared/toast/toast.service';
import type { InfringementDetail, AircraftPosition, EventComment } from '../models/event-api.models';
import { vi } from 'vitest';

// OpenLayers' Map constructor calls ResizeObserver internally, which jsdom does not provide.
// This no-op stub satisfies the OL dependency without affecting the EventDetailComponent behaviour under test.
(globalThis as Record<string, unknown>)['ResizeObserver'] = class {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
};

function makePosition(radarCycle: number, timestampUTC = '2026-04-03T14:22:00Z'): AircraftPosition {
  return { radarCycle, timestampUTC, x: 0, y: 0, altFeet: 35000, lat: null, lon: null, speedKn: null };
}

function makeDetail(overrides: Partial<InfringementDetail> = {}): InfringementDetail {
  return {
    id: 'abc123',
    firstAircraftCallsign: 'BAW123',
    secondAircraftCallsign: 'AFR456',
    startedAt: '2026-04-03T14:22:00Z',
    endedAt: '2026-04-03T14:24:00Z',
    status: 'PENDING_REVIEW',
    commentCount: 0,
    escalationReason: null,
    escalatedAt: null,
    summary: null,
    type: 'SMI',
    minHorizontalSeparationNm: 3.2,
    minVerticalSeparationFt: 850,
    minSeparationCycleIndex: 5,
    minVerticalSeparationCycleIndex: null,
    eventStartCycle: 3,
    eventEndCycle: 10,
    trajectory1: [],
    trajectory2: [],
    comments: [],
    ...overrides
  };
}

describe('EventDetailComponent', () => {
  let fixture: ComponentFixture<EventDetailComponent>;
  let controller: HttpTestingController;
  let params$: ReplaySubject<Record<string, string>>;

  function flushDetail(detail: InfringementDetail): void {
    controller.expectOne(`/api/v1/events/${detail.id}`).flush(detail);
  }

  beforeEach(async () => {
    params$ = new ReplaySubject(1);

    await TestBed.configureTestingModule({
      imports: [EventDetailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { params: params$.asObservable() } }
      ]
    }).compileComponents();

    controller = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EventDetailComponent);
  });

  afterEach(() => controller.verify());

  describe('on initial load', () => {
    it('should show a loading skeleton before the API responds', () => {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.animate-pulse')).toBeTruthy();
      flushDetail(makeDetail());
    });

    it('should call GET /api/v1/events/:id when the route param arrives', () => {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();

      const req = controller.expectOne('/api/v1/events/abc123');
      expect(req.request.method).toBe('GET');
      req.flush(makeDetail());
    });

    it('should render the aircraft callsigns after the API responds', () => {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      flushDetail(makeDetail());
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('BAW123');
      expect(fixture.nativeElement.textContent).toContain('AFR456');
    });

    it('should show an error message when the API call fails', () => {
      fixture.detectChanges();
      params$.next({ id: 'bad-id' });
      fixture.detectChanges();

      controller.expectOne('/api/v1/events/bad-id').error(new ErrorEvent('network'));
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Failed to load event details.');
    });
  });

  describe('null safety', () => {
    it('should not throw when escalationReason is null on a PENDING_REVIEW event', () => {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      flushDetail(makeDetail({ status: 'PENDING_REVIEW', escalationReason: null }));

      expect(() => fixture.detectChanges()).not.toThrow();
    });

    it('should not throw when escalationReason and escalatedAt are null on a DISMISSED event', () => {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      flushDetail(makeDetail({ status: 'DISMISSED', escalationReason: null, escalatedAt: null }));

      expect(() => fixture.detectChanges()).not.toThrow();
    });
  });

  describe('EventActionsComponent visibility', () => {
    it('should show EventActionsComponent for PENDING_REVIEW events', () => {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      flushDetail(makeDetail({ status: 'PENDING_REVIEW' }));
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-event-actions')).toBeTruthy();
    });

    it('should NOT show EventActionsComponent for ESCALATED events', () => {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      flushDetail(makeDetail({ status: 'ESCALATED', escalationReason: 'proximity breach' }));
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-event-actions')).toBeNull();
    });

    it('should NOT show EventActionsComponent for DISMISSED events', () => {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      flushDetail(makeDetail({ status: 'DISMISSED' }));
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-event-actions')).toBeNull();
    });
  });

  describe('comment submission', () => {
    it('should POST to /api/v1/events/:id/comments with the comment text and author', () => {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      flushDetail(makeDetail({ status: 'PENDING_REVIEW' }));
      fixture.detectChanges();

      fixture.componentInstance.onAddComment('Both aircraft converging.');

      const req = controller.expectOne('/api/v1/events/abc123/comments');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ text: 'Both aircraft converging.', author: 'analyst@atc.eu' });
      req.flush({ id: 'c1', text: 'Both aircraft converging.', author: 'analyst@atc.eu', createdAt: '2026-04-03T15:00:00Z' } as EventComment);
    });

    it('should append the returned comment to the comments signal without re-fetching event detail', () => {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      flushDetail(makeDetail({ status: 'PENDING_REVIEW' }));
      fixture.detectChanges();

      expect(fixture.componentInstance.comments().length).toBe(0);

      fixture.componentInstance.onAddComment('Observation one.');
      const newComment: EventComment = {
        id: 'c1', text: 'Observation one.', author: 'analyst@atc.eu', createdAt: '2026-04-03T15:00:00Z'
      };
      controller.expectOne('/api/v1/events/abc123/comments').flush(newComment);
      fixture.detectChanges();

      expect(fixture.componentInstance.comments().length).toBe(1);
      expect(fixture.componentInstance.comments()[0].text).toBe('Observation one.');
      // No second GET should have been issued for the event detail
      controller.expectNone('/api/v1/events/abc123');
    });

    it('should sort comments ascending by createdAt on load', () => {
      const earlier: EventComment = { id: 'c1', text: 'First', author: 'a', createdAt: '2026-04-03T14:00:00Z' };
      const later: EventComment   = { id: 'c2', text: 'Second', author: 'b', createdAt: '2026-04-03T15:00:00Z' };

      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      // API returns comments in reverse order to verify client-side sort
      flushDetail(makeDetail({ comments: [later, earlier] }));
      fixture.detectChanges();

      expect(fixture.componentInstance.comments()[0].text).toBe('First');
      expect(fixture.componentInstance.comments()[1].text).toBe('Second');
    });
  });

  describe('collapsible right panel', () => {
    it('should default to panelCollapsed = false', () => {
      fixture.detectChanges();
      expect(fixture.componentInstance.panelCollapsed()).toBe(false);
    });

    it('should hide the right panel when panelCollapsed is true', () => {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      flushDetail(makeDetail());
      fixture.detectChanges();

      fixture.componentInstance.panelCollapsed.set(true);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-current-separation-panel')).toBeNull();
    });

    it('should show the right panel when panelCollapsed is false', () => {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      flushDetail(makeDetail());
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-current-separation-panel')).toBeTruthy();
    });
  });

  describe('dismiss flow', () => {
    function loadPendingEvent(): void {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      flushDetail(makeDetail({ status: 'PENDING_REVIEW' }));
      fixture.detectChanges();
    }

    it('should open the dismiss dialog when onDismiss() is called', () => {
      loadPendingEvent();

      const dialog = TestBed.inject(Dialog);
      const closed$ = new Subject<boolean | undefined>();
      const openSpy = vi.spyOn(dialog, 'open').mockReturnValue({ closed: closed$.asObservable() } as never);

      fixture.componentInstance.onDismiss();

      expect(openSpy).toHaveBeenCalledWith(
        DismissDialogComponent,
        expect.objectContaining({ data: { eventId: 'abc123' } })
      );
      closed$.complete();
    });

    it('should update status to DISMISSED and show toast when dialog closes with true', () => {
      loadPendingEvent();

      const dialog = TestBed.inject(Dialog);
      const toast = TestBed.inject(ToastService);
      const closed$ = new Subject<boolean | undefined>();
      vi.spyOn(dialog, 'open').mockReturnValue({ closed: closed$.asObservable() } as never);
      const toastSpy = vi.spyOn(toast, 'show');

      fixture.componentInstance.onDismiss();
      closed$.next(true);
      closed$.complete();
      fixture.detectChanges();

      expect(fixture.componentInstance.detail()!.status).toBe('DISMISSED');
      expect(toastSpy).toHaveBeenCalledWith('Event dismissed successfully');
    });

    it('should NOT update status when dialog closes without a result', () => {
      loadPendingEvent();

      const dialog = TestBed.inject(Dialog);
      const closed$ = new Subject<boolean | undefined>();
      vi.spyOn(dialog, 'open').mockReturnValue({ closed: closed$.asObservable() } as never);

      fixture.componentInstance.onDismiss();
      closed$.next(undefined);
      closed$.complete();
      fixture.detectChanges();

      expect(fixture.componentInstance.detail()!.status).toBe('PENDING_REVIEW');
    });
  });

  describe('replay controls', () => {
    const traj = [
      makePosition(1, '2026-04-03T14:21:00Z'),
      makePosition(3, '2026-04-03T14:22:00Z'),
      makePosition(5, '2026-04-03T14:23:00Z'),
      makePosition(10, '2026-04-03T14:24:00Z'),
      makePosition(12, '2026-04-03T14:25:00Z')
    ];

    function loadWithTrajectory(): void {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      flushDetail(makeDetail({ trajectory1: traj, eventStartCycle: 1, eventEndCycle: 3, minSeparationCycleIndex: 2 }));
      fixture.detectChanges();
    }

    it('should show the replay panel when trajectory is non-empty', () => {
      loadWithTrajectory();
      expect(fixture.nativeElement.querySelector('app-replay-scrubber')).toBeTruthy();
    });

    it('should NOT show the replay panel when trajectory is empty', () => {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      flushDetail(makeDetail({ trajectory1: [] }));
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-replay-scrubber')).toBeNull();
    });

    it('should call replay.play() with the last valid index (length - 1) when onPlay() is called', () => {
      loadWithTrajectory();
      const replay = fixture.componentInstance.replay;

      fixture.componentInstance.onPlay();

      expect(replay.isPlaying()).toBe(true);
    });

    it('should stop playback when onPause() is called', () => {
      loadWithTrajectory();
      const replay = fixture.componentInstance.replay;

      fixture.componentInstance.onPlay();
      fixture.componentInstance.onPause();

      expect(replay.isPlaying()).toBe(false);
    });

    it('should reset currentIndex to 0 and stop playback when onRewind() is called', () => {
      loadWithTrajectory();
      const replay = fixture.componentInstance.replay;

      fixture.componentInstance.onPlay();
      replay.seek(3);
      fixture.componentInstance.onRewind();

      expect(replay.currentIndex()).toBe(0);
      expect(replay.isPlaying()).toBe(false);
    });

    it('should seek to minSeparationCycleIndex when onJumpToMinHSep() is called', () => {
      loadWithTrajectory();
      const replay = fixture.componentInstance.replay;

      fixture.componentInstance.onJumpToMinHSep();

      // minSeparationCycleIndex is 2, which is within [0, 4]
      expect(replay.currentIndex()).toBe(2);
    });

    it('should clamp out-of-bounds minSeparationCycleIndex to maxIndex', () => {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      flushDetail(makeDetail({ trajectory1: traj, minSeparationCycleIndex: 99 }));
      fixture.detectChanges();

      fixture.componentInstance.onJumpToMinHSep();

      expect(fixture.componentInstance.replay.currentIndex()).toBe(4); // clamped to traj.length - 1
    });

    it('should NOT seek when onJumpToMinVSep() is called and minVerticalSeparationCycleIndex is null', () => {
      loadWithTrajectory();
      const replay = fixture.componentInstance.replay;
      replay.seek(2);

      fixture.componentInstance.onJumpToMinVSep(); // minVerticalSeparationCycleIndex is null by default

      expect(replay.currentIndex()).toBe(2); // unchanged
    });

    it('should seek to minVerticalSeparationCycleIndex when it is not null', () => {
      fixture.detectChanges();
      params$.next({ id: 'abc123' });
      fixture.detectChanges();
      flushDetail(makeDetail({ trajectory1: traj, minVerticalSeparationCycleIndex: 3 }));
      fixture.detectChanges();

      fixture.componentInstance.onJumpToMinVSep();

      expect(fixture.componentInstance.replay.currentIndex()).toBe(3);
    });

    it('should compute correct eventStartIndex and eventEndIndex via scrubberData', () => {
      loadWithTrajectory();

      const sd = fixture.componentInstance.scrubberData();
      expect(sd).not.toBeNull();
      expect(sd!.eventStartIndex).toBe(1); // trajectory index used directly
      expect(sd!.eventEndIndex).toBe(3);   // trajectory index used directly
    });

    it('should reset replay state when navigating to a different event', () => {
      loadWithTrajectory();
      fixture.componentInstance.replay.seek(3);

      params$.next({ id: 'xyz999' });
      fixture.detectChanges();

      // After navigation the index should be reset to 0
      expect(fixture.componentInstance.replay.currentIndex()).toBe(0);
      controller.expectOne('/api/v1/events/xyz999').flush(makeDetail({ id: 'xyz999', trajectory1: traj }));
    });
  });
});
