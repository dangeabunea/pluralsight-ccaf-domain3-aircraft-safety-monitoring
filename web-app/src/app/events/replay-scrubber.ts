import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-replay-scrubber',
  templateUrl: './replay-scrubber.html'
})
export class ReplayScrubberComponent {
  readonly maxIndex = input.required<number>();
  readonly currentIndex = input.required<number>();
  readonly eventStartIndex = input.required<number>();
  readonly eventEndIndex = input.required<number>();
  readonly startTime = input.required<string>();
  readonly endTime = input.required<string>();
  readonly currentTime = input.required<string>();

  readonly indexChange = output<number>();

  onScrub(event: Event): void {
    this.indexChange.emit(+(event.target as HTMLInputElement).value);
  }

  /** Returns a percentage string for positioning elements on the scrubbar track. */
  pct(index: number): string {
    const max = this.maxIndex();
    if (max === 0) return '0%';
    return `${(index / max) * 100}%`;
  }
}
