import { del, getJson, postForm } from './client';
import type { ScheduledJob, ScheduleJobRequest } from './types';

export async function getScheduledJobs(): Promise<ScheduledJob[]> {
  const response = await getJson<{ data: ScheduledJob[] }>('/bin/groovyconsole/jobs.json');
  return response.data ?? [];
}

export function scheduleJob(request: ScheduleJobRequest): Promise<unknown> {
  return postForm('/bin/groovyconsole/jobs.json', { ...request });
}

export function deleteScheduledJob(scheduledJobId: string): Promise<void> {
  return del('/bin/groovyconsole/jobs.json', { scheduledJobId });
}
