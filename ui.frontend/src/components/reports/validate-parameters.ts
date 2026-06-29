import type { ReportParameterType } from '../../api/reports-types';

/** Minimal shape both ReportParameter and the editor's ParameterRow satisfy. */
interface ValidatableParameter {
  name: string;
  label: string;
  type: ReportParameterType;
  required: boolean;
  defaultValue?: string | null;
}

/**
 * Client-side check mirroring the server's required-parameter validation: a required parameter must have
 * a non-empty submitted value (a non-empty default counts, since the server falls back to it). BOOLEAN
 * parameters always carry a value, so they are never "missing". Returns per-parameter error messages.
 */
export function validateRequired(
  parameters: ValidatableParameter[],
  values: Record<string, string>,
): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const parameter of parameters) {
    if (!parameter.required || parameter.type === 'BOOLEAN') {
      continue;
    }
    const provided = values[parameter.name];
    const effective = (provided ?? parameter.defaultValue ?? '').trim();
    if (!effective) {
      errors[parameter.name] = `${parameter.label || parameter.name} is required.`;
    }
  }
  return errors;
}
