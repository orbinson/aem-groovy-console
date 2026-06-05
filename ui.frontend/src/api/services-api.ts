import { getJson } from './client';
import type { ServicesMap } from './types';

export function getServices(): Promise<ServicesMap> {
  return getJson<ServicesMap>('/bin/groovyconsole/services');
}
