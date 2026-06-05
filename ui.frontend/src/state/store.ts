import type { ReactiveController, ReactiveControllerHost } from 'lit';
import type { RunScriptResponse } from '../api/types';
import { persistence } from './local-storage';

export interface AppState {
  colorScheme: 'light' | 'dark';
  running: boolean;
  result: RunScriptResponse | null;
  /** Accumulated live output while a streaming execution is running; null when not streaming. */
  liveOutput: string | null;
  scriptName: string;
  dirty: boolean;
  toast: { message: string; variant: 'positive' | 'negative' } | null;
}

type Listener = (state: AppState) => void;

class Store {
  private state: AppState = {
    colorScheme: persistence.getColorScheme(),
    running: false,
    result: null,
    liveOutput: null,
    scriptName: persistence.getScriptName(),
    dirty: false,
    toast: null,
  };

  private listeners = new Set<Listener>();

  getState(): AppState {
    return this.state;
  }

  setState(partial: Partial<AppState>): void {
    this.state = { ...this.state, ...partial };
    this.listeners.forEach((listener) => listener(this.state));
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  showToast(message: string, variant: 'positive' | 'negative' = 'positive'): void {
    this.setState({ toast: { message, variant } });
  }
}

export const store = new Store();

/** Re-renders the host Lit element whenever the store changes. */
export class StoreController implements ReactiveController {
  private unsubscribe?: () => void;

  constructor(private host: ReactiveControllerHost) {
    host.addController(this);
  }

  get state(): AppState {
    return store.getState();
  }

  hostConnected(): void {
    this.unsubscribe = store.subscribe(() => this.host.requestUpdate());
  }

  hostDisconnected(): void {
    this.unsubscribe?.();
  }
}
