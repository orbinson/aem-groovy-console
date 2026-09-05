/**
 * Small cron helper for the schedule editor: builds Quartz-style expressions from a few friendly presets,
 * parses an existing expression back into those presets, describes one in plain language, and validates it
 * with the same rules the server enforces (see the backend CronValidator).
 *
 * Quartz form used by the Sling scheduler: 6 or 7 fields
 * `seconds minutes hours day-of-month month day-of-week [year]`.
 */

export type CronMode = 'daily' | 'weekday' | 'weekly' | 'monthly' | 'custom';

export interface CronParts {
  mode: CronMode;
  /** 0-23 */
  hour: number;
  /** 0-59 */
  minute: number;
  /** day-of-week token for weekly, e.g. MON */
  weekday: string;
  /** 1-31 for monthly */
  dayOfMonth: number;
}

export const WEEKDAYS: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'MON', label: 'Monday' },
  { value: 'TUE', label: 'Tuesday' },
  { value: 'WED', label: 'Wednesday' },
  { value: 'THU', label: 'Thursday' },
  { value: 'FRI', label: 'Friday' },
  { value: 'SAT', label: 'Saturday' },
  { value: 'SUN', label: 'Sunday' },
];

export const DEFAULT_CRON_PARTS: CronParts = {
  mode: 'daily',
  hour: 6,
  minute: 0,
  weekday: 'MON',
  dayOfMonth: 1,
};

const pad = (value: number): string => String(value).padStart(2, '0');

const weekdayLabel = (token: string): string =>
  WEEKDAYS.find((day) => day.value === token.toUpperCase())?.label ?? token;

/** Build a cron expression from the preset parts. Returns '' for custom (the raw field is authoritative there). */
export function buildCron(parts: CronParts): string {
  const { hour, minute, weekday, dayOfMonth } = parts;

  switch (parts.mode) {
    case 'daily':
      return `0 ${minute} ${hour} * * ?`;
    case 'weekday':
      return `0 ${minute} ${hour} ? * MON-FRI`;
    case 'weekly':
      return `0 ${minute} ${hour} ? * ${weekday}`;
    case 'monthly':
      return `0 ${minute} ${hour} ${dayOfMonth} * ?`;
    default:
      return '';
  }
}

/** Best-effort parse of an expression into preset parts; falls back to custom mode when it isn't a known preset. */
export function parseCron(expression: string | null | undefined): CronParts {
  const custom = (): CronParts => ({ ...DEFAULT_CRON_PARTS, mode: 'custom' });
  const expr = (expression ?? '').trim();

  if (!expr) {
    return { ...DEFAULT_CRON_PARTS };
  }

  const fields = expr.split(/\s+/);

  if (fields.length < 6 || fields.length > 7) {
    return custom();
  }

  const [seconds, minuteField, hourField, dom, month, dow] = fields;
  const isInt = (value: string): boolean => /^\d{1,2}$/.test(value);

  // presets only cover a fixed second/minute/hour on any month
  if (seconds !== '0' || month !== '*' || !isInt(minuteField) || !isInt(hourField)) {
    return custom();
  }

  const minute = Number(minuteField);
  const hour = Number(hourField);

  if (minute > 59 || hour > 23) {
    return custom();
  }

  const base = { ...DEFAULT_CRON_PARTS, hour, minute };

  if (dom === '*' && dow === '?') {
    return { ...base, mode: 'daily' };
  }
  if (dom === '?' && dow.toUpperCase() === 'MON-FRI') {
    return { ...base, mode: 'weekday' };
  }
  if (dom === '?' && WEEKDAYS.some((day) => day.value === dow.toUpperCase())) {
    return { ...base, mode: 'weekly', weekday: dow.toUpperCase() };
  }
  if (dow === '?' && isInt(dom) && Number(dom) >= 1 && Number(dom) <= 31) {
    return { ...base, mode: 'monthly', dayOfMonth: Number(dom) };
  }

  return custom();
}

/** Plain-language description of a preset expression (empty string when it can't be described concisely). */
export function describeCron(expression: string): string {
  const parts = parseCron(expression);
  const at = `${pad(parts.hour)}:${pad(parts.minute)}`;

  switch (parts.mode) {
    case 'daily':
      return `Runs every day at ${at}.`;
    case 'weekday':
      return `Runs every weekday (Mon–Fri) at ${at}.`;
    case 'weekly':
      return `Runs every ${weekdayLabel(parts.weekday)} at ${at}.`;
    case 'monthly':
      return `Runs on day ${parts.dayOfMonth} of every month at ${at}.`;
    default:
      return '';
  }
}

/**
 * Validate an expression with the same rules as the server, so the editor can flag problems before saving.
 * Returns an error message, or null when the expression is acceptable.
 */
export function validateCron(expression: string | null | undefined): string | null {
  const expr = (expression ?? '').trim();

  if (!expr) {
    return 'A cron expression is required for an enabled schedule.';
  }

  const fields = expr.split(/\s+/);

  if (fields.length < 6 || fields.length > 7) {
    return 'Expected 6 or 7 fields (seconds minutes hours day-of-month month day-of-week [year]).';
  }

  if (!/^\d{1,2}$/.test(fields[0]) || Number(fields[0]) > 59) {
    return 'The seconds field must be a fixed value between 0 and 59; sub-minute schedules are not allowed.';
  }

  return null;
}
