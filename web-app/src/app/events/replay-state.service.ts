import { Injectable, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval, Subscription } from 'rxjs';

@Injectable()
export class ReplayStateService {
  private readonly destroyRef = inject(DestroyRef);
  private sub: Subscription | null = null;
  private maxIndex = 0;

  readonly currentIndex = signal(0);
  readonly isPlaying = signal(false);
  readonly speed = signal<1 | 2 | 4>(1);

  play(maxIndex: number): void {
    if (this.isPlaying()) return;
    this.maxIndex = maxIndex;
    this.isPlaying.set(true);
    this.startInterval();
  }

  pause(): void {
    this.isPlaying.set(false);
    this.sub?.unsubscribe();
    this.sub = null;
  }

  rewind(): void {
    this.pause();
    this.currentIndex.set(0);
  }

  seek(index: number): void {
    this.pause();
    this.currentIndex.set(index);
  }

  setSpeed(speed: 1 | 2 | 4): void {
    this.speed.set(speed);
    if (this.isPlaying()) {
      this.sub?.unsubscribe();
      this.startInterval();
    }
  }

  private startInterval(): void {
    this.sub = interval(5000 / this.speed())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        const next = this.currentIndex() + 1;
        if (next >= this.maxIndex) {
          this.currentIndex.set(this.maxIndex);
          this.isPlaying.set(false);
          this.sub?.unsubscribe();
          this.sub = null;
        } else {
          this.currentIndex.set(next);
        }
      });
  }
}
