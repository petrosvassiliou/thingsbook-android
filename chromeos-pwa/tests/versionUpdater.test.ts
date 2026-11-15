import test from 'node:test';
import assert from 'node:assert/strict';
import { APP_BUILD_CODE, APP_VERSION } from '../src/domain/types.ts';
import {
  compareSemver as compareFromUtils,
  isEligibleReleasePayload as isEligibleFromUtils
} from '../src/data/versionUtils.ts';
import {
  compareSemver,
  isEligibleReleasePayload
} from '../src/data/pwaUpdater.ts';

test('v1.8.1 build metadata constants are bumped to 1.8.1 (Build 44)', () => {
  assert.equal(APP_VERSION, '1.8.1');
  assert.equal(APP_BUILD_CODE, 44);
  assert.equal(compareSemver, compareFromUtils);
  assert.equal(isEligibleReleasePayload, isEligibleFromUtils);
});

test('compareSemver orders major/minor/patch, pre-release, and +build metadata accurately', () => {
  const ordered = [
    '1.7.9',
    '1.8.0-alpha.1',
    '1.8.0-beta.1',
    '1.8.0-rc.1',
    '1.8.0',
    '1.8.0+1',
    '1.8.0+2',
    '1.8.1-beta.1',
    '1.8.1',
    '1.8.1+1',
    '1.8.2-beta.1',
    '1.8.2',
    '2.0.0-beta.1',
    '2.0.0'
  ];

  for (let i = 0; i < ordered.length; i++) {
    assert.equal(compareSemver(ordered[i], ordered[i]), 0, `${ordered[i]} == ${ordered[i]}`);
    for (let j = i + 1; j < ordered.length; j++) {
      assert.equal(
        compareSemver(ordered[i], ordered[j]),
        -1,
        `Expected ${ordered[i]} < ${ordered[j]}`
      );
      assert.equal(
        compareSemver(ordered[j], ordered[i]),
        1,
        `Expected ${ordered[j]} > ${ordered[i]}`
      );
    }
  }

  // Explicitly verify prompt edge cases
  assert.equal(compareSemver('1.8.0+1', '1.8.0'), 1);
  assert.equal(compareSemver('1.8.1', '1.8.0+1'), 1);
  assert.equal(compareSemver('1.8.1+1', '1.8.1'), 1);
  assert.equal(compareSemver('v1.8.1', '1.8.1'), 0);
});

test('isEligibleReleasePayload strictly isolates stable 1.8.1 from 2.0-beta/rc/alpha, drafts, prereleases, and non-v tags', () => {
  // Reject 2.0 pre-release tags even if GitHub API marks prerelease: false
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'v2.0.0-beta.1', draft: false, prerelease: false },
      '1.8.1'
    ),
    false
  );
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'v2.0.0-alpha.1', draft: false, prerelease: false },
      '1.8.1'
    ),
    false
  );
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'v2.0.0-rc.1', draft: false, prerelease: false },
      '1.8.1'
    ),
    false
  );
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'V2.0.0-beta.2+45', draft: false, prerelease: false },
      '1.8.1+44'
    ),
    false
  );

  // Reject macOS / non-v tags
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'macos-build-12', draft: false, prerelease: false },
      '1.8.1'
    ),
    false
  );
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'release-1.8.2', draft: false, prerelease: false },
      '1.8.1'
    ),
    false
  );

  // Reject drafts and GitHub-flagged prereleases
  assert.equal(
    isEligibleReleasePayload({ tag_name: 'v1.8.2', draft: true, prerelease: false }, '1.8.1'),
    false
  );
  assert.equal(
    isEligibleReleasePayload({ tag_name: 'v1.8.2', draft: false, prerelease: true }, '1.8.1'),
    false
  );
  assert.equal(isEligibleReleasePayload(null, '1.8.1'), false);
  assert.equal(isEligibleReleasePayload({}, '1.8.1'), false);

  // Accept genuine stable releases
  assert.equal(
    isEligibleReleasePayload({ tag_name: 'v1.8.2', draft: false, prerelease: false }, '1.8.1'),
    true
  );
  assert.equal(
    isEligibleReleasePayload({ tag_name: 'V2.0.0', draft: false, prerelease: false }, '1.8.1'),
    true
  );
  assert.equal(
    isEligibleReleasePayload({ tag_name: 'v1.8.1+1', draft: false, prerelease: false }, '1.8.1'),
    true
  );
});
