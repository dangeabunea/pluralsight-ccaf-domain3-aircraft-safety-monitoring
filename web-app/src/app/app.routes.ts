import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadComponent: () => import('./dashboard/dashboard').then(m => m.DashboardComponent)
  },
  {
    path: 'events',
    loadComponent: () => import('./events/events-page').then(m => m.EventsPageComponent)
  },
  {
    path: 'events/:id',
    loadComponent: () => import('./events/event-detail').then(m => m.EventDetailComponent)
  }
];
