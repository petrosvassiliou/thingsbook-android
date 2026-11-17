export type MagicPlusDropMode = 'todo' | 'heading';

export interface MagicPlusTaskBox {
  taskId: string;
  sortOrder: number;
  headingId: string | null;
  evening: boolean;
  whenDate: number | null;
  top: number;
  bottom: number;
}

export interface MagicPlusHeadingBox {
  headingId: string;
  sortOrder: number;
  top: number;
  bottom: number;
  sectionBottom: number;
}

export interface MagicPlusResolvedDrop {
  mode: MagicPlusDropMode;
  indicatorTop: number;
  indicatorLeft: number;
  indicatorWidth: number;
  prevTaskOrder: number | null;
  nextTaskOrder: number | null;
  prevHeadingOrder: number | null;
  nextHeadingOrder: number | null;
  headingId: string | null;
  evening: boolean;
  whenDate: number | null;
}

export interface ResolveMagicPlusDropInput {
  clientX: number;
  clientY: number;
  isProjectView: boolean;
  containerLeft: number;
  containerWidth: number;
  containerTop: number;
  taskBoxes: MagicPlusTaskBox[];
  headingBoxes: MagicPlusHeadingBox[];
  headingGutterPx?: number;
}

function sameTaskGroup(a: MagicPlusTaskBox, b: MagicPlusTaskBox): boolean {
  return (
    a.headingId === b.headingId &&
    a.evening === b.evening &&
    a.whenDate === b.whenDate
  );
}

export function resolveMagicPlusDrop(
  input: ResolveMagicPlusDropInput
): MagicPlusResolvedDrop {
  const {
    clientX,
    clientY,
    isProjectView,
    containerLeft,
    containerWidth,
    containerTop,
    taskBoxes,
    headingBoxes,
    headingGutterPx = 88
  } = input;

  const sortedTasks = [...taskBoxes].sort((a, b) => a.top - b.top);
  const sortedHeadings = [...headingBoxes].sort((a, b) => a.top - b.top);
  const mode: MagicPlusDropMode =
    isProjectView && clientX <= containerLeft + headingGutterPx ? 'heading' : 'todo';

  if (mode === 'heading') {
    if (sortedHeadings.length === 0) {
      const indicatorTop =
        sortedTasks.length === 0
          ? containerTop + 96
          : clientY < (sortedTasks[0].top + sortedTasks[0].bottom) / 2
            ? sortedTasks[0].top - 6
            : sortedTasks[sortedTasks.length - 1].bottom + 8;

      return {
        mode: 'heading',
        indicatorTop,
        indicatorLeft: containerLeft,
        indicatorWidth: containerWidth,
        prevTaskOrder: null,
        nextTaskOrder: null,
        prevHeadingOrder: null,
        nextHeadingOrder: null,
        headingId: null,
        evening: false,
        whenDate: null
      };
    }

    const beforeIdx = sortedHeadings.findIndex(
      (h) => clientY < (h.top + h.bottom) / 2
    );

    if (beforeIdx === 0) {
      return {
        mode: 'heading',
        indicatorTop: sortedHeadings[0].top - 6,
        indicatorLeft: containerLeft,
        indicatorWidth: containerWidth,
        prevTaskOrder: null,
        nextTaskOrder: null,
        prevHeadingOrder: null,
        nextHeadingOrder: sortedHeadings[0].sortOrder,
        headingId: null,
        evening: false,
        whenDate: null
      };
    }

    if (beforeIdx > 0) {
      const prev = sortedHeadings[beforeIdx - 1];
      const next = sortedHeadings[beforeIdx];
      return {
        mode: 'heading',
        indicatorTop: next.top - 6,
        indicatorLeft: containerLeft,
        indicatorWidth: containerWidth,
        prevTaskOrder: null,
        nextTaskOrder: null,
        prevHeadingOrder: prev.sortOrder,
        nextHeadingOrder: next.sortOrder,
        headingId: null,
        evening: false,
        whenDate: null
      };
    }

    const lastHeading = sortedHeadings[sortedHeadings.length - 1];
    return {
      mode: 'heading',
      indicatorTop: Math.max(lastHeading.bottom, lastHeading.sectionBottom) + 8,
      indicatorLeft: containerLeft,
      indicatorWidth: containerWidth,
      prevTaskOrder: null,
      nextTaskOrder: null,
      prevHeadingOrder: lastHeading.sortOrder,
      nextHeadingOrder: null,
      headingId: null,
      evening: false,
      whenDate: null
    };
  }

  const todoLeft = containerLeft + 10;
  const todoWidth = Math.max(120, containerWidth - 20);

  // Check if pointer is inside an empty or collapsed project heading section
  if (isProjectView && sortedHeadings.length > 0) {
    for (let i = 0; i < sortedHeadings.length; i++) {
      const hd = sortedHeadings[i];
      const nextHeadingTop =
        i + 1 < sortedHeadings.length ? sortedHeadings[i + 1].top : Infinity;
      const tasksInHeading = sortedTasks.filter((tk) => tk.headingId === hd.headingId);
      if (
        tasksInHeading.length === 0 &&
        clientY >= hd.top - 6 &&
        clientY < Math.min(nextHeadingTop - 6, Math.max(hd.sectionBottom + 24, hd.bottom + 36))
      ) {
        return {
          mode: 'todo',
          indicatorTop: hd.bottom + 6,
          indicatorLeft: todoLeft,
          indicatorWidth: todoWidth,
          prevTaskOrder: null,
          nextTaskOrder: null,
          prevHeadingOrder: null,
          nextHeadingOrder: null,
          headingId: hd.headingId,
          evening: false,
          whenDate: null
        };
      }
    }
  }

  if (sortedTasks.length === 0) {
    return {
      mode: 'todo',
      indicatorTop: containerTop + 104,
      indicatorLeft: todoLeft,
      indicatorWidth: todoWidth,
      prevTaskOrder: null,
      nextTaskOrder: null,
      prevHeadingOrder: null,
      nextHeadingOrder: null,
      headingId: null,
      evening: false,
      whenDate: null
    };
  }

  const idx = sortedTasks.findIndex(
    (tk) => clientY < (tk.top + tk.bottom) / 2
  );

  if (idx === 0) {
    const first = sortedTasks[0];
    if (isProjectView && first.headingId != null) {
      const firstHeading = sortedHeadings.find((h) => h.headingId === first.headingId);
      if (firstHeading && clientY < firstHeading.top - 4) {
        return {
          mode: 'todo',
          indicatorTop: firstHeading.top - 10,
          indicatorLeft: todoLeft,
          indicatorWidth: todoWidth,
          prevTaskOrder: null,
          nextTaskOrder: null,
          prevHeadingOrder: null,
          nextHeadingOrder: null,
          headingId: null,
          evening: false,
          whenDate: null
        };
      }
    }

    return {
      mode: 'todo',
      indicatorTop: first.top - 4,
      indicatorLeft: todoLeft,
      indicatorWidth: todoWidth,
      prevTaskOrder: null,
      nextTaskOrder: first.sortOrder,
      prevHeadingOrder: null,
      nextHeadingOrder: null,
      headingId: first.headingId,
      evening: first.evening,
      whenDate: first.whenDate
    };
  }

  if (idx > 0) {
    const prev = sortedTasks[idx - 1];
    const next = sortedTasks[idx];

    if (sameTaskGroup(prev, next)) {
      return {
        mode: 'todo',
        indicatorTop: Math.round((prev.bottom + next.top) / 2),
        indicatorLeft: todoLeft,
        indicatorWidth: todoWidth,
        prevTaskOrder: prev.sortOrder,
        nextTaskOrder: next.sortOrder,
        prevHeadingOrder: null,
        nextHeadingOrder: null,
        headingId: next.headingId,
        evening: next.evening,
        whenDate: next.whenDate
      };
    }

    const nextHeading =
      isProjectView && next.headingId != null
        ? sortedHeadings.find((h) => h.headingId === next.headingId)
        : undefined;
    const boundaryY = nextHeading
      ? nextHeading.top
      : (prev.bottom + next.top) / 2;

    if (clientY < boundaryY) {
      return {
        mode: 'todo',
        indicatorTop: prev.bottom + 4,
        indicatorLeft: todoLeft,
        indicatorWidth: todoWidth,
        prevTaskOrder: prev.sortOrder,
        nextTaskOrder: null,
        prevHeadingOrder: null,
        nextHeadingOrder: null,
        headingId: prev.headingId,
        evening: prev.evening,
        whenDate: prev.whenDate
      };
    }

    return {
      mode: 'todo',
      indicatorTop: next.top - 4,
      indicatorLeft: todoLeft,
      indicatorWidth: todoWidth,
      prevTaskOrder: null,
      nextTaskOrder: next.sortOrder,
      prevHeadingOrder: null,
      nextHeadingOrder: null,
      headingId: next.headingId,
      evening: next.evening,
      whenDate: next.whenDate
    };
  }

  const last = sortedTasks[sortedTasks.length - 1];
  return {
    mode: 'todo',
    indicatorTop: last.bottom + 4,
    indicatorLeft: todoLeft,
    indicatorWidth: todoWidth,
    prevTaskOrder: last.sortOrder,
    nextTaskOrder: null,
    prevHeadingOrder: null,
    nextHeadingOrder: null,
    headingId: last.headingId,
    evening: last.evening,
    whenDate: last.whenDate
  };
}
