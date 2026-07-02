import { getJson, getText, postForm } from './client';
import type { RunScriptResponse } from './types';

export interface StreamPoll {
  chunk: string;
  offset: number;
  done: boolean;
  response?: RunScriptResponse;
}

/**
 * Start an asynchronous (streaming) execution; output is fetched via pollExecution.
 * Older backends without streaming support answer synchronously with the full response instead.
 */
export function runScriptAsync(
  script: string,
  data: string,
): Promise<RunScriptResponse & { executionId?: string }> {
  return postForm<RunScriptResponse & { executionId?: string }>('/bin/groovyconsole/post.json', {
    script,
    data,
    async: 'true',
  });
}

export function pollExecution(executionId: string, offset: number): Promise<StreamPoll> {
  return getJson<StreamPoll>('/bin/groovyconsole/stream.json', {
    executionId,
    offset: String(offset),
  });
}

export function distributeScript(script: string, data: string): Promise<RunScriptResponse> {
  return postForm<RunScriptResponse>('/bin/groovyconsole/replicate.json', { script, data });
}

export function saveScript(fileName: string, script: string): Promise<unknown> {
  return postForm('/bin/groovyconsole/save', { fileName, script });
}

/** Load a saved script's source, exactly like the classic UI does. */
export function loadScript(scriptPath: string): Promise<string> {
  return getText(`${scriptPath}/jcr:content/jcr:data`);
}
