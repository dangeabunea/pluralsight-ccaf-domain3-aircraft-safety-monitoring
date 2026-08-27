import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SummaryCountWidgetComponent } from './summary-count-widget';

describe('SummaryCountWidgetComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SummaryCountWidgetComponent],
      providers: [provideRouter([])]
    });
  });

  it('should render the count and label passed as inputs', () => {
    const fixture = TestBed.createComponent(SummaryCountWidgetComponent);
    fixture.componentRef.setInput('count', 7);
    fixture.componentRef.setInput('label', 'Pending Review');
    fixture.componentRef.setInput('colour', 'blue');
    fixture.componentRef.setInput('routerLink', '/events');
    fixture.componentRef.setInput('linkLabel', 'Go to Events');
    fixture.componentRef.setInput('subtitle', 'separation events awaiting review');
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('7');
    expect(el.textContent).toContain('Pending Review');
  });

  it('should render 0 — not blank — when count is zero', () => {
    const fixture = TestBed.createComponent(SummaryCountWidgetComponent);
    fixture.componentRef.setInput('count', 0);
    fixture.componentRef.setInput('label', 'Escalated');
    fixture.componentRef.setInput('colour', 'amber');
    fixture.componentRef.setInput('routerLink', '/events');
    fixture.componentRef.setInput('linkLabel', 'View Escalated');
    fixture.componentRef.setInput('subtitle', 'events forwarded to safety board');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('0');
  });

  it('should render -- when count is null (error/fallback state)', () => {
    const fixture = TestBed.createComponent(SummaryCountWidgetComponent);
    fixture.componentRef.setInput('count', null);
    fixture.componentRef.setInput('label', 'Pending Review');
    fixture.componentRef.setInput('colour', 'blue');
    fixture.componentRef.setInput('routerLink', '/events');
    fixture.componentRef.setInput('linkLabel', 'Go to Events');
    fixture.componentRef.setInput('subtitle', 'separation events awaiting review');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('--');
  });
});
