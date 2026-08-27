import { Component, EventEmitter, Output } from '@angular/core';

@Component({
  selector: 'app-event-actions',
  templateUrl: './event-actions.html'
})
export class EventActionsComponent {
  @Output() readonly dismiss = new EventEmitter<void>();
  @Output() readonly escalate = new EventEmitter<void>();
}
