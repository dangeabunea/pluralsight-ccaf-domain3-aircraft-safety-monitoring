import { Component, EventEmitter, Input, Output } from '@angular/core';
import type { EventComment } from '../models/event-api.models';
import { AddCommentComponent } from './add-comment';

@Component({
  selector: 'app-event-notes',
  imports: [AddCommentComponent],
  templateUrl: './event-notes.html'
})
export class EventNotesComponent {
  @Input({ required: true }) comments!: EventComment[];
  @Output() readonly addComment = new EventEmitter<string>();

  formatCommentDate(iso: string): string {
    const d = new Date(iso);
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                    'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${months[d.getMonth()]} ${d.getDate()}, ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }
}
