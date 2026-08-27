import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { NavComponent } from './nav';

describe('NavComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NavComponent],
      providers: [
        provideRouter([
          { path: 'dashboard', component: NavComponent },
          { path: 'events', component: NavComponent },
          { path: 'events/:id', component: NavComponent }
        ])
      ]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(NavComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should display the SAFM logo text', () => {
    const fixture = TestBed.createComponent(NavComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('nav')?.textContent).toContain('SAFM');
  });

  it('should render the SVG logo icon', () => {
    const fixture = TestBed.createComponent(NavComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('svg path')).toBeTruthy();
  });

  it('should have a Dashboard link pointing to /dashboard', () => {
    const fixture = TestBed.createComponent(NavComponent);
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('a[href="/dashboard"]');
    expect(link).toBeTruthy();
    expect(link.textContent.trim()).toBe('Dashboard');
  });

  it('should have an Events link pointing to /events', () => {
    const fixture = TestBed.createComponent(NavComponent);
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('a[href="/events"]');
    expect(link).toBeTruthy();
    expect(link.textContent.trim()).toBe('Events');
  });

  it('should mark Dashboard as active when navigated to /dashboard', async () => {
    const fixture = TestBed.createComponent(NavComponent);
    const router = TestBed.inject(Router);
    fixture.detectChanges();

    await router.navigate(['/dashboard']);
    fixture.detectChanges();

    const dashboardLink = fixture.nativeElement.querySelector('a[href="/dashboard"]');
    expect(dashboardLink.classList.contains('nav-link-active')).toBe(true);
  });

  it('should mark Events as active when navigated to /events', async () => {
    const fixture = TestBed.createComponent(NavComponent);
    const router = TestBed.inject(Router);
    fixture.detectChanges();

    await router.navigate(['/events']);
    fixture.detectChanges();

    const eventsLink = fixture.nativeElement.querySelector('a[href="/events"]');
    expect(eventsLink.classList.contains('nav-link-active')).toBe(true);
  });

  it('should mark Events as active when navigated to /events/:id', async () => {
    const fixture = TestBed.createComponent(NavComponent);
    const router = TestBed.inject(Router);
    fixture.detectChanges();

    await router.navigate(['/events', '42']);
    fixture.detectChanges();

    const eventsLink = fixture.nativeElement.querySelector('a[href="/events"]');
    expect(eventsLink.classList.contains('nav-link-active')).toBe(true);
  });

  it('should NOT mark Events as active when navigated to /events?tab=escalated', async () => {
    const fixture = TestBed.createComponent(NavComponent);
    const router = TestBed.inject(Router);
    fixture.detectChanges();

    await router.navigate(['/events'], { queryParams: { tab: 'escalated' } });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const eventsLinks = fixture.nativeElement.querySelectorAll('a[href="/events"]');
    const eventsLink = Array.from(eventsLinks).find((el) =>
      (el as HTMLElement).textContent?.trim() === 'Events'
    ) as HTMLElement;
    expect(eventsLink.classList.contains('nav-link-active')).toBe(false);
  });

  it('should mark Escalated as active when navigated to /events?tab=escalated', async () => {
    const fixture = TestBed.createComponent(NavComponent);
    const router = TestBed.inject(Router);
    fixture.detectChanges();

    await router.navigate(['/events'], { queryParams: { tab: 'escalated' } });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const escalatedLink = fixture.nativeElement.querySelector('a[href="/events?tab=escalated"]');
    expect(escalatedLink).toBeTruthy();
    expect(escalatedLink.classList.contains('nav-link-active')).toBe(true);
  });
});
