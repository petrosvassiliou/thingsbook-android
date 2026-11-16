/**
 * Deterministic cross-platform tooltip timing & viewport placement engine
 * for Thingsbook ChromeOS / Web PWA (parity with macOS TooltipTimingCoordinator
 * and Android TooltipTimingPolicy).
 *
 * - Cold hover delay: 160ms
 * - Warm handoff window: 450ms -> 0ms instant transition between controls
 * - Live state/language text update while hovered: 0ms
 * - Stationary touch long-press preview: 380ms (10px slop)
 */

export const DEFAULT_INITIAL_DELAY_MS = 160;
export const DEFAULT_WARM_HANDOFF_WINDOW_MS = 450;
export const DEFAULT_TOUCH_LONG_PRESS_MS = 380;
export const DEFAULT_TOUCH_SLOP_PX = 10;
export const DEFAULT_TOUCH_RELEASE_HIDE_MS = 1800;
export const DEFAULT_TOUCH_MAX_VISIBLE_MS = 4000;

export interface TooltipTimingConfig {
  initialDelayMs?: number;
  warmHandoffWindowMs?: number;
}

export interface TooltipEnterTransition {
  delayMs: number;
  shouldShowImmediately: boolean;
  activeText: string;
}

export interface TooltipTextUpdateResult {
  changed: boolean;
  isVisible: boolean;
  activeText: string;
}

export class TooltipTimingMachine {
  readonly initialDelayMs: number;
  readonly warmHandoffWindowMs: number;

  private currentAnchorId: string | null = null;
  private currentText = '';
  private visible = false;
  private lastDismissedAtMs: number | null = null;

  constructor(config: TooltipTimingConfig = {}) {
    this.initialDelayMs = config.initialDelayMs ?? DEFAULT_INITIAL_DELAY_MS;
    this.warmHandoffWindowMs =
      config.warmHandoffWindowMs ?? DEFAULT_WARM_HANDOFF_WINDOW_MS;
  }

  get activeAnchorId(): string | null {
    return this.currentAnchorId;
  }

  get activeText(): string {
    return this.currentText;
  }

  get isVisible(): boolean {
    return this.visible;
  }

  pointerEnter(
    anchorId: string,
    rawText: string,
    nowMs: number
  ): TooltipEnterTransition {
    const trimmed = rawText.trim();
    if (!trimmed) {
      if (this.currentAnchorId === anchorId) {
        this.pointerLeave(anchorId, nowMs);
      }
      return {
        delayMs: 0,
        shouldShowImmediately: false,
        activeText: ''
      };
    }

    const withinWarmWindow =
      this.visible ||
      (this.lastDismissedAtMs !== null &&
        nowMs - this.lastDismissedAtMs >= 0 &&
        nowMs - this.lastDismissedAtMs <= this.warmHandoffWindowMs);

    this.currentAnchorId = anchorId;
    this.currentText = trimmed;

    if (withinWarmWindow) {
      this.visible = true;
      this.lastDismissedAtMs = null;
      return {
        delayMs: 0,
        shouldShowImmediately: true,
        activeText: trimmed
      };
    }

    this.visible = false;
    return {
      delayMs: this.initialDelayMs,
      shouldShowImmediately: false,
      activeText: trimmed
    };
  }

  commitPendingShow(anchorId: string): boolean {
    if (this.currentAnchorId !== anchorId || !this.currentText) {
      return false;
    }
    this.visible = true;
    this.lastDismissedAtMs = null;
    return true;
  }

  updateActiveText(
    anchorId: string,
    nextRawText: string,
    nowMs: number
  ): TooltipTextUpdateResult {
    if (this.currentAnchorId !== anchorId) {
      return {
        changed: false,
        isVisible: this.visible,
        activeText: this.currentText
      };
    }

    const trimmed = nextRawText.trim();
    if (!trimmed) {
      this.pointerLeave(anchorId, nowMs);
      return {
        changed: true,
        isVisible: false,
        activeText: ''
      };
    }

    const changed = this.currentText !== trimmed;
    this.currentText = trimmed;
    return {
      changed,
      isVisible: this.visible,
      activeText: trimmed
    };
  }

  pointerLeave(anchorId: string, nowMs: number): boolean {
    if (this.currentAnchorId !== anchorId) {
      return false;
    }
    if (this.visible) {
      this.lastDismissedAtMs = nowMs;
    }
    this.currentAnchorId = null;
    this.currentText = '';
    this.visible = false;
    return true;
  }

  dismissAll(nowMs?: number, preserveWarmWindow = false): void {
    if (preserveWarmWindow && this.visible && nowMs !== undefined) {
      this.lastDismissedAtMs = nowMs;
    } else {
      this.lastDismissedAtMs = null;
    }
    this.currentAnchorId = null;
    this.currentText = '';
    this.visible = false;
  }
}

export interface AnchorRectLike {
  left: number;
  top: number;
  width: number;
  height: number;
}

export interface TooltipPositionResult {
  left: number;
  top: number;
  placement: 'above' | 'below';
}

export function computeTooltipPosition(
  anchor: AnchorRectLike,
  tooltipWidth: number,
  tooltipHeight: number,
  viewportWidth: number,
  viewportHeight: number,
  gapPx = 8,
  marginPx = 8
): TooltipPositionResult {
  const safeMaxLeft = Math.max(marginPx, viewportWidth - tooltipWidth - marginPx);
  const safeMaxTop = Math.max(marginPx, viewportHeight - tooltipHeight - marginPx);

  const rawLeft = anchor.left + anchor.width / 2 - tooltipWidth / 2;
  const left = Math.round(Math.min(Math.max(rawLeft, marginPx), safeMaxLeft));

  const aboveTop = anchor.top - tooltipHeight - gapPx;
  if (aboveTop >= marginPx) {
    return {
      left,
      top: Math.round(Math.min(aboveTop, safeMaxTop)),
      placement: 'above'
    };
  }

  const belowTop = anchor.top + anchor.height + gapPx;
  return {
    left,
    top: Math.round(Math.min(Math.max(belowTop, marginPx), safeMaxTop)),
    placement: 'below'
  };
}

export interface TopBarIconEligibilityInput {
  isInsideTopBar: boolean;
  tagName: string;
  isIconButtonClass: boolean;
  isResponsiveTopBarControl: boolean;
  hasVisibleTextLabel: boolean;
}

/**
 * Restricts floating tooltips strictly to icon buttons inside `.workspace-topbar`.
 * Labeled pills in the top bar (when their text label is visible) and all controls
 * outside `.workspace-topbar` never show tooltips.
 */
export function isTopBarIconTooltipAllowed(
  input: TopBarIconEligibilityInput
): boolean {
  if (!input.isInsideTopBar) return false;
  if (input.tagName.toUpperCase() !== 'BUTTON') return false;
  if (input.isIconButtonClass) return true;
  if (input.isResponsiveTopBarControl && !input.hasVisibleTextLabel) {
    return true;
  }
  return false;
}
