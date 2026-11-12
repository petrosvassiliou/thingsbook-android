import React, { useState } from 'react';
import { FolderPlus, Sparkles, Wand2, X } from 'lucide-react';
import {
  addDaysLocal,
  startOfDayEpoch,
  type AreaRecord,
  type SupportedLang
} from '../domain/types';
import {
  generateProjectBlueprint,
  parseBrainDumpToTasks
} from '../data/geminiService';
import { localDb } from '../data/localDb';
import type { TranslationStrings } from '../i18n/translations';

interface GeminiModalProps {
  areas: AreaRecord[];
  defaultAreaId: string | null;
  lang: SupportedLang;
  geminiApiKey: string;
  t: TranslationStrings;
  onNavigateProject: (projectId: string) => void;
  onToast: (msg: string) => void;
  onClose: () => void;
}

export const GeminiModal: React.FC<GeminiModalProps> = ({
  areas,
  defaultAreaId,
  lang,
  geminiApiKey,
  t,
  onNavigateProject,
  onToast,
  onClose
}) => {
  const [tab, setTab] = useState<'braindump' | 'architect'>('braindump');
  const [brainDumpText, setBrainDumpText] = useState('');
  const [projectGoal, setProjectGoal] = useState('');
  const [selectedAreaId, setSelectedAreaId] = useState<string>(defaultAreaId || '');
  const [loading, setLoading] = useState(false);

  const handleBrainDump = async () => {
    if (!brainDumpText.trim() || loading) return;
    setLoading(true);
    try {
      const parsed = await parseBrainDumpToTasks(brainDumpText, lang, geminiApiKey);
      const todayEpoch = startOfDayEpoch();
      for (let i = 0; i < parsed.length; i++) {
        const item = parsed[i];
        const whenDate =
          item.whenDaysOffset != null ? addDaysLocal(todayEpoch, item.whenDaysOffset) : null;
        const deadline =
          item.deadlineDaysOffset != null ? addDaysLocal(todayEpoch, item.deadlineDaysOffset) : null;
        const created = await localDb.upsertTask({
          title: item.title,
          notes: item.notes,
          scheduleState: item.scheduleState,
          whenDate,
          deadline,
          areaId: selectedAreaId || null,
          sortOrder: (i + 1) * 100
        });
        if (item.checklist.length > 0) {
          await localDb.addChecklistItems(created.id, item.checklist);
        }
      }
      onToast(`${parsed.length} ${t.entityTodos}`);
      onClose();
    } catch (err) {
      onToast(err instanceof Error ? err.message : 'Gemini error');
    } finally {
      setLoading(false);
    }
  };

  const handleProjectArchitect = async () => {
    if (!projectGoal.trim() || loading) return;
    setLoading(true);
    try {
      const bp = await generateProjectBlueprint(projectGoal, lang, geminiApiKey);
      const projectId = await localDb.createProjectWithBlueprint({
        title: bp.projectTitle,
        notes: bp.projectNotes,
        areaId: selectedAreaId || null,
        headings: bp.headings
      });
      onNavigateProject(projectId);
      onClose();
    } catch (err) {
      onToast(err instanceof Error ? err.message : 'Gemini error');
    } finally {
      setLoading(false);
    }
  };

  const activeAreas = areas.filter((a) => !a.isDeleted);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel wide" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Sparkles size={19} style={{ color: 'var(--gemini-violet)' }} />
            <span>{t.geminiHubTitle}</span>
          </h3>
          <button type="button" className="icon-btn" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <div style={{ display: 'flex', gap: 8, marginBottom: 14 }}>
          <button
            type="button"
            className={`btn-secondary ${tab === 'braindump' ? 'active' : ''}`}
            style={{
              flex: 1,
              borderColor: tab === 'braindump' ? 'var(--gemini-violet)' : undefined,
              color: tab === 'braindump' ? 'var(--gemini-violet)' : undefined
            }}
            onClick={() => setTab('braindump')}
          >
            <Wand2 size={15} />
            <span>{t.geminiBrainDumpTitle}</span>
          </button>
          <button
            type="button"
            className={`btn-secondary ${tab === 'architect' ? 'active' : ''}`}
            style={{
              flex: 1,
              borderColor: tab === 'architect' ? 'var(--gemini-violet)' : undefined,
              color: tab === 'architect' ? 'var(--gemini-violet)' : undefined
            }}
            onClick={() => setTab('architect')}
          >
            <FolderPlus size={15} />
            <span>{t.geminiArchitectTitle}</span>
          </button>
        </div>

        {activeAreas.length > 0 && (
          <div style={{ marginBottom: 12 }}>
            <label
              style={{
                display: 'block',
                fontSize: 12,
                color: 'var(--text-secondary)',
                marginBottom: 4
              }}
            >
              {t.entityArea} (optional)
            </label>
            <select
              className="text-field"
              value={selectedAreaId}
              onChange={(e) => setSelectedAreaId(e.target.value)}
            >
              <option value="">— {t.viewInbox} / Unassigned —</option>
              {activeAreas.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.title}
                </option>
              ))}
            </select>
          </div>
        )}

        {tab === 'braindump' ? (
          <div>
            <textarea
              className="text-field"
              rows={5}
              autoFocus
              value={brainDumpText}
              onChange={(e) => setBrainDumpText(e.target.value)}
              placeholder={t.geminiBrainDumpPlaceholder}
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 14 }}>
              <button type="button" className="btn-secondary" onClick={onClose}>
                {t.actionCancel}
              </button>
              <button
                type="button"
                className="btn-primary"
                style={{ background: 'var(--gemini-violet)' }}
                disabled={!brainDumpText.trim() || loading}
                onClick={handleBrainDump}
              >
                <Sparkles size={15} />
                <span>{loading ? t.geminiGenerating : t.geminiBrainDumpAction}</span>
              </button>
            </div>
          </div>
        ) : (
          <div>
            <textarea
              className="text-field"
              rows={4}
              autoFocus
              value={projectGoal}
              onChange={(e) => setProjectGoal(e.target.value)}
              placeholder={t.geminiArchitectPlaceholder}
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 14 }}>
              <button type="button" className="btn-secondary" onClick={onClose}>
                {t.actionCancel}
              </button>
              <button
                type="button"
                className="btn-primary"
                style={{ background: 'var(--gemini-violet)' }}
                disabled={!projectGoal.trim() || loading}
                onClick={handleProjectArchitect}
              >
                <FolderPlus size={15} />
                <span>{loading ? t.geminiGenerating : t.geminiArchitectAction}</span>
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
