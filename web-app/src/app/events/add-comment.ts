import { Component, EventEmitter, Output, signal } from '@angular/core';

@Component({
  selector: 'app-add-comment',
  templateUrl: './add-comment.html'
})
export class AddCommentComponent {
  @Output() readonly submitted = new EventEmitter<string>();

  readonly draftText = signal('');

  onSubmit(): void {
    const text = this.draftText().trim();
    if (!text) return;
    this.submitted.emit(text);
    this.draftText.set('');
  }
}
