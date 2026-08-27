import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { DashboardComponent } from './dashboard';

describe('DashboardComponent', () => {
  let controller: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    controller.verify();
  });

  describe('on initialisation', () => {
    it('should call GET /api/v1/events/summary', () => {
      const fixture = TestBed.createComponent(DashboardComponent);
      fixture.detectChanges();
      controller.expectOne('/api/v1/events/summary').flush({ pendingCount: 0, escalatedCount: 0 });
    });

    it('should be in loading state before the HTTP response arrives', () => {
      const fixture = TestBed.createComponent(DashboardComponent);
      fixture.detectChanges();

      expect(fixture.componentInstance.loading()).toBe(true);

      controller.expectOne('/api/v1/events/summary').flush({ pendingCount: 0, escalatedCount: 0 });
    });
  });

  describe('when the summary call succeeds', () => {
    it('should display the counts returned by the API', () => {
      const fixture = TestBed.createComponent(DashboardComponent);
      fixture.detectChanges();
      controller.expectOne('/api/v1/events/summary').flush({ pendingCount: 7, escalatedCount: 3 });
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('7');
      expect(el.textContent).toContain('3');
      expect(el.textContent).toContain('Pending Review');
      expect(el.textContent).toContain('Escalated');
    });

    it('should display 0 — not blank — when both counts are zero', () => {
      const fixture = TestBed.createComponent(DashboardComponent);
      fixture.detectChanges();
      controller.expectOne('/api/v1/events/summary').flush({ pendingCount: 0, escalatedCount: 0 });
      fixture.detectChanges();

      const widgets = Array.from<HTMLElement>(
        fixture.nativeElement.querySelectorAll('app-summary-count-widget')
      );
      expect(widgets.length).toBe(2);
      widgets.forEach(widget => expect(widget.textContent).toContain('0'));
    });

    it('should render the placeholder widget div', () => {
      const fixture = TestBed.createComponent(DashboardComponent);
      fixture.detectChanges();
      controller.expectOne('/api/v1/events/summary').flush({ pendingCount: 1, escalatedCount: 0 });
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.placeholder-widget')).toBeTruthy();
    });
  });

  describe('when the summary call fails', () => {
    it('should display an error message', () => {
      const fixture = TestBed.createComponent(DashboardComponent);
      fixture.detectChanges();
      controller
        .expectOne('/api/v1/events/summary')
        .flush('error', { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Failed to load event summary');
    });

    it('should render the placeholder widget div even on error', () => {
      const fixture = TestBed.createComponent(DashboardComponent);
      fixture.detectChanges();
      controller
        .expectOne('/api/v1/events/summary')
        .flush('error', { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.placeholder-widget')).toBeTruthy();
    });
  });
});
