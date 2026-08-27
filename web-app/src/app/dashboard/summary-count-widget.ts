import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-summary-count-widget',
  templateUrl: './summary-count-widget.html',
  imports: [RouterLink]
})
export class SummaryCountWidgetComponent {
  readonly count = input<number | null>(null);
  readonly label = input.required<string>();
  readonly colour = input.required<'blue' | 'amber'>();
  readonly routerLink = input.required<string>();
  readonly linkLabel = input.required<string>();
  readonly subtitle = input.required<string>();
}
