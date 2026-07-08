import type { MigrationStatus } from '../../api/migration-types';

/** Map a migration status onto a Spectrum badge variant. */
export function statusVariant(status: MigrationStatus | string): 'positive' | 'negative' | 'informative' | 'neutral' {
  switch (status) {
    case 'SUCCESS':
      return 'positive';
    case 'FAILED':
      return 'negative';
    case 'RUNNING':
      return 'informative';
    default:
      return 'neutral';
  }
}
