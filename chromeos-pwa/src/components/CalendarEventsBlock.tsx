import React from 'react';
import type { CalendarEventItem } from '../domain/types';
import {
  formatCalendarEventTimeRange,
  openCalendarEventInGoogleCalendar
} from '../data/calendarService';
import type { TranslationStrings } from '../i18n/translations';

interface CalendarEventsBlockProps {
  events: CalendarEventItem[];
  t: TranslationStrings;
  compactTop?: boolean;
}

export const CalendarEventsBlock: React.FC<CalendarEventsBlockProps> = ({
  events,
  t,
  compactTop = false
}) => {
  if (events.length === 0) return null;

  return (
    <div
      className={`calendar-events-block ${compactTop ? 'compact-top' : ''}`}
      onClick={(e) => e.stopPropagation()}
    >
      {events.map((event) => {
        const displayTitle = event.title.trim() || t.calendarUntitledEvent;
        const timeLabel = event.isAllDay
          ? t.calendarAllDay
          : formatCalendarEventTimeRange(event.startMillis, event.endMillis);

        return (
          <button
            key={`cal_ev_${event.calendarId}_${event.eventId}_${event.startMillis}`}
            type="button"
            className="calendar-event-row"
            onClick={() => openCalendarEventInGoogleCalendar(event)}
          >
            <span
              className="calendar-event-bar"
              style={{ backgroundColor: event.calendarColorCss }}
            />

            {event.isAllDay ? (
              <span
                className="calendar-event-allday-pill"
                style={{
                  backgroundColor: `${event.calendarColorCss}24`,
                  color: event.calendarColorCss
                }}
              >
                {timeLabel}
              </span>
            ) : (
              <span className="calendar-event-time">{timeLabel}</span>
            )}

            <span className="calendar-event-title">{displayTitle}</span>

            {event.calendarName.trim().length > 0 && (
              <span className="calendar-event-calname">{event.calendarName}</span>
            )}
          </button>
        );
      })}
    </div>
  );
};
