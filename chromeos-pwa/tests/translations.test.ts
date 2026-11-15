import test from 'node:test';
import assert from 'node:assert/strict';
import { TRANSLATIONS, resolveLanguage } from '../src/i18n/translations.ts';
import type { SupportedLang } from '../src/domain/types.ts';

const SUPPORTED_LANGS: SupportedLang[] = ['en', 'de', 'fr', 'it', 'es'];

test('TRANSLATIONS maintains 100% key parity and non-empty strings/arrays/formatters across en, de, fr, it, and es', () => {
  const baselineKeys = Object.keys(TRANSLATIONS.en).sort();
  assert.ok(baselineKeys.length > 100, 'Expected comprehensive translation dictionary');

  for (const lang of SUPPORTED_LANGS) {
    const dict = TRANSLATIONS[lang];
    assert.ok(dict, `Missing translation dictionary for ${lang}`);

    const keys = Object.keys(dict).sort();
    assert.deepEqual(keys, baselineKeys, `Key mismatch in ${lang} compared to en`);

    for (const key of baselineKeys) {
      const enVal = (TRANSLATIONS.en as unknown as Record<string, unknown>)[key];
      const localVal = (dict as unknown as Record<string, unknown>)[key];
      assert.equal(
        typeof localVal,
        typeof enVal,
        `Type mismatch for key "${key}" in "${lang}"`
      );
      assert.equal(
        Array.isArray(localVal),
        Array.isArray(enVal),
        `Array mismatch for key "${key}" in "${lang}"`
      );

      if (typeof localVal === 'string') {
        assert.ok(
          localVal.trim().length > 0,
          `Empty translation string for key "${key}" in "${lang}"`
        );
      } else if (Array.isArray(localVal)) {
        const enArr = enVal as unknown[];
        assert.equal(
          localVal.length,
          enArr.length,
          `Array length mismatch for "${key}" in "${lang}"`
        );
        for (const item of localVal) {
          assert.equal(typeof item, 'string');
          assert.ok(item.trim().length > 0, `Empty array item in "${key}" (${lang})`);
        }
      } else if (typeof localVal === 'function') {
        const fn = localVal as (arg: unknown) => string;
        const sampleArg =
          key.toLowerCase().includes('version') ||
          key.toLowerCase().includes('uptodate') ||
          key.toLowerCase().includes('undo') ||
          key.toLowerCase().includes('title')
            ? 'Test'
            : 3;
        const rendered = fn(sampleArg);
        assert.equal(
          typeof rendered,
          'string',
          `Function key "${key}" in "${lang}" did not return a string`
        );
        assert.ok(
          rendered.trim().length > 0,
          `Function key "${key}" in "${lang}" returned empty output`
        );
      } else {
        assert.fail(`Unexpected translation value type for "${key}" in "${lang}"`);
      }
    }
  }
});

test('project deletion prompt localization keys format title & count accurately across en, de, fr, it, and es', () => {
  for (const lang of SUPPORTED_LANGS) {
    const dict = TRANSLATIONS[lang];
    const titleOut = dict.projectDeletePromptTitle('Q4 Launch');
    const bodyOut = dict.projectDeletePromptTasksBody(7);
    assert.ok(titleOut.includes('Q4 Launch'), `Missing title interpolation in ${lang}`);
    assert.ok(bodyOut.includes('7'), `Missing count interpolation in ${lang}`);
    assert.ok(dict.projectDeleteWithTasksBtn.trim().length > 0, `Empty with-tasks btn in ${lang}`);
    assert.ok(dict.projectDeleteKeepTasksBtn.trim().length > 0, `Empty keep-tasks btn in ${lang}`);
    assert.notEqual(
      dict.projectDeleteWithTasksBtn,
      dict.projectDeleteKeepTasksBtn,
      `Buttons must be distinct in ${lang}`
    );
  }
});

test('resolveLanguage resolves explicit locales accurately', () => {
  for (const lang of SUPPORTED_LANGS) {
    assert.equal(resolveLanguage(lang), lang);
  }
  const sys = resolveLanguage('system');
  assert.ok(SUPPORTED_LANGS.includes(sys));
});
