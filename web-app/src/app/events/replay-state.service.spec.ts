import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { ReplayStateService } from './replay-state.service';

describe('ReplayStateService', () => {
  let service: ReplayStateService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({});
    service = TestBed.runInInjectionContext(() => new ReplayStateService());
  });

  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  describe('initial state', () => {
    it('should initialise currentIndex to 0 and isPlaying to false', () => {
      expect(service.currentIndex()).toBe(0);
      expect(service.isPlaying()).toBe(false);
    });
  });

  describe('play()', () => {
    it('should set isPlaying to true when play() is called', () => {
      service.play(10);
      expect(service.isPlaying()).toBe(true);
    });

    it('should increment currentIndex by 1 after each 5000ms interval', () => {
      service.play(10);
      vi.advanceTimersByTime(5000);
      expect(service.currentIndex()).toBe(1);

      vi.advanceTimersByTime(5000);
      expect(service.currentIndex()).toBe(2);
    });

    it('should stop playback when currentIndex reaches maxIndex', () => {
      service.play(2);

      vi.advanceTimersByTime(5000);
      expect(service.currentIndex()).toBe(1);

      vi.advanceTimersByTime(5000);
      expect(service.currentIndex()).toBe(2);
      expect(service.isPlaying()).toBe(false);
    });

    it('should clamp currentIndex at maxIndex without going beyond', () => {
      service.play(1);
      vi.advanceTimersByTime(10000);
      expect(service.currentIndex()).toBe(1);
    });

    it('should not start a second timer if play() is called while already playing', () => {
      service.play(10);
      service.play(10);
      vi.advanceTimersByTime(5000);
      // If two intervals were created, currentIndex would be 2
      expect(service.currentIndex()).toBe(1);
    });
  });

  describe('pause()', () => {
    it('should set isPlaying to false when pause() is called', () => {
      service.play(10);
      service.pause();
      expect(service.isPlaying()).toBe(false);
    });

    it('should stop incrementing currentIndex after pause()', () => {
      service.play(10);
      vi.advanceTimersByTime(5000);
      service.pause();
      vi.advanceTimersByTime(10000);
      expect(service.currentIndex()).toBe(1);
    });
  });

  describe('rewind()', () => {
    it('should reset currentIndex to 0 and stop playback when rewind() is called', () => {
      service.play(10);
      vi.advanceTimersByTime(5000);
      expect(service.currentIndex()).toBe(1);

      service.rewind();

      expect(service.currentIndex()).toBe(0);
      expect(service.isPlaying()).toBe(false);
    });

    it('should not increment after rewind even if time passes', () => {
      service.play(10);
      service.rewind();
      vi.advanceTimersByTime(10000);
      expect(service.currentIndex()).toBe(0);
    });
  });

  describe('seek()', () => {
    it('should update currentIndex and stop playback when seek() is called', () => {
      service.play(10);
      service.seek(5);

      expect(service.currentIndex()).toBe(5);
      expect(service.isPlaying()).toBe(false);
    });

    it('should not increment after seek() even if time passes', () => {
      service.seek(3);
      vi.advanceTimersByTime(10000);
      expect(service.currentIndex()).toBe(3);
    });
  });
});
