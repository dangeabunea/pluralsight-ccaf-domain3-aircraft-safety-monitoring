export interface EventSummary {
  pendingCount: number;
  escalatedCount: number;
}

export interface InfringementListItem {
  id: string;
  firstAircraftCallsign: string;
  secondAircraftCallsign: string;
  startedAt: string;
  endedAt: string;
  status: 'PENDING_REVIEW' | 'ESCALATED' | 'DISMISSED';
  commentCount: number;
  escalationReason: string | null;
  escalatedAt: string | null;
  summary: string | null;
  type: string;
  minHorizontalSeparationNm: number | null;
  minVerticalSeparationFt: number | null;
}

export interface AircraftPosition {
  radarCycle: number;
  timestampUTC: string;
  x: number;
  y: number;
  altFeet: number;
  lat: number | null;
  lon: number | null;
  speedKn: number | null;
}

export interface EventComment {
  id: string;
  text: string;
  createdAt: string;
  author: string;
}

export interface InfringementDetail extends InfringementListItem {
  minSeparationCycleIndex: number;
  minVerticalSeparationCycleIndex: number | null;
  eventStartCycle: number;
  eventEndCycle: number;
  trajectory1: AircraftPosition[];
  trajectory2: AircraftPosition[];
  comments: EventComment[];
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export type UpdateStatusRequest =
  | { status: 'DISMISSED' }
  | { status: 'ESCALATED'; escalationReason: string };

export interface UpdateStatusResponse {
  id: string;
  status: string;
  escalationReason: string | null;
  escalatedAt: string | null;
  dismissedAt: string | null;
}
