import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DialogRef, DIALOG_DATA } from '@angular/cdk/dialog';
import { EventApiService } from '../services/event-api.service';

export interface EscalationDialogData {
  readonly eventId: string;
}

const ESCALATION_REASONS = [
  'Severe infringement',
  'Suspected crew error',
  'Suspected controller error',
  'Equipment anomaly',
  'Repeat occurrence',
  'Potential AIRPROX'
] as const;

@Component({
  selector: 'app-escalation-dialog',
  templateUrl: './escalation-dialog.html',
  // The CDK overlay panel IS this host element — give it block layout and fixed width.
  host: { style: 'display: block; width: 480px; max-width: calc(100vw - 2rem);' }
})
export class EscalationDialogComponent {
  private readonly dialogRef = inject<DialogRef<boolean>>(DialogRef);
  private readonly data = inject<EscalationDialogData>(DIALOG_DATA);
  private readonly eventApi = inject(EventApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly reasons = ESCALATION_REASONS;
  readonly selectedReason = signal<string | null>(null);
  readonly additionalNotes = signal('');
  readonly submitting = signal(false);
  readonly apiError = signal<string | null>(null);

  readonly MAX_NOTES = 500;

  chipStyle(reason: string): Record<string, string> {
    if (this.selectedReason() === reason) {
      return { background: '#fffbeb', color: '#d97706', 'border-color': '#fde68a' };
    }
    return {
      background: 'var(--color-card-bg)',
      color: 'var(--color-text-secondary)',
      'border-color': 'var(--color-border)'
    };
  }

  selectReason(reason: string): void {
    this.selectedReason.set(this.selectedReason() === reason ? null : reason);
  }

  onNotesInput(event: Event): void {
    const value = (event.target as HTMLTextAreaElement).value;
    this.additionalNotes.set(value.slice(0, this.MAX_NOTES));
  }

  confirm(): void {
    const reason = this.selectedReason();
    if (!reason || this.submitting()) return;

    this.submitting.set(true);
    this.apiError.set(null);

    const notes = this.additionalNotes().trim();
    const fullReason = notes ? `${reason} — ${notes}` : reason;

    this.eventApi.changeStatus(this.data.eventId, { status: 'ESCALATED', escalationReason: fullReason })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.dialogRef.close(true),
        error: () => {
          this.submitting.set(false);
          this.apiError.set('Failed to escalate event. Please try again.');
        }
      });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
