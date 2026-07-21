// Bundled open fonts matching the Spectrum look (Adobe Clean is proprietary); the token
// font stacks in styles/tokens.css list these families first.
import '@fontsource/source-sans-3/400.css';
import '@fontsource/source-sans-3/600.css';
import '@fontsource/source-sans-3/700.css';
import '@fontsource/source-code-pro/400.css';
import '@fontsource/source-code-pro/600.css';

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
import '@spectrum-web-components/menu/sp-menu-divider.js';
import '@spectrum-web-components/switch/sp-switch.js';
import '@spectrum-web-components/toast/sp-toast.js';
import '@spectrum-web-components/table/elements.js';
import '@spectrum-web-components/dialog/sp-dialog-wrapper.js';
import '@spectrum-web-components/textfield/sp-textfield.js';
import '@spectrum-web-components/field-label/sp-field-label.js';
import '@spectrum-web-components/checkbox/sp-checkbox.js';
import '@spectrum-web-components/picker/sp-picker.js';
import '@spectrum-web-components/help-text/sp-help-text.js';
import '@spectrum-web-components/badge/sp-badge.js';
import '@spectrum-web-components/action-menu/sp-action-menu.js';
import '@spectrum-web-components/icons-workflow/icons/sp-icon-history.js';
import '@spectrum-web-components/icons-workflow/icons/sp-icon-clock.js';
import '@spectrum-web-components/icons-workflow/icons/sp-icon-help.js';
import '@spectrum-web-components/icons-workflow/icons/sp-icon-close.js';
import '@spectrum-web-components/icons-workflow/icons/sp-icon-refresh.js';
import '@spectrum-web-components/icons-workflow/icons/sp-icon-folder.js';
import '@spectrum-web-components/icons-workflow/icons/sp-icon-file-code.js';
import '@spectrum-web-components/icons-workflow/icons/sp-icon-chevron-right.js';

// App styles
import './styles/app.css';

// Components
import './components/gc-app';
import './components/gc-aem-nav';
import './components/gc-app-bar';
import './components/gc-status-bar';
import './components/gc-drawer';
import './components/gc-script-editor';
import './components/gc-data-editor';
import './components/gc-result';
import './components/gc-history';
import './components/gc-scheduler';
import './components/gc-scheduled-jobs';
import './components/gc-active-jobs';
import './components/gc-reference';
import './components/gc-script-browser-dialog';
import './components/gc-save-dialog';
