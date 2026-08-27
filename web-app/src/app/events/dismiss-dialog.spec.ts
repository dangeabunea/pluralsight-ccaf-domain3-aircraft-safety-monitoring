import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { DialogRef, DIALOG_DATA } from '@angular/cdk/dialog';
import { DismissDialogComponent } from './dismiss-dialog';
import type { UpdateStatusResponse } from '../models/event-api.models';

const TEST_EVENT_ID = 'evt-001';

function makeStatusResponse(overrides: Partial<UpdateStatusResponse> = {}): UpdateStatusResponse {
  return {
    id: TEST_EVENT_ID,
    status: 'DISMISSED',
    escalationReason: null,
    escalatedAt: null,
    dismissedAt: '2026-04-07T10:00:00Z',
    ...overrides
  };
}

describe('DismissDialogComponent', () => {
  let fixture: ComponentFixture<DismissDialogComponent>;
  let controller: HttpTestingController;
  let closedWith: boolean | undefined;

  const mockDialogRef: Pick<DialogRef<boolean>, 'close'> = {
    close: (result?: boolean) => { closedWith = result; }
  };

  beforeEach(async () => {
    closedWith = undefined;

    await TestBed.configureTestingModule({
      imports: [DismissDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DialogRef, useValue: mockDialogRef },
        { provide: DIALOG_DATA, useValue: { eventId: TEST_EVENT_ID } }
      ]
    }).compileComponents();

    controller = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DismissDialogComponent);
    fixture.detectChanges();
  });

  afterEach(() => controller.verify());

  describe('on cancel', () => {
    it('should close the dialog with undefined when Cancel is clicked', () => {
      fixture.componentInstance.cancel();
      expect(closedWith).toBeUndefined();
    });

    it('should not trigger an API call when Cancel is clicked', () => {
      fixture.componentInstance.cancel();
      controller.expectNone(`/api/v1/events/${TEST_EVENT_ID}/status`);
    });
  });

  describe('on confirm', () => {
    it('should PATCH /api/v1/events/:id/status with status DISMISSED', () => {
      fixture.componentInstance.confirm();

      const req = controller.expectOne(`/api/v1/events/${TEST_EVENT_ID}/status`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ status: 'DISMISSED' });
      req.flush(makeStatusResponse());
    });

    it('should close the dialog with true on API success', () => {
      fixture.componentInstance.confirm();

      controller.expectOne(`/api/v1/events/${TEST_EVENT_ID}/status`).flush(makeStatusResponse());

      expect(closedWith).toBe(true);
    });

    it('should close the dialog on API failure without showing an inline error', () => {
      fixture.componentInstance.confirm();

      controller.expectOne(`/api/v1/events/${TEST_EVENT_ID}/status`)
        .error(new ErrorEvent('network'));
      fixture.detectChanges();

      // Dialog closes on error — analyst cannot recover, so no inline error message is shown.
      expect(closedWith).toBeUndefined();
    });

    it('should not make a second API call if confirm() is called while already submitting', () => {
      fixture.componentInstance.confirm();
      fixture.componentInstance.confirm();

      // Only one request should have been made
      controller.expectOne(`/api/v1/events/${TEST_EVENT_ID}/status`).flush(makeStatusResponse());
    });
  });
});
