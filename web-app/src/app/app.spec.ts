import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the SAFM brand in the nav', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const nav = fixture.nativeElement as HTMLElement;
    expect(nav.querySelector('nav')?.textContent).toContain('SAFM');
  });
});
