import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import type {
  EventSummary,
  InfringementListItem,
  InfringementDetail,
  EventComment,
  PagedResponse,
  UpdateStatusRequest,
  UpdateStatusResponse
} from '../models/event-api.models';

@Injectable({ providedIn: 'root' })
export class EventApiService {
  private readonly http = inject(HttpClient);

  private readonly BASE_URL = '/api/v1/events';

  getSummary(): Observable<EventSummary> {
    return this.http.get<EventSummary>(`${this.BASE_URL}/summary`);
  }

  listEvents(
    status: string,
    page: number,
    size: number,
    sort?: string,
    order?: string
  ): Observable<PagedResponse<InfringementListItem>> {
    let params = new HttpParams().set('status', status).set('page', page).set('size', size);
    if (sort) {
      params = params.set('sort', order ? `${sort},${order}` : sort);
    }
    return this.http.get<PagedResponse<InfringementListItem>>(this.BASE_URL, { params });
  }

  getEventDetail(id: string): Observable<InfringementDetail> {
    return this.http.get<InfringementDetail>(`${this.BASE_URL}/${id}`);
  }

  addComment(id: string, text: string, author: string): Observable<EventComment> {
    return this.http.post<EventComment>(`${this.BASE_URL}/${id}/comments`, { text, author });
  }

  changeStatus(id: string, request: UpdateStatusRequest): Observable<UpdateStatusResponse> {
    return this.http.patch<UpdateStatusResponse>(`${this.BASE_URL}/${id}/status`, request);
  }
}
