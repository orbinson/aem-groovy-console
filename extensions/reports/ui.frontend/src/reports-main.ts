// Business-facing reports UI entry point.
// Spectrum Web Components — per-component imports only (bundle size)
import '@spectrum-web-components/theme/sp-theme.js';
import '@spectrum-web-components/theme/theme-light.js';
import '@spectrum-web-components/theme/theme-dark.js';
import '@spectrum-web-components/theme/scale-medium.js';
import '@spectrum-web-components/button/sp-button.js';
import '@spectrum-web-components/action-button/sp-action-button.js';
import '@spectrum-web-components/progress-circle/sp-progress-circle.js';
import '@spectrum-web-components/search/sp-search.js';
import '@spectrum-web-components/menu/sp-menu.js';
import '@spectrum-web-components/menu/sp-menu-item.js';
import '@spectrum-web-components/switch/sp-switch.js';
import '@spectrum-web-components/toast/sp-toast.js';
import '@spectrum-web-components/textfield/sp-textfield.js';
import '@spectrum-web-components/field-label/sp-field-label.js';
import '@spectrum-web-components/checkbox/sp-checkbox.js';
import '@spectrum-web-components/picker/sp-picker.js';
import '@spectrum-web-components/help-text/sp-help-text.js';
import '@spectrum-web-components/badge/sp-badge.js';

// App styles
import './styles/reports.css';

// Components
// (gcr-code-editor is NOT imported here: the editor view lazy-loads it so business users browsing
// and running reports never download Monaco)
import './components/reports/gcr-app';
import './components/reports/gcr-report-list';
import './components/reports/gcr-report-run';
import './components/reports/gcr-report-editor';
import './components/reports/gcr-path-browser';
