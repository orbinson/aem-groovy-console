// Migration history UI entry point.
// Spectrum Web Components — per-component imports only (bundle size)
import '@spectrum-web-components/theme/sp-theme.js';
import '@spectrum-web-components/theme/theme-light.js';
import '@spectrum-web-components/theme/theme-dark.js';
import '@spectrum-web-components/theme/scale-medium.js';
import '@spectrum-web-components/button/sp-button.js';
import '@spectrum-web-components/action-button/sp-action-button.js';
import '@spectrum-web-components/progress-circle/sp-progress-circle.js';
import '@spectrum-web-components/switch/sp-switch.js';
import '@spectrum-web-components/toast/sp-toast.js';
import '@spectrum-web-components/badge/sp-badge.js';

// App styles
import './styles/migration.css';

// Components
import './components/migration/gcm-app';
import './components/migration/gcm-run-list';
import './components/migration/gcm-run-detail';
