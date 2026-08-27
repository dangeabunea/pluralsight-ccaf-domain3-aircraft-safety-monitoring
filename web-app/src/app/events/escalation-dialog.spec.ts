import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { DialogRef, DIALOG_DATA } from '@angular/cdk/dialog';
import { EscalationDialogComponent } from './escalation-dialog';
import type { UpdateStatusResponse } from '../models/event-api.models';

const TEST_EVENT_ID = 'evt-001';

function makeStatusResponse(overrides: Partial<UpdateStatusResponse> = {}): UpdateStatusResponse {
  return {
    id: TEST_EVENT_ID,
    status: 'ESCALATED',
    escalationReason: 'Severe infringement',
    escalatedAt: '2026-04-07T10:00:00Z',
    dismissedAt: null,
    ...overrides
  };
}

describe('EscalationDialogComponent', () => {
  let fixture: ComponentFixture<EscalationDialogComponent>;
  let controller: HttpTestingController;
  let closedWith: boolean | undefined;

  const mockDialogRef: Pick<DialogRef<boolean>, 'close'> = {
    close: (result?: boolean) => { closedWith = result; }
  };

  beforeEach(async () => {
    closedWith = undefined;

    await TestBed.configureTestingModule({
      imports: [EscalationDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DialogRef, useValue: mockDialogRef },
        { provide: DIALOG_DATA, useValue: { eventId: TEST_EVENT_ID } }
      ]
    }).compileComponents();

    controller = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EscalationDialogComponent);
    fixture.detectChanges();
  });

  afterEach(() => controller.verify());

  describe('initial state', () => {
    it('should render all six reason chips', () => {
      const chips = fixture.nativeElement.querySelectorAll('button[type="button"]');
      // 6 chips + Cancel + Confirm = 8 buttons total
      expect(chips.length).toBe(8);
    });

    it('should not call the API when confirm() is called with no reason selected', () => {
      fixture.componentInstance.confirm();
      controller.expectNone(`/api/v1/events/${TEST_EVENT_ID}/status`);
    });
  });

  describe('reason chip selection', () => {
    it('should select a reason when a chip is clicked', () => {
      fixture.componentInstance.selectReason('Severe infringement');
      expect(fixture.componentInstance.selectedReason()).toBe('Severe infringement');
    });

    it('should deselect a reason when the selected chip is clicked again', () => {
      fixture.componentInstance.selectReason('Severe infringement');
      fixture.componentInstance.selectReason('Severe infringement');
      expect(fixture.componentInstance.selectedReason()).toBeNull();
    });

    it('should allow confirm() to call the API after a reason is selected', () => {
      fixture.componentInstance.selectedReason.set('Equipment anomaly');
      fixture.componentInstance.confirm();

      const req = controller.expectOne(`/api/v1/events/${TEST_EVENT_ID}/status`);
      req.flush(makeStatusResponse({ escalationReason: 'Equipment anomaly' }));
    });
  });

  describe('on confirm', () => {
    it('should PATCH /api/v1/events/:id/status with the selected reason', () => {
      fixture.componentInstance.selectedReason.set('Potential AIRPROX');
      fixture.componentInstance.confirm();

      const req = controller.expectOne(`/api/v1/events/${TEST_EVENT_ID}/status`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ status: 'ESCALATED', escalationReason: 'Potential AIRPROX' });
      req.flush(makeStatusResponse({ escalationReason: 'Potential AIRPROX' }));
    });

    it('should append additional notes to the reason when provided', () => {
      fixture.componentInstance.selectedReason.set('Repeat occurrence');
      fixture.componentInstance.additionalNotes.set('Third time this week');
      fixture.componentInstance.confirm();

      const req = controller.expectOne(`/api/v1/events/${TEST_EVENT_ID}/status`);
      expect(req.request.body.escalationReason).toBe('Repeat occurrence — Third time this week');
      req.flush(makeStatusResponse());
    });

    it('should close the dialog with true on API success', () => {
      fixture.componentInstance.selectedReason.set('Suspected crew error');
      fixture.componentInstance.confirm();

      controller.expectOne(`/api/v1/events/${TEST_EVENT_ID}/status`).flush(makeStatusResponse());

      expect(closedWith).toBe(true);
    });

    it('should show an error message and not close the dialog when the API fails', () => {
      fixture.componentInstance.selectedReason.set('Suspected controller error');
      fixture.componentInstance.confirm();

      controller.expectOne(`/api/v1/events/${TEST_EVENT_ID}/status`)
        .error(new ErrorEvent('network'));
      fixture.detectChanges();

      expect(closedWith).toBeUndefined();
      expect(fixture.componentInstance.apiError()).toContain('Failed to escalate');
    });

    it('should re-enable the Confirm button after an API error', () => {
      fixture.componentInstance.selectedReason.set('Equipment anomaly');
      fixture.componentInstance.confirm();

      controller.expectOne(`/api/v1/events/${TEST_EVENT_ID}/status`)
        .error(new ErrorEvent('network'));
      fixture.detectChanges();

      expect(fixture.componentInstance.submitting()).toBe(false);
    });
  });

  describe('on cancel', () => {
    it('should close the dialog with undefined when Cancel is clicked', () => {
      fixture.componentInstance.cancel();
      expect(closedWith).toBeUndefined();
    });
  });
});
