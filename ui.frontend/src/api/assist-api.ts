import { getJson, postForm } from './client';
import type { AssistContextResponse, ClassDictionaryResponse, ClassMembersResponse, CompileResponse } from './types';

export function getClasses(prefix: string, limit = 1000): Promise<ClassDictionaryResponse> {
  return getJson<ClassDictionaryResponse>('/bin/groovyconsole/assist/classes', {
    prefix,
    limit: String(limit),
  });
}

export function getMembers(fqcn: string): Promise<ClassMembersResponse> {
  return getJson<ClassMembersResponse>('/bin/groovyconsole/assist/members', { class: fqcn });
}

export function getContext(): Promise<AssistContextResponse> {
  return getJson<AssistContextResponse>('/bin/groovyconsole/assist/context');
}

export function compileScript(script: string): Promise<CompileResponse> {
  return postForm<CompileResponse>('/bin/groovyconsole/assist/compile', { script });
}
