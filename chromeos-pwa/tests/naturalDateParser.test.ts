import test from 'node:test';
import assert from 'node:assert/strict';
import { parseNaturalDate } from '../src/domain/naturalDateParser.ts';
import { resolveWhenPreset, type SupportedLang } from '../src/domain/types.ts';

// Wednesday, 2026-11-18 10:30 local time
const REF_DATE = new Date(2026, 10, 18, 10, 30, 0, 0);
const REF_MIDNIGHT = new Date(2026, 10, 18, 0, 0, 0, 0).getTime();

function localDayEpoch(year: number, monthIndex: number, day: number): number {
  return new Date(year, monthIndex, day, 0, 0, 0, 0).getTime();
}

function localTimeEpoch(
  year: number,
  monthIndex: number,
  day: number,
  hour: number,
  minute: number
): number {
  return new Date(year, monthIndex, day, hour, minute, 0, 0).getTime();
}

test('parseNaturalDate parses Today, This Evening, Tomorrow, Someday, and Anytime across all 5 languages', () => {
  const cases: Array<{
    lang: SupportedLang;
    today: string;
    evening: string;
    tomorrow: string;
    overmorrow: string;
    someday: string;
    anytime: string;
  }> = [
    {
      lang: 'en',
      today: 'Today',
      evening: 'This evening',
      tomorrow: 'Tomorrow',
      overmorrow: 'Overmorrow',
      someday: 'Someday',
      anytime: 'Anytime'
    },
    {
      lang: 'de',
      today: 'Heute',
      evening: 'Heute Abend',
      tomorrow: 'Morgen',
      overmorrow: 'Übermorgen',
      someday: 'Irgendwann',
      anytime: 'Jederzeit'
    },
    {
      lang: 'fr',
      today: "Aujourd'hui",
      evening: 'Ce soir',
      tomorrow: 'Demain',
      overmorrow: 'Après-demain',
      someday: 'Un jour',
      anytime: 'À tout moment'
    },
    {
      lang: 'it',
      today: 'Oggi',
      evening: 'Stasera',
      tomorrow: 'Domani',
      overmorrow: 'Dopodomani',
      someday: 'Prima o poi',
      anytime: 'In qualsiasi momento'
    },
    {
      lang: 'es',
      today: 'Hoy',
      evening: 'Esta noche',
      tomorrow: 'Mañana',
      overmorrow: 'Pasado mañana',
      someday: 'Algún día',
      anytime: 'En cualquier momento'
    }
  ];

  for (const c of cases) {
    const tod = parseNaturalDate(c.today, c.lang, REF_DATE);
    assert.ok(tod, `today failed for ${c.lang}`);
    assert.equal(tod.scheduleState, 'TODAY');
    assert.equal(tod.whenDateEpochMillis, REF_MIDNIGHT);
    assert.equal(tod.reminderTimeEpochMillis, null);

    const eve = parseNaturalDate(c.evening, c.lang, REF_DATE);
    assert.ok(eve, `evening failed for ${c.lang}`);
    assert.equal(eve.scheduleState, 'THIS_EVENING');
    assert.equal(eve.whenDateEpochMillis, REF_MIDNIGHT);

    const tmr = parseNaturalDate(c.tomorrow, c.lang, REF_DATE);
    assert.ok(tmr, `tomorrow failed for ${c.lang}`);
    assert.equal(tmr.scheduleState, 'UPCOMING');
    assert.equal(tmr.whenDateEpochMillis, localDayEpoch(2026, 10, 19));

    const ovm = parseNaturalDate(c.overmorrow, c.lang, REF_DATE);
    assert.ok(ovm, `overmorrow failed for ${c.lang}`);
    assert.equal(ovm.scheduleState, 'UPCOMING');
    assert.equal(ovm.whenDateEpochMillis, localDayEpoch(2026, 10, 20));

    const som = parseNaturalDate(c.someday, c.lang, REF_DATE);
    assert.ok(som, `someday failed for ${c.lang}`);
    assert.equal(som.scheduleState, 'SOMEDAY');
    assert.equal(som.whenDateEpochMillis, null);

    const any = parseNaturalDate(c.anytime, c.lang, REF_DATE);
    assert.ok(any, `anytime failed for ${c.lang}`);
    assert.equal(any.scheduleState, 'ANYTIME');
    assert.equal(any.whenDateEpochMillis, null);
  }
});

test('parseNaturalDate parses relative days, weeks, weekdays, ISO, and Euro dates across EN/DE/FR/IT/ES', () => {
  const relDaysCases: Array<[SupportedLang, string, number]> = [
    ['en', 'in 5 days', 5],
    ['de', 'in 5 Tagen', 5],
    ['fr', 'dans 5 jours', 5],
    ['it', 'tra 5 giorni', 5],
    ['es', 'en 5 días', 5]
  ];
  for (const [lang, expr, offset] of relDaysCases) {
    const res = parseNaturalDate(expr, lang, REF_DATE);
    assert.ok(res, `${expr} (${lang})`);
    assert.equal(res.scheduleState, 'UPCOMING');
    assert.equal(res.whenDateEpochMillis, localDayEpoch(2026, 10, 18 + offset));
  }

  const relWeeksCases: Array<[SupportedLang, string, number]> = [
    ['en', 'in 2 weeks', 14],
    ['de', 'in 2 Wochen', 14],
    ['fr', 'dans 2 semaines', 14],
    ['it', 'fra 2 settimane', 14],
    ['es', 'en 2 semanas', 14]
  ];
  for (const [lang, expr, offset] of relWeeksCases) {
    const res = parseNaturalDate(expr, lang, REF_DATE);
    assert.ok(res, `${expr} (${lang})`);
    assert.equal(res.scheduleState, 'UPCOMING');
    assert.equal(res.whenDateEpochMillis, localDayEpoch(2026, 10, 18 + offset));
  }

  // REF_DATE is Wednesday (ISO 3) 2026-11-18 -> Friday is +2 days (Nov 20), Wednesday is +7 days (Nov 25)
  const weekdayCases: Array<[SupportedLang, string, number]> = [
    ['en', 'next Friday', localDayEpoch(2026, 10, 20)],
    ['de', 'nächsten Freitag', localDayEpoch(2026, 10, 20)],
    ['fr', 'vendredi prochain', localDayEpoch(2026, 10, 20)],
    ['it', 'prossimo venerdì', localDayEpoch(2026, 10, 20)],
    ['es', 'próximo miércoles', localDayEpoch(2026, 10, 25)]
  ];
  for (const [lang, expr, expectedEpoch] of weekdayCases) {
    const res = parseNaturalDate(expr, lang, REF_DATE);
    assert.ok(res, `${expr} (${lang})`);
    assert.equal(res.scheduleState, 'UPCOMING');
    assert.equal(res.whenDateEpochMillis, expectedEpoch);
  }

  const iso = parseNaturalDate('2026-12-24', 'en', REF_DATE);
  assert.ok(iso);
  assert.equal(iso.scheduleState, 'UPCOMING');
  assert.equal(iso.whenDateEpochMillis, localDayEpoch(2026, 11, 24));

  const euro = parseNaturalDate('24.12.2026', 'de', REF_DATE);
  assert.ok(euro);
  assert.equal(euro.whenDateEpochMillis, localDayEpoch(2026, 11, 24));
});

test('parseNaturalDate and resolveWhenPreset parse multilingual reminder times across EN/DE/FR/IT/ES', () => {
  const reminderCases: Array<{
    lang: SupportedLang;
    input: string;
    expectedState: string;
    expectedDay: number;
    expectedReminder: number;
    expectedClock: string;
  }> = [
    {
      lang: 'en',
      input: 'tomorrow at 9:30',
      expectedState: 'UPCOMING',
      expectedDay: localDayEpoch(2026, 10, 19),
      expectedReminder: localTimeEpoch(2026, 10, 19, 9, 30),
      expectedClock: '09:30'
    },
    {
      lang: 'en',
      input: 'today 2:15 pm',
      expectedState: 'TODAY',
      expectedDay: localDayEpoch(2026, 10, 18),
      expectedReminder: localTimeEpoch(2026, 10, 18, 14, 15),
      expectedClock: '14:15'
    },
    {
      lang: 'de',
      input: 'morgen um 14:00',
      expectedState: 'UPCOMING',
      expectedDay: localDayEpoch(2026, 10, 19),
      expectedReminder: localTimeEpoch(2026, 10, 19, 14, 0),
      expectedClock: '14:00'
    },
    {
      lang: 'de',
      input: 'heute um 9 Uhr',
      expectedState: 'TODAY',
      expectedDay: localDayEpoch(2026, 10, 18),
      expectedReminder: localTimeEpoch(2026, 10, 18, 9, 0),
      expectedClock: '09:00'
    },
    {
      lang: 'fr',
      input: 'demain à 18h30',
      expectedState: 'UPCOMING',
      expectedDay: localDayEpoch(2026, 10, 19),
      expectedReminder: localTimeEpoch(2026, 10, 19, 18, 30),
      expectedClock: '18:30'
    },
    {
      lang: 'it',
      input: 'domani alle 08:15',
      expectedState: 'UPCOMING',
      expectedDay: localDayEpoch(2026, 10, 19),
      expectedReminder: localTimeEpoch(2026, 10, 19, 8, 15),
      expectedClock: '08:15'
    },
    {
      lang: 'es',
      input: 'mañana a las 15:00',
      expectedState: 'UPCOMING',
      expectedDay: localDayEpoch(2026, 10, 19),
      expectedReminder: localTimeEpoch(2026, 10, 19, 15, 0),
      expectedClock: '15:00'
    }
  ];

  for (const rc of reminderCases) {
    const parsed = parseNaturalDate(rc.input, rc.lang, REF_DATE);
    assert.ok(parsed, `Failed to parse "${rc.input}" (${rc.lang})`);
    assert.equal(parsed.scheduleState, rc.expectedState);
    assert.equal(parsed.whenDateEpochMillis, rc.expectedDay);
    assert.equal(parsed.reminderTimeEpochMillis, rc.expectedReminder);
    assert.equal(parsed.reminderClock, rc.expectedClock);
  }

  const presetToday = resolveWhenPreset('TODAY', '18:45', REF_DATE);
  assert.equal(presetToday.scheduleState, 'TODAY');
  assert.equal(presetToday.whenDate, REF_MIDNIGHT);
  assert.equal(presetToday.reminderTime, localTimeEpoch(2026, 10, 18, 18, 45));
});
