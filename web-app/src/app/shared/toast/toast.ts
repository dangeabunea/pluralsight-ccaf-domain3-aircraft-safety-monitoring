import { Component, inject } from '@angular/core';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-toast',
  templateUrl: './toast.html'
})
export class ToastComponent {
  readonly toast = inject(ToastService);
}
