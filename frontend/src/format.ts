/** The UI language; every date and time in the app is formatted with it. */
export const LOCALE = 'ru-RU';

/** `08:15` style time (24-hour clock, never `24:00`) in [timeZone]. */
export const timeFormatter = (timeZone: string) =>
  new Intl.DateTimeFormat(LOCALE, { timeZone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });

/** Date in [timeZone]: day and month name, plus the optional weekday (long or short) and year. */
export const dateFormatter = (timeZone: string, extra: Pick<Intl.DateTimeFormatOptions, 'weekday' | 'year'> = {}) =>
  new Intl.DateTimeFormat(LOCALE, { timeZone, day: 'numeric', month: 'long', ...extra });
