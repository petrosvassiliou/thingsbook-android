import test from 'node:test';
import assert from 'node:assert/strict';
import { APP_BUILD_CODE, APP_VERSION } from '../src/domain/types.ts';
import {
  APP_BUILD_CODE as buildFromUtils,
  APP_VERSION as versionFromUtils,
  compareSemver as compareFromUtils,
  isEligibleReleasePayload as isEligibleFromUtils,
  isPrereleaseVersion,
  selectLatestEligibleRelease as selectFromUtils
} from '../src/data/versionUtils.ts';
import {
  compareSemver,
  isEligibleReleasePayload,
  selectLatestEligibleRelease
} from '../src/data/pwaUpdater.ts';

test('v2.0.0-beta.8 build metadata constants are bumped to 2.0.0-beta.8 (Build 52)', () => {
  assert.equal(APP_VERSION, '2.0.0-beta.8');
  assert.equal(APP_BUILD_CODE, 52);
  assert.equal(versionFromUtils, '2.0.0-beta.8');
  assert.equal(buildFromUtils, 52);
  assert.equal(isPrereleaseVersion(APP_VERSION), true);
  assert.equal(isPrereleaseVersion('1.8.1'), false);
  assert.equal(isPrereleaseVersion('2.0.0'), false);
  assert.equal(compareSemver, compareFromUtils);
  assert.equal(isEligibleReleasePayload, isEligibleFromUtils);
  assert.equal(selectLatestEligibleRelease, selectFromUtils);
});

test('compareSemver orders numeric pre-release identifiers (beta.1 < beta.2 < beta.3 < beta.10 < 2.0.0) and +build metadata accurately', () => {
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
    '2.0.0-alpha.1',
    '2.0.0-beta.1',
    '2.0.0-beta.2',
    '2.0.0-beta.3',
    '2.0.0-beta.10',
    '2.0.0-beta.99',
    '2.0.0-rc.1',
    '2.0.0-rc.2',
    '2.0.0',
    '2.0.0+1'
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

  // Explicit pre-release numeric & upgrade checks
  assert.equal(compareSemver('2.0.0-beta.2', '2.0.0-beta.1'), 1);
  assert.equal(compareSemver('2.0.0-beta.10', '2.0.0-beta.2'), 1);
  assert.equal(compareSemver('2.0.0', '2.0.0-beta.99'), 1);
  assert.equal(compareSemver('2.0.0-beta.1', '1.8.1'), 1);
  assert.equal(compareSemver('v2.0.0-beta.1', '2.0.0-beta.1'), 0);
});

test('isEligibleReleasePayload with includeBeta=false strictly isolates stable 1.8.1 from 2.0-beta/rc/alpha, drafts, and non-v tags', () => {
  // Stable 1.8.1 without opt-in (default includeBeta === false)
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'v2.0.0-beta.1', draft: false, prerelease: true },
      '1.8.1',
      false
    ),
    false
  );
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

  // Reject macOS / non-v tags & drafts
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'macos-build-12', draft: false, prerelease: false },
      '1.8.1'
    ),
    false
  );
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

  // Accept genuine stable releases (including 2.0.0 final!)
  assert.equal(
    isEligibleReleasePayload({ tag_name: 'v1.8.2', draft: false, prerelease: false }, '1.8.1'),
    true
  );
  assert.equal(
    isEligibleReleasePayload({ tag_name: 'V2.0.0', draft: false, prerelease: false }, '1.8.1'),
    true
  );
});

test('isEligibleReleasePayload and selectLatestEligibleRelease support includeBeta=true for 2.0 beta channel & opt-in upgrades', () => {
  // Running 2.0.0-beta.1 defaults includeBeta to true automatically
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'v2.0.0-beta.2', draft: false, prerelease: true },
      '2.0.0-beta.1'
    ),
    true
  );
  // Stable 1.8.1 user opting into "Include 2.0 Beta Updates" (includeBeta = true)
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'v2.0.0-beta.1', draft: false, prerelease: true },
      '1.8.1',
      true
    ),
    true
  );
  // Still strictly rejects drafts and macos-build-* even when includeBeta === true!
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'v2.0.0-beta.3', draft: true, prerelease: true },
      '2.0.0-beta.1',
      true
    ),
    false
  );
  assert.equal(
    isEligibleReleasePayload(
      { tag_name: 'macos-build-45', draft: false, prerelease: true },
      '2.0.0-beta.1',
      true
    ),
    false
  );

  const releasesFeed = [
    { tag_name: 'v2.0.0-beta.11', draft: true, prerelease: true },
    { tag_name: 'macos-build-99', draft: false, prerelease: true },
    { tag_name: 'v2.0.0-beta.2', draft: false, prerelease: true },
    { tag_name: 'v2.0.0-beta.10', draft: false, prerelease: true },
    { tag_name: 'v1.8.1', draft: false, prerelease: false }
  ];

  // Stable 1.8.1 without beta opt-in picks v1.8.1 (ignores beta.2 & beta.10)
  const pickedStable = selectLatestEligibleRelease(releasesFeed, '1.8.1', false);
  assert.equal(pickedStable?.tag_name, 'v1.8.1');

  // Stable 1.8.1 WITH beta opt-in picks v2.0.0-beta.10 (numeric 10 > 2, skips draft beta.11)
  const pickedOptIn = selectLatestEligibleRelease(releasesFeed, '1.8.1', true);
  assert.equal(pickedOptIn?.tag_name, 'v2.0.0-beta.10');

  // Once final v2.0.0 ships, both beta and stable channels upgrade to v2.0.0!
  const feedWithFinal = [
    { tag_name: 'v2.0.0', draft: false, prerelease: false },
    ...releasesFeed
  ];
  assert.equal(selectLatestEligibleRelease(feedWithFinal, '2.0.0-beta.10', true)?.tag_name, 'v2.0.0');
  assert.equal(selectLatestEligibleRelease(feedWithFinal, '1.8.1', false)?.tag_name, 'v2.0.0');
});
