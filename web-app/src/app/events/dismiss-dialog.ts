import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DialogRef, DIALOG_DATA } from '@angular/cdk/dialog';
import { EventApiService } from '../services/event-api.service';

export interface DismissDialogData {
  readonly eventId: string;
}

@Component({
  selector: 'app-dismiss-dialog',
  templateUrl: './dismiss-dialog.html',
  // The CDK overlay panel IS this host element — give it block layout and fixed width.
  host: { style: 'display: block; width: 400px; max-width: calc(100vw - 2rem);' }
})
export class DismissDialogComponent {
  private readonly dialogRef = inject<DialogRef<boolean>>(DialogRef);
  private readonly data = inject<DismissDialogData>(DIALOG_DATA);
  private readonly eventApi = inject(EventApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly submitting = signal(false);

  confirm(): void {
    if (this.submitting()) return;

    this.submitting.set(true);

    this.eventApi.changeStatus(this.data.eventId, { status: 'DISMISSED' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.dialogRef.close(true),
        // On failure the dialog closes — there is nothing for the analyst to recover.
        // This is intentionally different from EscalationDialogComponent which keeps the
        // dialog open so the analyst can retry with a different reason.
        error: () => this.dialogRef.close()
      });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
