import { beforeEach, describe, expect, it, vi } from 'vitest';

// the resolver heuristics call the assist API / context — mock both so the tests are pure and offline
vi.mock('../api/assist-api', () => ({
  getClasses: vi.fn(),
  getMembers: vi.fn(),
}));
vi.mock('./assist-data', () => ({
  getAssistContext: vi.fn(),
  getServicesMap: vi.fn(),
}));

import { getMembers } from '../api/assist-api';
import { getAssistContext } from './assist-data';
import { isDeclarationName, isInsideString, resolveExplicitImport, resolveReceiverType } from './groovy-completion';

const getMembersMock = vi.mocked(getMembers);
const getAssistContextMock = vi.mocked(getAssistContext);

const RESOLVER_TYPE = 'org.apache.sling.api.resource.ResourceResolver';
const RESOURCE_TYPE = 'org.apache.sling.api.resource.Resource';

beforeEach(() => {
  vi.resetAllMocks();
  getAssistContextMock.mockResolvedValue({
    bindings: [{ name: 'resourceResolver', type: RESOLVER_TYPE }],
    starImports: [],
  } as never);
  getMembersMock.mockResolvedValue({ fqcn: '', error: 'no members', members: [] } as never);
});

describe('resolveExplicitImport', () => {
  it('resolves a simple name to the FQCN of an explicit import', () => {
    const script = 'import a.b.ReportColumnType\n\ndef data = report.data()';
    expect(resolveExplicitImport(script, 'ReportColumnType')).toBe('a.b.ReportColumnType');
  });

  it('handles static imports', () => {
    expect(resolveExplicitImport('import static a.b.C.FOO\n', 'FOO')).toBe('a.b.C.FOO');
  });

  it('returns null when there is no matching import', () => {
    expect(resolveExplicitImport('def x = 1', 'ReportColumnType')).toBeNull();
  });
});

describe('isInsideString', () => {
  it('is true between an opening quote and the cursor', () => {
    expect(isInsideString('def s = "hello wor')).toBe(true);
    expect(isInsideString("def s = 'hello wor")).toBe(true);
  });

  it('is false once the string is closed', () => {
    expect(isInsideString('def s = "hello"')).toBe(false);
    expect(isInsideString('def s = "a" + b')).toBe(false);
  });

  it('is false in plain code', () => {
    expect(isInsideString('def node = resourceReso')).toBe(false);
  });

  it('ignores escaped quotes', () => {
    expect(isInsideString('def s = "a \\" b')).toBe(true);
  });

  it('treats the other quote char as literal inside a string', () => {
    expect(isInsideString('def s = "it\'s ')).toBe(true);
  });
});

describe('isDeclarationName', () => {
  it('is true while naming a def/var', () => {
    expect(isDeclarationName('def wh')).toBe(true);
    expect(isDeclarationName('  var foo')).toBe(true);
    expect(isDeclarationName('final def bar')).toBe(true);
  });

  it('is false once an initializer is being typed', () => {
    expect(isDeclarationName('def x = wh')).toBe(false);
  });

  it('is false for ordinary identifier positions', () => {
    expect(isDeclarationName('return resourceReso')).toBe(false);
    expect(isDeclarationName('new Reso')).toBe(false);
  });
});

describe('resolveReceiverType', () => {
  it('resolves a script binding to its type', async () => {
    expect(await resolveReceiverType('resourceResolver', '')).toBe(RESOLVER_TYPE);
  });

  it('resolves a capitalized name via its explicit import', async () => {
    const script = `import ${RESOURCE_TYPE}\nResource.`;
    expect(await resolveReceiverType('Resource', script)).toBe(RESOURCE_TYPE);
  });

  it('resolves a typed local variable declaration through the imported type', async () => {
    const script = `import ${RESOURCE_TYPE}\nResource resource = resourceResolver.getResource(params.path)`;
    expect(await resolveReceiverType('resource', script)).toBe(RESOURCE_TYPE);
  });

  it('infers a def variable from the initializer method return type', async () => {
    getMembersMock.mockResolvedValue({
      fqcn: RESOLVER_TYPE,
      members: [{ kind: 'method', name: 'getResource', returnType: RESOURCE_TYPE }],
    } as never);

    const script = 'def res = resourceResolver.getResource(params.path)';
    expect(await resolveReceiverType('res', script)).toBe(RESOURCE_TYPE);
  });

  it('picks the nearest declaration when a name is re-declared (last match wins)', async () => {
    getMembersMock.mockResolvedValue({
      fqcn: RESOLVER_TYPE,
      members: [{ kind: 'method', name: 'getResource', returnType: RESOURCE_TYPE }],
    } as never);

    // an earlier unresolvable def then a later resolvable one; text-up-to-cursor means the last is nearest
    const script = ['def resource = something', 'def resource = resourceResolver.getResource(params.path)'].join('\n');
    expect(await resolveReceiverType('resource', script)).toBe(RESOURCE_TYPE);
  });

  it('returns null past the recursion depth guard', async () => {
    expect(await resolveReceiverType('resourceResolver', '', 4)).toBeNull();
  });

  it('returns null for an unresolvable receiver', async () => {
    expect(await resolveReceiverType('mysteryVariable', 'def something = 1')).toBeNull();
  });
});
