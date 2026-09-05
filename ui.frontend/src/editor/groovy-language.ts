import type * as Monaco from 'monaco-editor';

export const GROOVY_LANGUAGE_ID = 'groovy';

/** Methods provided to all console scripts (see the classic UI's methods.html / builders.html). */
export const CONSOLE_BUILTINS = [
  'getPage',
  'getNode',
  'getResource',
  'getModel',
  'getService',
  'getServices',
  'copy',
  'move',
  'rename',
  'save',
  'activate',
  'deactivate',
  'delete',
  'distribute',
  'invalidate',
  'createQuery',
  'xpathQuery',
  'sql2Query',
  'nodeBuilder',
  'pageBuilder',
];

const configuration: Monaco.languages.LanguageConfiguration = {
  comments: {
    lineComment: '//',
    blockComment: ['/*', '*/'],
  },
  brackets: [
    ['{', '}'],
    ['[', ']'],
    ['(', ')'],
  ],
  autoClosingPairs: [
    { open: '{', close: '}' },
    { open: '[', close: ']' },
    { open: '(', close: ')' },
    { open: '"', close: '"' },
    { open: "'", close: "'" },
  ],
  surroundingPairs: [
    { open: '{', close: '}' },
    { open: '[', close: ']' },
    { open: '(', close: ')' },
    { open: '"', close: '"' },
    { open: "'", close: "'" },
  ],
};

// Monarch tokenizer adapted from Monaco's built-in Java language, extended with
// Groovy syntax: def/as/in/it/trait, triple-quoted and slashy strings, and
// GString ${} interpolation.
const monarch: Monaco.languages.IMonarchLanguage = {
  defaultToken: '',
  tokenPostfix: '.groovy',

  keywords: [
    'abstract', 'as', 'assert', 'boolean', 'break', 'byte', 'case', 'catch', 'char', 'class', 'const', 'continue',
    'def', 'default', 'do', 'double', 'else', 'enum', 'extends', 'final', 'finally', 'float', 'for', 'goto', 'if',
    'implements', 'import', 'in', 'instanceof', 'int', 'interface', 'long', 'native', 'new', 'package', 'private',
    'protected', 'public', 'return', 'short', 'static', 'strictfp', 'super', 'switch', 'synchronized', 'this',
    'threadsafe', 'throw', 'throws', 'trait', 'transient', 'try', 'var', 'void', 'volatile', 'while',
    'true', 'false', 'null', 'it',
  ],

  builtins: CONSOLE_BUILTINS,

  operators: [
    '=', '>', '<', '!', '~', '?', ':', '==', '<=', '>=', '!=', '&&', '||', '++', '--', '+', '-', '*', '/', '&', '|',
    '^', '%', '<<', '>>', '>>>', '+=', '-=', '*=', '/=', '&=', '|=', '^=', '%=', '<<=', '>>=', '>>>=', '?.', '?:',
    '*.', '.&', '.@', '=~', '==~', '<=>', '..', '..<', '**', '**=', '->',
  ],

  symbols: /[=><!~?:&|+\-*/^%]+/,
  escapes: /\\(?:[abfnrtv\\"'$]|u[0-9A-Fa-f]{4})/,
  digits: /\d+(_+\d+)*/,

  tokenizer: {
    root: [
      // identifiers and keywords
      [
        /[a-zA-Z_$][\w$]*/,
        {
          cases: {
            '@keywords': 'keyword',
            '@builtins': 'type.identifier',
            '@default': 'identifier',
          },
        },
      ],

      { include: '@whitespace' },

      // delimiters and operators
      [/[{}()[\]]/, '@brackets'],
      [/[<>](?!@symbols)/, '@brackets'],
      [
        /@symbols/,
        {
          cases: {
            '@operators': 'operator',
            '@default': '',
          },
        },
      ],

      // annotations
      [/@\s*[a-zA-Z_$][\w$]*/, 'annotation'],

      // numbers
      [/(@digits)[eE]([-+]?(@digits))?[fFdD]?/, 'number.float'],
      [/(@digits)\.(@digits)([eE][-+]?(@digits))?[fFdDgG]?/, 'number.float'],
      [/0[xX][0-9a-fA-F]+[lLiIgG]?/, 'number.hex'],
      [/(@digits)[lLiIgGfFdD]?/, 'number'],

      // delimiter: after numbers because of .\d floats
      [/[;,.]/, 'delimiter'],

      // strings
      [/"""/, 'string', '@tripleDoubleString'],
      [/'''/, 'string', '@tripleSingleString'],
      [/"([^"\\]|\\.)*$/, 'string.invalid'],
      [/"/, 'string', '@doubleString'],
      [/'([^'\\]|\\.)*$/, 'string.invalid'],
      [/'/, 'string', '@singleString'],
    ],

    whitespace: [
      [/[ \t\r\n]+/, ''],
      [/\/\*\*(?!\/)/, 'comment.doc', '@javadoc'],
      [/\/\*/, 'comment', '@comment'],
      [/\/\/.*$/, 'comment'],
      [/^#!.*$/, 'comment'],
    ],

    comment: [
      [/[^/*]+/, 'comment'],
      [/\*\//, 'comment', '@pop'],
      [/[/*]/, 'comment'],
    ],

    javadoc: [
      [/[^/*]+/, 'comment.doc'],
      [/\*\//, 'comment.doc', '@pop'],
      [/[/*]/, 'comment.doc'],
    ],

    doubleString: [
      [/[^\\"$]+/, 'string'],
      [/@escapes/, 'string.escape'],
      [/\\./, 'string.escape.invalid'],
      [/\$\{/, { token: 'delimiter.bracket', next: '@interpolation' }],
      [/\$[a-zA-Z_][\w]*/, 'variable'],
      [/"/, 'string', '@pop'],
    ],

    singleString: [
      [/[^\\']+/, 'string'],
      [/@escapes/, 'string.escape'],
      [/\\./, 'string.escape.invalid'],
      [/'/, 'string', '@pop'],
    ],

    tripleDoubleString: [
      [/[^\\"$]+/, 'string'],
      [/@escapes/, 'string.escape'],
      [/\$\{/, { token: 'delimiter.bracket', next: '@interpolation' }],
      [/\$[a-zA-Z_][\w]*/, 'variable'],
      [/"""/, 'string', '@pop'],
      [/"/, 'string'],
    ],

    tripleSingleString: [
      [/[^\\']+/, 'string'],
      [/@escapes/, 'string.escape'],
      [/'''/, 'string', '@pop'],
      [/'/, 'string'],
    ],

    interpolation: [
      [/\}/, { token: 'delimiter.bracket', next: '@pop' }],
      { include: 'root' },
    ],
  },
};

export function registerGroovyLanguage(monaco: typeof Monaco): void {
  monaco.languages.register({ id: GROOVY_LANGUAGE_ID, extensions: ['.groovy'], aliases: ['Groovy'] });
  monaco.languages.setLanguageConfiguration(GROOVY_LANGUAGE_ID, configuration);
  monaco.languages.setMonarchTokensProvider(GROOVY_LANGUAGE_ID, monarch);
}
