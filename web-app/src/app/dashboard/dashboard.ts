import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EventApiService } from '../services/event-api.service';
import type { EventSummary } from '../models/event-api.models';
import { SummaryCountWidgetComponent } from './summary-count-widget';

@Component({
  selector: 'app-dashboard',
  imports: [SummaryCountWidgetComponent],
  templateUrl: './dashboard.html'
})
export class DashboardComponent implements OnInit {
  private readonly eventApi = inject(EventApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly summary = signal<EventSummary | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.eventApi.getSummary().pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (data) => {
        this.summary.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load event summary.');
        this.loading.set(false);
      }
    });
  }
}
