import { getClasses, getContext } from '../api/assist-api';
import { getServices } from '../api/services-api';
import type { AssistContextResponse, ServicesMap } from '../api/types';

// Shared, lazily-fetched assist data used by the completion/hover providers and reference panels.

let contextPromise: Promise<AssistContextResponse> | null = null;
let servicesPromise: Promise<ServicesMap> | null = null;

export function getAssistContext(): Promise<AssistContextResponse> {
  if (!contextPromise) {
    contextPromise = getContext().catch((error) => {
      contextPromise = null;
      throw error;
    });
  }
  return contextPromise;
}

export function getServicesMap(): Promise<ServicesMap> {
  if (!servicesPromise) {
    servicesPromise = getServices().catch((error) => {
      servicesPromise = null;
      throw error;
    });
  }
  return servicesPromise;
}

/**
 * Fire-and-forget warm-up on app load: fetches the script context and forces the server to
 * build its class dictionary so the first completion request is instant.
 */
export function prefetchAssistData(): void {
  void getAssistContext().catch(() => undefined);
  void getClasses('Object', 1).catch(() => undefined);
}
