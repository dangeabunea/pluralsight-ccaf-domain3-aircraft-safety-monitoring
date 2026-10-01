import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, EMPTY } from 'rxjs';
import { switchMap, catchError } from 'rxjs/operators';
import { EventApiService } from '../services/event-api.service';
import type { InfringementListItem } from '../models/event-api.models';
import { EventsTableComponent } from './events-table';

type Tab = 'pending' | 'escalated';

@Component({
  selector: 'app-events-page',
  imports: [EventsTableComponent],
  templateUrl: './events-page.html'
})
export class EventsPageComponent implements OnInit {
  private readonly eventApi = inject(EventApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly tab = signal<Tab>('pending');
  readonly page = signal(0);
  readonly events = signal<InfringementListItem[]>([]);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly pendingCount = signal<number | null>(null);
  readonly escalatedCount = signal<number | null>(null);

  // switchMap ensures that if the user changes tabs before the current request
  // completes, the in-flight HTTP call is cancelled and only the latest fires.
  private readonly loadTrigger$ = new Subject<void>();
  private readonly PAGE_SIZE = 20;

  constructor() {
    this.loadTrigger$
      .pipe(
        switchMap(() => {
          this.loading.set(true);
          const status = this.tab() === 'pending' ? 'PENDING_REVIEW' : 'ESCALATED';
          const sort = this.tab() === 'pending' ? 'startedAt' : 'escalatedAt';
          const size = this.tab() === 'escalated' ? 0 : this.PAGE_SIZE;
          return this.eventApi.listEvents(status, this.page(), size, sort, 'asc').pipe(
            catchError(() => {
              this.loading.set(false);
              return EMPTY;
            })
          );
        }),
        takeUntilDestroyed()
      )
      .subscribe(data => {
        this.events.set(data.content);
        this.totalPages.set(data.totalPages);
        this.totalElements.set(data.totalElements);
        this.loading.set(false);
      });
  }

  ngOnInit(): void {
    this.refreshSummary();

    this.route.queryParams
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(params => {
        const newTab: Tab = params['tab'] === 'escalated' ? 'escalated' : 'pending';
        this.tab.set(newTab);
        this.page.set(0);
        this.loadTrigger$.next();
      });
  }

  switchTab(tab: Tab): void {
    this.router.navigate([], {
      queryParams: { tab },
      queryParamsHandling: 'merge'
    });
  }

  goToPage(p: number): void {
    this.page.set(p);
    this.loadTrigger$.next();
  }

  onView(id: string): void {
    this.router.navigate(['/events', id]);
  }

  private refreshSummary(): void {
    this.eventApi.getSummary()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(summary => {
        this.pendingCount.set(summary.pendingCount);
        this.escalatedCount.set(summary.escalatedCount);
      });
  }
}
