import { del, getJson } from './client';
import type { AuditRecord } from './types';

export async function getAuditRecords(startDate?: string, endDate?: string): Promise<AuditRecord[]> {
  const params: Record<string, string> = {};
  if (startDate && endDate) {
    params.startDate = startDate;
    params.endDate = endDate;
  }
  const response = await getJson<{ data: AuditRecord[] }>('/bin/groovyconsole/audit.json', params);
  return response.data ?? [];
}

export function deleteAuditRecord(userId: string, script: string): Promise<void> {
  return del('/bin/groovyconsole/audit.json', { userId, script });
}

export function deleteAllAuditRecords(): Promise<void> {
  return del('/bin/groovyconsole/audit.json');
}
