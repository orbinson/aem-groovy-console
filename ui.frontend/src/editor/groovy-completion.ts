import type * as Monaco from 'monaco-editor';
import { getClasses, getMembers } from '../api/assist-api';
import type { ClassMember } from '../api/types';
import { METHODS } from '../data/reference-content';
import { CONSOLE_BUILTINS, GROOVY_LANGUAGE_ID } from './groovy-language';
import { getAssistContext, getServicesMap } from './assist-data';

/** Documentation for the console's builtin methods, keyed by method name (from the reference content). */
export const BUILTIN_DOCS = new Map<string, { signature: string; description: string }>();

for (const method of METHODS) {
  const match = method.signature.match(/^(\w+)\s*\(/);
  if (match && !BUILTIN_DOCS.has(match[1])) {
    BUILTIN_DOCS.set(match[1], method);
  }
}

const SNIPPETS: Array<{ label: string; insertText: string; documentation: string }> = [
  {
    label: 'each',
    insertText: 'each { ${1:it} ->\n\t$0\n}',
    documentation: 'Iterate over a collection',
  },
  {
    label: 'getService',
    insertText: 'def ${1:service} = getService("${2:com.example.Service}")',
    documentation: 'Get an OSGi service instance',
  },
  {
    label: 'sql2Query',
    insertText: 'sql2Query("SELECT * FROM [${1:nt:base}] WHERE ISDESCENDANTNODE([${2:/content}])")',
    documentation: 'Execute an SQL-2 query',
  },
  {
    label: 'recurse',
    insertText: 'getNode("${1:/content}").recurse { node ->\n\t$0\n}',
    documentation: 'Recurse a node tree',
  },
];

function memberToSuggestion(
  monaco: typeof Monaco,
  member: ClassMember,
  range: Monaco.IRange,
): Monaco.languages.CompletionItem {
  if (member.kind === 'method') {
    const params = (member.params ?? []).map((param) => param.split('.').pop()).join(', ');
    return {
      label: `${member.name}(${params})`,
      kind: monaco.languages.CompletionItemKind.Method,
      insertText: member.name + ((member.params ?? []).length ? '(' : '()'),
      detail: `${member.returnType?.split('.').pop() ?? ''}${member.source === 'groovy' ? ' (Groovy)' : ''}`,
      sortText: (member.source === 'groovy' ? '2' : '1') + member.name,
      range,
    };
  }

  return {
    label: member.name,
    kind:
      member.kind === 'property'
        ? monaco.languages.CompletionItemKind.Property
        : monaco.languages.CompletionItemKind.Field,
    insertText: member.name,
    detail: member.type?.split('.').pop() ?? '',
    sortText: '0' + member.name,
    range,
  };
}

/** Resolve the type of the expression immediately before a trailing dot, if determinable. */
async function resolveReceiverType(textBeforeDot: string): Promise<string | null> {
  // direct binding reference, e.g. "resourceResolver."
  const identifierMatch = textBeforeDot.match(/([A-Za-z_$][\w$]*)\s*$/);

  if (identifierMatch) {
    const identifier = identifierMatch[1];
    const context = await getAssistContext().catch(() => null);
    const binding = context?.bindings.find((candidate) => candidate.name === identifier);

    if (binding?.type) {
      return binding.type;
    }

    // capitalized simple class name, e.g. "Calendar." — try star imports and java.lang
    if (/^[A-Z]/.test(identifier)) {
      const packages = [...(context?.starImports.map((starImport) => starImport.packageName) ?? []), 'java.lang'];

      for (const packageName of packages) {
        const result = await getMembers(`${packageName}.${identifier}`).catch(() => null);
        if (result && !result.error && result.members.length) {
          return result.fqcn;
        }
      }
    }
  }

  // fully qualified class name, e.g. "javax.jcr.Session."
  const fqcnMatch = textBeforeDot.match(/([a-z][\w$]*(?:\.[\w$]+)+)\s*$/);

  return fqcnMatch ? fqcnMatch[1] : null;
}

export function registerGroovyCompletionProvider(monaco: typeof Monaco): void {
  monaco.languages.registerCompletionItemProvider(GROOVY_LANGUAGE_ID, {
    triggerCharacters: ['.', '"', "'"],

    async provideCompletionItems(model, position) {
      const lineContent = model.getValueInRange({
        startLineNumber: position.lineNumber,
        startColumn: 1,
        endLineNumber: position.lineNumber,
        endColumn: position.column,
      });

      const word = model.getWordUntilPosition(position);
      const range: Monaco.IRange = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      };

      // OSGi service name completion inside getService("...") / getServices("...")
      const serviceMatch = lineContent.match(/getServices?\s*\(\s*["']([\w.]*)$/);

      if (serviceMatch) {
        const services = await getServicesMap().catch(() => ({}) as Record<string, string>);
        const prefix = serviceMatch[1].toLowerCase();

        return {
          // incomplete: Monaco re-queries on each keystroke instead of only filtering the first result
          incomplete: true,
          suggestions: Object.keys(services)
            .filter((name) => name.toLowerCase().includes(prefix))
            .slice(0, 100)
            .map((name) => ({
              label: name,
              kind: monaco.languages.CompletionItemKind.Interface,
              insertText: name,
              filterText: name.split('.').pop(),
              range: {
                ...range,
                startColumn: position.column - serviceMatch[1].length,
                endColumn: position.column,
              },
            })),
        };
      }

      // member completion after a dot
      const dotMatch = lineContent.match(/^(.*?)\.\s*[\w$]*$/);

      if (dotMatch && lineContent.trimEnd().length > 1) {
        const receiverType = await resolveReceiverType(dotMatch[1]);

        if (receiverType) {
          const result = await getMembers(receiverType).catch(() => null);

          if (result && !result.error) {
            return {
              suggestions: result.members.map((member) => memberToSuggestion(monaco, member, range)),
            };
          }
        }

        return { suggestions: [] };
      }

      // identifier completion: bindings, builtins, snippets, classes
      const suggestions: Monaco.languages.CompletionItem[] = [];
      const context = await getAssistContext().catch(() => null);

      context?.bindings.forEach((binding) => {
        suggestions.push({
          label: binding.name,
          kind: monaco.languages.CompletionItemKind.Variable,
          insertText: binding.name,
          detail: binding.type?.split('.').pop() ?? '',
          documentation: binding.type,
          sortText: '0' + binding.name,
          range,
        });
      });

      CONSOLE_BUILTINS.forEach((builtin) => {
        const doc = BUILTIN_DOCS.get(builtin);

        suggestions.push({
          label: builtin,
          kind: monaco.languages.CompletionItemKind.Function,
          insertText: builtin,
          detail: doc?.signature ?? 'Groovy Console',
          documentation: doc ? { value: `**${doc.signature}**\n\n${doc.description}` } : 'Groovy Console',
          sortText: '1' + builtin,
          range,
        });
      });

      SNIPPETS.forEach((snippet) => {
        suggestions.push({
          label: snippet.label,
          kind: monaco.languages.CompletionItemKind.Snippet,
          insertText: snippet.insertText,
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          documentation: snippet.documentation,
          sortText: '3' + snippet.label,
          range,
        });
      });

      // class index lookup once the prefix is meaningful
      if (word.word.length >= 2) {
        const classIndex = await getClasses(word.word, 200).catch(() => null);

        classIndex?.classes.forEach((entry) => {
          suggestions.push({
            label: { label: entry.name, description: entry.package },
            kind: monaco.languages.CompletionItemKind.Class,
            insertText: entry.name,
            detail: entry.package,
            sortText: (entry.exported ? '4' : '5') + entry.name,
            range,
            // auto-import: qualify usage by adding an import statement when needed
            additionalTextEdits: buildAutoImportEdit(model, entry.fqcn, entry.package, context?.starImports ?? []),
          });
        });
      }

      // incomplete: the class index is prefix-queried server-side, so Monaco must
      // re-invoke this provider as the user keeps typing instead of filtering stale results
      return { suggestions, incomplete: true };
    },
  });
}

function buildAutoImportEdit(
  model: Monaco.editor.ITextModel,
  fqcn: string,
  packageName: string,
  starImports: Array<{ packageName: string }>,
): Monaco.languages.TextEdit[] | undefined {
  if (!packageName || packageName === 'java.lang') {
    return undefined;
  }

  if (starImports.some((starImport) => starImport.packageName === packageName)) {
    return undefined;
  }

  const text = model.getValue();

  if (new RegExp(`import\\s+${fqcn.replace(/\./g, '\\.')}\\s*$`, 'm').test(text) ||
      new RegExp(`import\\s+${packageName.replace(/\./g, '\\.')}\\.\\*`, 'm').test(text)) {
    return undefined;
  }

  // insert after the last existing import, or at the top of the script
  const lines = text.split('\n');
  let insertLine = 1;

  for (let index = 0; index < lines.length; index++) {
    if (/^\s*import\s/.test(lines[index])) {
      insertLine = index + 2;
    }
  }

  return [
    {
      range: { startLineNumber: insertLine, startColumn: 1, endLineNumber: insertLine, endColumn: 1 },
      text: `import ${fqcn}\n`,
    },
  ];
}
