import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly _active = signal(false);
  private readonly _message = signal('');
  private dismissTimer?: ReturnType<typeof setTimeout>;

  readonly active = this._active.asReadonly();
  readonly message = this._message.asReadonly();

  show(text: string, durationMs = 3000): void {
    clearTimeout(this.dismissTimer);
    this._message.set(text);
    this._active.set(true);
    this.dismissTimer = setTimeout(() => this._active.set(false), durationMs);
  }
}
