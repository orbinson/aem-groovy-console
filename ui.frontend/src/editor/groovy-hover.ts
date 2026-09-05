import type * as Monaco from 'monaco-editor';
import { BUILTIN_DOCS } from './groovy-completion';
import { GROOVY_LANGUAGE_ID } from './groovy-language';
import { getAssistContext } from './assist-data';

export function registerGroovyHoverProvider(monaco: typeof Monaco): void {
  monaco.languages.registerHoverProvider(GROOVY_LANGUAGE_ID, {
    async provideHover(model, position) {
      const word = model.getWordAtPosition(position);

      if (!word) {
        return null;
      }

      const range = new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn);

      // console builtin methods (getNode, getService, sql2Query, ...)
      const builtin = BUILTIN_DOCS.get(word.word);

      if (builtin) {
        return {
          range,
          contents: [{ value: `**${builtin.signature}**` }, { value: builtin.description }],
        };
      }

      // script bindings (resourceResolver, session, ...)
      const context = await getAssistContext().catch(() => null);
      const binding = context?.bindings.find((candidate) => candidate.name === word.word);

      if (!binding) {
        return null;
      }

      const contents: Monaco.IMarkdownString[] = [
        { value: `**${binding.name}**: \`${binding.type}\`` },
      ];

      if (binding.link) {
        contents.push({ value: `[Documentation](${binding.link})` });
      }

      return { range, contents };
    },
  });
}
