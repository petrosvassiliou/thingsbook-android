import React from 'react';
import { CheckCircle2, FolderPlus, Heading, PackagePlus, Plus } from 'lucide-react';
import { MAX_AREAS, type ActiveNavigation } from '../domain/types';
import type { TranslationStrings } from '../i18n/translations';

interface MagicPlusFabProps {
  open: boolean;
  activeNav: ActiveNavigation;
  currentViewTitle: string;
  activeAreasCount: number;
  t: TranslationStrings;
  onToggleOpen: () => void;
  onClose: () => void;
  onNewTodo: () => void;
  onNewHeading: () => void;
  onNewProject: () => void;
  onNewArea: () => void;
}

export const MagicPlusFab: React.FC<MagicPlusFabProps> = ({
  open,
  activeNav,
  currentViewTitle,
  activeAreasCount,
  t,
  onToggleOpen,
  onClose,
  onNewTodo,
  onNewHeading,
  onNewProject,
  onNewArea
}) => {
  const isProjectView = activeNav.kind === 'project';
  const showNewArea = !isProjectView && activeAreasCount < MAX_AREAS;

  return (
    <>
      {open && (
        <>
          <div className="magic-fab-backdrop" onClick={onClose} />
          <div className="magic-fab-popover" role="menu">
            {/* 1. New To-Do (always shown) */}
            <button
              type="button"
              className="magic-fab-option"
              onClick={() => {
                onClose();
                onNewTodo();
              }}
            >
              <span className="magic-fab-opt-icon">
                <CheckCircle2 size={17} />
              </span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="magic-fab-opt-title-row">
                  <span className="magic-fab-opt-title">{t.entityNewTodo}</span>
                  <span className="magic-fab-shortcut-badge">Ctrl+N</span>
                </div>
                <div className="magic-fab-opt-sub">
                  {currentViewTitle ? `${t.magicTodoSubDefault} • ${currentViewTitle}` : t.magicTodoSubDefault}
                </div>
              </div>
            </button>

            {/* 2. New Heading (only in ProjectDetail) */}
            {isProjectView && (
              <button
                type="button"
                className="magic-fab-option"
                onClick={() => {
                  onClose();
                  onNewHeading();
                }}
              >
                <span className="magic-fab-opt-icon">
                  <Heading size={17} />
                </span>
                <div>
                  <div className="magic-fab-opt-title">{t.entityNewHeading}</div>
                  <div className="magic-fab-opt-sub">{t.magicHeadingSub}</div>
                </div>
              </button>
            )}

            {/* 3. New Project (always shown) */}
            <button
              type="button"
              className="magic-fab-option"
              onClick={() => {
                onClose();
                onNewProject();
              }}
            >
              <span className="magic-fab-opt-icon">
                <FolderPlus size={17} />
              </span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="magic-fab-opt-title-row">
                  <span className="magic-fab-opt-title">{t.entityNewProject}</span>
                  <span className="magic-fab-shortcut-badge">Ctrl+Shift+N</span>
                </div>
                <div className="magic-fab-opt-sub">{t.magicProjectSub}</div>
              </div>
            </button>

            {/* 4. New Area (non-Project views & < 8 areas) */}
            {showNewArea && (
              <button
                type="button"
                className="magic-fab-option"
                onClick={() => {
                  onClose();
                  onNewArea();
                }}
              >
                <span className="magic-fab-opt-icon">
                  <PackagePlus size={17} />
                </span>
                <div>
                  <div className="magic-fab-opt-title">{t.entityNewArea}</div>
                  <div className="magic-fab-opt-sub">
                    {t.magicAreaSub} ({activeAreasCount}/{MAX_AREAS})
                  </div>
                </div>
              </button>
            )}
          </div>
        </>
      )}

      <button
        type="button"
        className={`magic-fab-circle ${open ? 'open' : ''}`}
        onClick={onToggleOpen}
        aria-label={t.entityNewTodo}
        title={`${t.entityNewTodo} (Ctrl+N)`}
      >
        <Plus size={26} strokeWidth={2.4} />
      </button>
    </>
  );
};
