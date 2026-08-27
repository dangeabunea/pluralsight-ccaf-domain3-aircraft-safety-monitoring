import { Component, DestroyRef, OnInit, inject, signal, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY } from 'rxjs';
import { switchMap, catchError } from 'rxjs/operators';
import { Dialog } from '@angular/cdk/dialog';
import { EventApiService } from '../services/event-api.service';
import { ToastService } from '../shared/toast/toast.service';
import type { InfringementDetail, EventComment } from '../models/event-api.models';
import { EventMetadataComponent } from './event-metadata';
import { EventActionsComponent } from './event-actions';
import { EventNotesComponent } from './event-notes';
import { EscalationDialogComponent, type EscalationDialogData } from './escalation-dialog';
import { DismissDialogComponent, type DismissDialogData } from './dismiss-dialog';
import { ReplayScrubberComponent } from './replay-scrubber';
import { ReplayStateService } from './replay-state.service';
import { CurrentSeparationPanelComponent } from './current-separation-panel';
import { SeparationMapComponent } from './separation-map';
import { VerticalProfilePanelComponent } from './vertical-profile-panel';

@Component({
  selector: 'app-event-detail',
  imports: [RouterLink, EventMetadataComponent, EventActionsComponent, EventNotesComponent, ReplayScrubberComponent, CurrentSeparationPanelComponent, SeparationMapComponent, VerticalProfilePanelComponent],
  providers: [ReplayStateService],
  templateUrl: './event-detail.html'
})
export class EventDetailComponent implements OnInit {
  private readonly eventApi = inject(EventApiService);
  private readonly dialog = inject(Dialog);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly replay = inject(ReplayStateService);

  readonly detail = signal<InfringementDetail | null>(null);
  readonly comments = signal<EventComment[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly panelCollapsed = signal(false);
  readonly showVerticalProfile = signal(false);
  readonly speeds: readonly (1 | 2 | 4)[] = [1, 2, 4];

  ngOnInit(): void {
    // switchMap cancels in-flight requests if the route param changes before the response arrives.
    this.route.params.pipe(
      switchMap(params => {
        this.replay.rewind();
        this.loading.set(true);
        this.error.set(null);
        return this.eventApi.getEventDetail(params['id']).pipe(
          catchError(() => {
            this.error.set('Failed to load event details.');
            this.loading.set(false);
            return EMPTY;
          })
        );
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(detail => {
      this.detail.set(detail);
      this.comments.set(
        [...detail.comments].sort((a, b) => a.createdAt.localeCompare(b.createdAt))
      );
      this.loading.set(false);
    });
  }

  onAddComment(text: string): void {
    const id = this.detail()?.id;
    if (!id) return;
    this.eventApi.addComment(id, text, 'analyst@atc.eu')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: comment => this.comments.update(list => [...list, comment])
      });
  }

  onDismiss(): void {
    const id = this.detail()?.id;
    if (!id) return;

    const ref = this.dialog.open<boolean, DismissDialogData>(DismissDialogComponent, {
      data: { eventId: id },
      backdropClass: 'safm-dialog-backdrop',
      ariaLabel: 'Dismiss safety event'
    });

    ref.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(result => {
      if (result) {
        // Update status locally so the actions panel disappears without a re-fetch.
        this.detail.update(d => d ? { ...d, status: 'DISMISSED' } : d);
        this.toast.show('Event dismissed successfully');
      }
    });
  }

  onEscalate(): void {
    const id = this.detail()?.id;
    if (!id) return;

    const ref = this.dialog.open<boolean, EscalationDialogData>(EscalationDialogComponent, {
      data: { eventId: id },
      backdropClass: 'safm-dialog-backdrop',
      ariaLabel: 'Escalate safety event'
    });

    ref.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(result => {
      if (result) {
        // Update status locally so the actions panel disappears without a re-fetch.
        this.detail.update(d => d ? { ...d, status: 'ESCALATED' } : d);
        this.toast.show('Event escalated successfully');
      }
    });
  }

  onPlay(): void {
    const d = this.detail();
    if (!d) return;
    this.replay.play(d.trajectory1.length - 1);
  }

  onPause(): void {
    this.replay.pause();
  }

  onRewind(): void {
    this.replay.rewind();
  }

  onJumpToMinHSep(): void {
    const d = this.detail();
    if (!d) return;
    const maxIndex = d.trajectory1.length - 1;
    this.replay.seek(Math.min(Math.max(0, d.minSeparationCycleIndex), maxIndex));
  }

  onJumpToMinVSep(): void {
    const d = this.detail();
    if (!d || d.minVerticalSeparationCycleIndex === null) return;
    const maxIndex = d.trajectory1.length - 1;
    this.replay.seek(Math.min(Math.max(0, d.minVerticalSeparationCycleIndex), maxIndex));
  }

  /**
   * Computed scrubber inputs derived from the loaded trajectory.
   * Centralises the findIndex calls so strict templates don't see nullable `d` inside arrow fns.
   */
  readonly scrubberData = computed(() => {
    const d = this.detail();
    if (!d || d.trajectory1.length === 0) return null;

    const idx = this.replay.currentIndex();
    const last = d.trajectory1.length - 1;
    const safeIdx = Math.min(idx, last);

    return {
      maxIndex: last,
      eventStartIndex: Math.max(0, Math.min(d.eventStartCycle, d.trajectory1.length - 1)),
      eventEndIndex: Math.max(0, Math.min(d.eventEndCycle, d.trajectory1.length - 1)),
      startTime: d.trajectory1[0].timestampUTC.slice(11, 19),
      endTime: d.trajectory1[last].timestampUTC.slice(11, 19),
      currentTime: d.trajectory1[safeIdx].timestampUTC.slice(11, 19)
    };
  });
}
