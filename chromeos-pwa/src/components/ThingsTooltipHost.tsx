import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  DEFAULT_TOUCH_LONG_PRESS_MS,
  DEFAULT_TOUCH_MAX_VISIBLE_MS,
  DEFAULT_TOUCH_RELEASE_HIDE_MS,
  DEFAULT_TOUCH_SLOP_PX,
  TooltipTimingMachine,
  computeTooltipPosition,
  isTimelineMouseTooltipAllowed,
  isTopBarIconTooltipAllowed,
  type AnchorRectLike
} from '../domain/tooltipTiming';

export interface TooltipPointerContext {
  pointerType: string;
  buttons: number;
}

interface VisibleTooltipState {
  anchorId: string;
  text: string;
  anchorRect: AnchorRectLike;
}

let nextAnchorSeq = 1;
const anchorIdsByElement = new WeakMap<HTMLElement, string>();

function getOrAssignAnchorId(el: HTMLElement): string {
  let id = anchorIdsByElement.get(el);
  if (!id) {
    id = `tb-tip-${nextAnchorSeq++}`;
    anchorIdsByElement.set(el, id);
  }
  return id;
}

function isTooltipEligibleElement(el: Element): el is HTMLElement {
  return el instanceof HTMLElement && el.tagName !== 'IFRAME';
}

function isDisabledControl(el: HTMLElement): boolean {
  if ((el as HTMLButtonElement).disabled === true) {
    return true;
  }
  return el.getAttribute('aria-disabled') === 'true';
}

function isInteractiveChildControl(el: HTMLElement): boolean {
  return (
    el.tagName === 'BUTTON' ||
    el.tagName === 'INPUT' ||
    el.tagName === 'SELECT' ||
    el.tagName === 'TEXTAREA' ||
    el.tagName === 'A'
  );
}

function isTimelineDraggingElement(el: HTMLElement): boolean {
  return (
    el.getAttribute('data-timeline-dragging') === 'true' ||
    el.closest('[data-timeline-dragging="true"]') !== null
  );
}

function getTimelineMouseTooltipText(
  el: HTMLElement,
  pointerContext?: TooltipPointerContext
): string {
  const raw = (el.getAttribute('data-timeline-mouse-tip') ?? '').trim();
  const allowed = isTimelineMouseTooltipAllowed({
    hasTimelineTipAttr: raw.length > 0,
    pointerType: pointerContext?.pointerType ?? '',
    buttons: pointerContext?.buttons ?? 0,
    isDraggingTimelineBar: isTimelineDraggingElement(el)
  });
  return allowed ? raw : '';
}

function hasVisibleResponsiveTextLabel(el: HTMLElement): boolean {
  const labelNodes = el.querySelectorAll<HTMLElement>(
    '.brand-name, .topbar-label-hide-md, .topbar-label-hide-sm, .topbar-label-hide-xs'
  );
  if (labelNodes.length === 0) {
    return (el.textContent ?? '').trim().length > 0;
  }
  for (const node of labelNodes) {
    if ((node.textContent ?? '').trim().length === 0) continue;
    const style = window.getComputedStyle(node);
    if (style.display !== 'none' && style.visibility !== 'hidden') {
      return true;
    }
  }
  return false;
}

function isTopBarIconButton(el: HTMLElement): boolean {
  const isInsideTopBar = el.closest('.workspace-topbar') !== null;
  if (!isInsideTopBar) return false;
  const isIconButtonClass = el.classList.contains('icon-btn');
  const isResponsiveTopBarControl =
    el.classList.contains('brand-title-group') ||
    el.classList.contains('topbar-pill');
  const hasVisibleTextLabel = isResponsiveTopBarControl
    ? hasVisibleResponsiveTextLabel(el)
    : false;
  return isTopBarIconTooltipAllowed({
    isInsideTopBar,
    tagName: el.tagName,
    isIconButtonClass,
    isResponsiveTopBarControl,
    hasVisibleTextLabel
  });
}

/**
 * Strips native HTML `title` attributes across the entire app so ChromeOS / browser
 * native black OS tooltips never appear anywhere, while preserving ARIA accessibility
 * and storing tooltip copy strictly for `.workspace-topbar` buttons.
 */
function syncElementTooltipAttribute(el: HTMLElement): string {
  const inTopBarButton =
    el.tagName === 'BUTTON' && el.closest('.workspace-topbar') !== null;

  if (el.hasAttribute('title')) {
    const rawTitle = el.getAttribute('title') ?? '';
    el.removeAttribute('title');
    const trimmedTitle = rawTitle.trim();
    if (trimmedTitle) {
      if (inTopBarButton) {
        el.setAttribute('data-topbar-tip', trimmedTitle);
      } else {
        el.removeAttribute('data-topbar-tip');
      }
      const hasExplicitAria =
        (el.hasAttribute('aria-label') && !el.hasAttribute('data-tooltip-auto-aria')) ||
        el.hasAttribute('aria-labelledby');
      const hasVisibleText = (el.textContent ?? '').trim().length > 0;
      if (!hasExplicitAria && !hasVisibleText) {
        el.setAttribute('aria-label', trimmedTitle);
        el.setAttribute('data-tooltip-auto-aria', '1');
      }
    } else {
      el.removeAttribute('data-topbar-tip');
      if (el.hasAttribute('data-tooltip-auto-aria')) {
        el.removeAttribute('aria-label');
        el.removeAttribute('data-tooltip-auto-aria');
      }
    }
  }

  if (el.hasAttribute('data-tooltip')) {
    const legacy = (el.getAttribute('data-tooltip') ?? '').trim();
    el.removeAttribute('data-tooltip');
    if (inTopBarButton && legacy && !el.hasAttribute('data-topbar-tip')) {
      el.setAttribute('data-topbar-tip', legacy);
    }
  }

  if (!isTopBarIconButton(el)) {
    return '';
  }
  return (el.getAttribute('data-topbar-tip') ?? '').trim();
}

function getElementTooltipText(
  el: HTMLElement,
  pointerContext?: TooltipPointerContext
): string {
  if (isTopBarIconButton(el)) {
    return syncElementTooltipAttribute(el);
  }
  if (el.hasAttribute('data-timeline-mouse-tip')) {
    return getTimelineMouseTooltipText(el, pointerContext);
  }
  return '';
}

function sanitizeSubtreeTitles(root: ParentNode): void {
  if (root instanceof HTMLElement && (root.hasAttribute('title') || root.hasAttribute('data-tooltip'))) {
    syncElementTooltipAttribute(root);
  }
  const titled = root.querySelectorAll<HTMLElement>('[title], [data-tooltip]');
  for (const node of titled) {
    syncElementTooltipAttribute(node);
  }
}

function upgradeNativeTitlesInLineage(target: EventTarget | null): void {
  let cur: Element | null =
    target instanceof Element
      ? target
      : target instanceof Node
        ? target.parentElement
        : null;
  while (cur) {
    if (isTooltipEligibleElement(cur) && (cur.hasAttribute('title') || cur.hasAttribute('data-tooltip'))) {
      syncElementTooltipAttribute(cur);
    }
    cur = cur.parentElement;
  }
}

// oxlint-disable-next-line react/only-export-components
export function resolveTooltipAnchor(
  target: EventTarget | null,
  pointerContext?: TooltipPointerContext
): { element: HTMLElement; text: string; rect: AnchorRectLike } | null {
  upgradeNativeTitlesInLineage(target);
  let cur: Element | null =
    target instanceof Element
      ? target
      : target instanceof Node
        ? target.parentElement
        : null;
  while (cur) {
    if (isTooltipEligibleElement(cur)) {
      if (!cur.isConnected || isDisabledControl(cur)) {
        return null;
      }
      if (isTopBarIconButton(cur)) {
        const text = syncElementTooltipAttribute(cur);
        if (text) {
          const domRect = cur.getBoundingClientRect();
          if (domRect.width > 0 && domRect.height > 0) {
            return {
              element: cur,
              text,
              rect: {
                left: domRect.left,
                top: domRect.top,
                width: domRect.width,
                height: domRect.height
              }
            };
          }
        }
        return null;
      }
      if (cur.hasAttribute('data-timeline-mouse-tip')) {
        const text = getTimelineMouseTooltipText(cur, pointerContext);
        if (text) {
          const domRect = cur.getBoundingClientRect();
          if (domRect.width > 0 && domRect.height > 0) {
            return {
              element: cur,
              text,
              rect: {
                left: domRect.left,
                top: domRect.top,
                width: domRect.width,
                height: domRect.height
              }
            };
          }
        }
        return null;
      }
      // Inner interactive buttons/inputs inside a timeline bar or card (e.g. -1d / +1d / +15m)
      // do not inherit their ancestor's task tooltip.
      if (isInteractiveChildControl(cur)) {
        return null;
      }
    }
    cur = cur.parentElement;
  }
  return null;
}

export const ThingsTooltipHost: React.FC = () => {
  const [visibleTooltip, setVisibleTooltip] = useState<VisibleTooltipState | null>(null);
  const [coords, setCoords] = useState<{ left: number; top: number } | null>(null);

  const tooltipRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (typeof document === 'undefined' || typeof window === 'undefined') {
      return;
    }

    const machine = new TooltipTimingMachine();
    let pendingTimerId: number | null = null;
    let touchPressTimerId: number | null = null;
    let touchAutoHideTimerId: number | null = null;
    let activeElement: HTMLElement | null = null;
    let activeAnchorId: string | null = null;
    let activeTouchPointerId: number | null = null;
    let touchTooltipShownForPointer = false;
    let suppressTouchClickUntilMs = 0;
    let touchStartX = 0;
    let touchStartY = 0;
    let lastHoverPointerContext: TooltipPointerContext = {
      pointerType: '',
      buttons: 0
    };
    let mutationObserver: MutationObserver | null = null;

    // Globally strip native HTML `title` attributes so browser OS black tooltips never appear anywhere
    sanitizeSubtreeTitles(document.body);
    const globalTitleSuppressor = new MutationObserver((records) => {
      for (const rec of records) {
        if (rec.type === 'attributes' && rec.target instanceof HTMLElement) {
          if (rec.target.hasAttribute('title') || rec.target.hasAttribute('data-tooltip')) {
            syncElementTooltipAttribute(rec.target);
          }
        } else if (rec.type === 'childList') {
          for (const added of rec.addedNodes) {
            if (added instanceof HTMLElement) {
              sanitizeSubtreeTitles(added);
            }
          }
        }
      }
    });
    globalTitleSuppressor.observe(document.body, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ['title', 'data-tooltip']
    });

    const clearPendingHoverTimer = () => {
      if (pendingTimerId !== null) {
        window.clearTimeout(pendingTimerId);
        pendingTimerId = null;
      }
    };

    const clearTouchTimers = () => {
      if (touchPressTimerId !== null) {
        window.clearTimeout(touchPressTimerId);
        touchPressTimerId = null;
      }
      if (touchAutoHideTimerId !== null) {
        window.clearTimeout(touchAutoHideTimerId);
        touchAutoHideTimerId = null;
      }
    };

    const disconnectObserver = () => {
      if (mutationObserver) {
        mutationObserver.disconnect();
        mutationObserver = null;
      }
    };

    const dismissImmediately = (preserveWarmWindow = false) => {
      clearPendingHoverTimer();
      clearTouchTimers();
      disconnectObserver();
      activeTouchPointerId = null;
      activeElement = null;
      activeAnchorId = null;
      machine.dismissAll(performance.now(), preserveWarmWindow);
      setVisibleTooltip(null);
      setCoords(null);
    };

    const observeActiveElement = (el: HTMLElement, anchorId: string) => {
      disconnectObserver();
      mutationObserver = new MutationObserver(() => {
        const isTopBar = isTopBarIconButton(el);
        const hasTimelineAttr = el.hasAttribute('data-timeline-mouse-tip');
        if (!el.isConnected || isDisabledControl(el) || (!isTopBar && !hasTimelineAttr)) {
          if (activeAnchorId === anchorId) {
            dismissImmediately(true);
          }
          return;
        }
        const nextText = isTopBar
          ? syncElementTooltipAttribute(el)
          : getTimelineMouseTooltipText(el, lastHoverPointerContext);
        if (!nextText && hasTimelineAttr && isTimelineDraggingElement(el)) {
          if (activeAnchorId === anchorId) {
            dismissImmediately(false);
          }
          return;
        }
        const res = machine.updateActiveText(anchorId, nextText, performance.now());
        if (!res.activeText) {
          if (activeAnchorId === anchorId) {
            clearPendingHoverTimer();
            disconnectObserver();
            activeElement = null;
            activeAnchorId = null;
            setVisibleTooltip(null);
            setCoords(null);
          }
          return;
        }
        if (res.isVisible) {
          const domRect = el.getBoundingClientRect();
          if (domRect.width > 0 && domRect.height > 0) {
            setVisibleTooltip({
              anchorId,
              text: res.activeText,
              anchorRect: {
                left: domRect.left,
                top: domRect.top,
                width: domRect.width,
                height: domRect.height
              }
            });
          } else {
            dismissImmediately(true);
          }
        }
      });
      mutationObserver.observe(el, {
        attributes: true,
        attributeFilter: [
          'title',
          'data-tooltip',
          'data-topbar-tip',
          'data-timeline-mouse-tip',
          'data-timeline-dragging',
          'disabled',
          'aria-disabled'
        ]
      });
    };

    const activateHoverTarget = (
      target: EventTarget | null,
      pointerContext: TooltipPointerContext
    ) => {
      if (pointerContext.buttons !== 0) {
        if (activeAnchorId !== null) {
          dismissImmediately(false);
        }
        return;
      }

      const resolved = resolveTooltipAnchor(target, pointerContext);
      const now = performance.now();

      if (!resolved) {
        if (activeAnchorId !== null) {
          clearPendingHoverTimer();
          disconnectObserver();
          machine.pointerLeave(activeAnchorId, now);
          activeElement = null;
          activeAnchorId = null;
          setVisibleTooltip(null);
          setCoords(null);
        }
        return;
      }

      const nextId = getOrAssignAnchorId(resolved.element);
      if (activeAnchorId === nextId && activeElement === resolved.element) {
        // Still on the same element; check if text mutated without attribute event
        const synced = machine.updateActiveText(nextId, resolved.text, now);
        if (synced.changed && synced.isVisible) {
          setVisibleTooltip({
            anchorId: nextId,
            text: synced.activeText,
            anchorRect: resolved.rect
          });
        }
        return;
      }

      clearPendingHoverTimer();
      activeElement = resolved.element;
      activeAnchorId = nextId;
      observeActiveElement(resolved.element, nextId);

      const transition = machine.pointerEnter(nextId, resolved.text, now);
      if (transition.shouldShowImmediately) {
        setVisibleTooltip({
          anchorId: nextId,
          text: transition.activeText,
          anchorRect: resolved.rect
        });
      } else if (transition.delayMs > 0) {
        setVisibleTooltip(null);
        setCoords(null);
        const scheduledEl = resolved.element;
        pendingTimerId = window.setTimeout(() => {
          pendingTimerId = null;
          if (!scheduledEl.isConnected || isDisabledControl(scheduledEl)) {
            machine.pointerLeave(nextId, performance.now());
            return;
          }
          const latestText = getElementTooltipText(scheduledEl, lastHoverPointerContext);
          machine.updateActiveText(nextId, latestText, performance.now());
          if (!machine.commitPendingShow(nextId)) {
            return;
          }
          const liveRect = scheduledEl.getBoundingClientRect();
          if (liveRect.width <= 0 || liveRect.height <= 0) {
            machine.pointerLeave(nextId, performance.now());
            return;
          }
          setVisibleTooltip({
            anchorId: nextId,
            text: machine.activeText,
            anchorRect: {
              left: liveRect.left,
              top: liveRect.top,
              width: liveRect.width,
              height: liveRect.height
            }
          });
        }, transition.delayMs);
      }
    };

    const handlePointerOver = (e: PointerEvent) => {
      lastHoverPointerContext = { pointerType: e.pointerType, buttons: e.buttons };
      if (e.pointerType === 'touch') {
        upgradeNativeTitlesInLineage(e.target);
        return;
      }
      activateHoverTarget(e.target, lastHoverPointerContext);
    };

    const handlePointerMove = (e: PointerEvent) => {
      lastHoverPointerContext = { pointerType: e.pointerType, buttons: e.buttons };
      if (e.pointerType === 'touch') {
        if (activeTouchPointerId === e.pointerId && touchPressTimerId !== null) {
          const dx = e.clientX - touchStartX;
          const dy = e.clientY - touchStartY;
          if (Math.hypot(dx, dy) > DEFAULT_TOUCH_SLOP_PX) {
            clearTouchTimers();
            activeTouchPointerId = null;
          }
        }
        return;
      }

      if (activeElement && !activeElement.isConnected) {
        const hit = document.elementFromPoint(e.clientX, e.clientY);
        activateHoverTarget(hit, lastHoverPointerContext);
        return;
      }
      activateHoverTarget(e.target, lastHoverPointerContext);
    };

    const handlePointerOut = (e: PointerEvent) => {
      lastHoverPointerContext = { pointerType: e.pointerType, buttons: e.buttons };
      if (e.pointerType === 'touch') return;
      if (!activeElement || !activeAnchorId) return;

      const related = e.relatedTarget;
      if (related instanceof Node && activeElement.contains(related)) {
        // Moved into a child inside activeElement; check if child resolves to a different anchor or interactive barrier
        const inner = resolveTooltipAnchor(related, lastHoverPointerContext);
        if (!inner) {
          activateHoverTarget(null, lastHoverPointerContext);
        } else if (inner.element !== activeElement) {
          activateHoverTarget(inner.element, lastHoverPointerContext);
        }
        return;
      }

      // Leaving activeElement — check if relatedTarget is an outer tooltipped ancestor or neighbor
      activateHoverTarget(related, lastHoverPointerContext);
    };

    const handlePointerDown = (e: PointerEvent) => {
      upgradeNativeTitlesInLineage(e.target);
      lastHoverPointerContext = {
        pointerType: e.pointerType,
        buttons: e.buttons || 1
      };

      if (e.pointerType === 'touch') {
        dismissImmediately(false);
        const resolved = resolveTooltipAnchor(e.target, {
          pointerType: 'touch',
          buttons: e.buttons || 1
        });
        if (!resolved || resolved.element.hasAttribute('data-timeline-mouse-tip')) {
          return;
        }
        // Skip checkboxes that handle long-press / context status menu directly
        if (resolved.element.classList.contains('things-checkbox')) {
          return;
        }
        activeTouchPointerId = e.pointerId;
        touchTooltipShownForPointer = false;
        touchStartX = e.clientX;
        touchStartY = e.clientY;
        const pressedEl = resolved.element;
        const pressedId = getOrAssignAnchorId(pressedEl);

        touchPressTimerId = window.setTimeout(() => {
          touchPressTimerId = null;
          if (!pressedEl.isConnected || isDisabledControl(pressedEl)) return;
          if (pressedEl.hasAttribute('data-timeline-mouse-tip')) return;
          const text = syncElementTooltipAttribute(pressedEl);
          if (!text) return;
          const liveRect = pressedEl.getBoundingClientRect();
          if (liveRect.width <= 0 || liveRect.height <= 0) return;

          touchTooltipShownForPointer = true;
          activeElement = pressedEl;
          activeAnchorId = pressedId;
          observeActiveElement(pressedEl, pressedId);
          setVisibleTooltip({
            anchorId: pressedId,
            text,
            anchorRect: {
              left: liveRect.left,
              top: liveRect.top,
              width: liveRect.width,
              height: liveRect.height
            }
          });

          touchAutoHideTimerId = window.setTimeout(() => {
            dismissImmediately(false);
          }, DEFAULT_TOUCH_MAX_VISIBLE_MS);
        }, DEFAULT_TOUCH_LONG_PRESS_MS);
        return;
      }

      dismissImmediately(false);
    };

    const handlePointerUpOrCancel = (e: PointerEvent) => {
      if (e.pointerType !== 'touch') return;
      if (activeTouchPointerId !== e.pointerId) return;
      activeTouchPointerId = null;

      if (touchPressTimerId !== null) {
        window.clearTimeout(touchPressTimerId);
        touchPressTimerId = null;
        touchTooltipShownForPointer = false;
        return;
      }

      if (touchTooltipShownForPointer && e.type === 'pointerup') {
        suppressTouchClickUntilMs = performance.now() + 500;
      }
      touchTooltipShownForPointer = false;

      // Tooltip was already shown via stationary long-press; fade away shortly after release
      if (touchAutoHideTimerId !== null) {
        window.clearTimeout(touchAutoHideTimerId);
      }
      touchAutoHideTimerId = window.setTimeout(() => {
        dismissImmediately(false);
      }, DEFAULT_TOUCH_RELEASE_HIDE_MS);
    };

    const handleClickCapture = (e: MouseEvent) => {
      if (suppressTouchClickUntilMs > performance.now()) {
        suppressTouchClickUntilMs = 0;
        e.preventDefault();
        e.stopPropagation();
      }
    };

    const handleDismissInteraction = () => {
      dismissImmediately(false);
    };

    document.addEventListener('pointerover', handlePointerOver, true);
    document.addEventListener('pointermove', handlePointerMove, {
      capture: true,
      passive: true
    });
    document.addEventListener('pointerout', handlePointerOut, true);
    document.addEventListener('pointerdown', handlePointerDown, {
      capture: true,
      passive: true
    });
    document.addEventListener('pointerup', handlePointerUpOrCancel, {
      capture: true,
      passive: true
    });
    document.addEventListener('pointercancel', handlePointerUpOrCancel, {
      capture: true,
      passive: true
    });
    document.addEventListener('click', handleClickCapture, true);
    document.addEventListener('wheel', handleDismissInteraction, {
      capture: true,
      passive: true
    });
    document.addEventListener('scroll', handleDismissInteraction, {
      capture: true,
      passive: true
    });
    document.addEventListener('keydown', handleDismissInteraction, true);
    window.addEventListener('blur', handleDismissInteraction);
    window.addEventListener('resize', handleDismissInteraction);

    return () => {
      clearPendingHoverTimer();
      clearTouchTimers();
      disconnectObserver();
      globalTitleSuppressor.disconnect();
      document.removeEventListener('pointerover', handlePointerOver, true);
      document.removeEventListener('pointermove', handlePointerMove, true);
      document.removeEventListener('pointerout', handlePointerOut, true);
      document.removeEventListener('pointerdown', handlePointerDown, true);
      document.removeEventListener('pointerup', handlePointerUpOrCancel, true);
      document.removeEventListener('pointercancel', handlePointerUpOrCancel, true);
      document.removeEventListener('click', handleClickCapture, true);
      document.removeEventListener('wheel', handleDismissInteraction, true);
      document.removeEventListener('scroll', handleDismissInteraction, true);
      document.removeEventListener('keydown', handleDismissInteraction, true);
      window.removeEventListener('blur', handleDismissInteraction);
      window.removeEventListener('resize', handleDismissInteraction);
    };
  }, []);

  useLayoutEffect(() => {
    if (!visibleTooltip || !tooltipRef.current || typeof window === 'undefined') {
      setCoords(null);
      return;
    }
    const el = tooltipRef.current;
    // Reset fixed position to origin before measuring so a previous right-edge
    // anchor coordinate never compresses natural width during warm handoff.
    el.style.left = '0px';
    el.style.top = '0px';
    const bubbleRect = el.getBoundingClientRect();
    const pos = computeTooltipPosition(
      visibleTooltip.anchorRect,
      bubbleRect.width,
      bubbleRect.height,
      window.innerWidth,
      window.innerHeight,
      8,
      8
    );
    el.style.left = `${pos.left}px`;
    el.style.top = `${pos.top}px`;
    setCoords({ left: pos.left, top: pos.top });
  }, [visibleTooltip]);

  if (!visibleTooltip || typeof document === 'undefined') {
    return null;
  }

  return createPortal(
    <div
      ref={tooltipRef}
      role="tooltip"
      data-testid="things-floating-tooltip"
      className="tb-floating-tooltip"
      style={{
        left: coords ? `${coords.left}px` : '-9999px',
        top: coords ? `${coords.top}px` : '-9999px',
        opacity: coords ? 1 : 0
      }}
    >
      {visibleTooltip.text}
    </div>,
    document.body
  );
};
