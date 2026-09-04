import './style.css';
import { auth, googleProvider, signInWithPopup, signOut, onAuthStateChanged, type User } from './firebase';
import type { Note, DrawerDestination, SyncStatus } from './types';
import { getLocalNotes } from './db';
import { syncEngine } from './sync';
import { renderMarkdown, toggleChecklistInMarkdown } from './markdown';
import { getIconSvg, getLogoSvg } from './icons';
import {
  createShare,
  unlockSharedNote,
  getSharedNoteMeta,
  revokeShare,
  listUserShares,
  exportAsMarkdown,
  exportAsHtml,
  exportAsPdf
} from './share';
import { vaultManager } from './vault';
import { exportLibrary, importLibrary, inspectBackup } from './backup';

type ThemeId = 'peach' | 'mauve' | 'teal' | 'sky';

const THEMES: Array<{ id: ThemeId; name: string; gradient: string }> = [
  { id: 'peach', name: 'Peach', gradient: 'linear-gradient(135deg, #f0788a 0 50%, #fff3e0 50% 100%)' },
  { id: 'mauve', name: 'Mauve', gradient: 'linear-gradient(135deg, #cba6f7 0 50%, #f5b78f 50% 100%)' },
  { id: 'teal', name: 'Teal', gradient: 'linear-gradient(135deg, #8fe0d2 0 50%, #f2a3b3 50% 100%)' },
  { id: 'sky', name: 'Sky', gradient: 'linear-gradient(135deg, #9dc4ff 0 50%, #c4b0f5 50% 100%)' }
];

const NOTE_COLORS = [
  { name: 'Default', hex: '#DEFAULT' },
  { name: 'Coral', hex: '#F28B82' },
  { name: 'Amber', hex: '#FBBC04' },
  { name: 'Yellow', hex: '#FFF475' },
  { name: 'Mint', hex: '#CCFF90' },
  { name: 'Teal', hex: '#A7FFEB' },
  { name: 'Sky', hex: '#CBF0F8' },
  { name: 'Blue', hex: '#AECBFA' },
  { name: 'Lavender', hex: '#D7AEFB' },
  { name: 'Pink', hex: '#FDCFE8' }
];

class AstralNotesApp {
  private notes: Note[] = [];
  private currentDestination: DrawerDestination = 'NOTES';
  private selectedTag: string | null = null;
  private searchQuery = '';
  private currentUser: User | null = null;
  private activeNote: Note | null = null;
  private editorMode: 'edit' | 'preview' = 'edit';
  private currentTheme: ThemeId = 'peach';

  private appEl: HTMLElement;

  constructor() {
    this.appEl = document.getElementById('app')!;
    this.initTheme();
    this.init();
  }

  private initTheme(): void {
    const saved = localStorage.getItem('cn:theme') as ThemeId;
    if (saved && ['peach', 'mauve', 'teal', 'sky'].includes(saved)) {
      this.currentTheme = saved;
    } else {
      this.currentTheme = 'peach';
    }
    document.documentElement.setAttribute('data-theme', this.currentTheme);
  }

  private setTheme(theme: ThemeId): void {
    this.currentTheme = theme;
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('cn:theme', theme);
    this.updateThemeButtons();
  }

  private updateThemeButtons(): void {
    const btns = this.appEl.querySelectorAll('.theme-swatch-btn');
    btns.forEach((btn) => {
      const tid = btn.getAttribute('data-theme-id');
      if (tid === this.currentTheme) {
        btn.classList.add('active');
      } else {
        btn.classList.remove('active');
      }
    });
  }

  private async init(): Promise<void> {
    const hash = window.location.hash;
    if (hash.startsWith('#/share/')) {
      const shareId = hash.replace('#/share/', '').trim();
      this.renderShareReader(shareId);
      return;
    }

    syncEngine.onStatusChange((status) => {
      this.updateSyncUI(status);
    });

    syncEngine.onNotesUpdate(async (updatedNotes) => {
      const valid = (updatedNotes || []).filter(n => n && n.id);
      this.notes = await syncEngine.redecryptNotes(valid);
      if (this.activeNote) {
        const fresh = this.notes.find(n => n.id === this.activeNote!.id);
        if (fresh) {
          this.activeNote = { ...fresh };
        }
      }
      this.renderNotesList();
      this.renderNavCounts();
    });

    vaultManager.onVaultStateChange(async () => {
      this.notes = await syncEngine.redecryptNotes(this.notes);
      this.render();
    });

    onAuthStateChanged(auth, async (user) => {
      this.currentUser = user;
      vaultManager.lockVault();

      if (user) {
        this.notes = await getLocalNotes();
        this.render();
        await syncEngine.startRealtimeSync();
      } else {
        this.notes = [];
        syncEngine.stopRealtimeSync();
        this.render();
      }
    });

    window.addEventListener('keydown', (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'n') {
        e.preventDefault();
        if (this.currentUser) this.openNewNote();
      } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'f') {
        e.preventDefault();
        const searchInput = document.getElementById('search-input') as HTMLInputElement | null;
        searchInput?.focus();
      } else if (e.key === 'Escape') {
        if (this.activeNote && window.innerWidth <= 820) {
          this.closeEditor();
        }
        this.closeContextMenu();
        this.closeModal();
      }
    });

    document.addEventListener('click', () => {
      this.closeContextMenu();
    });
  }

  private render(): void {
    if (!this.currentUser) {
      this.renderSignInGate();
      return;
    }

    const isVaultSection = this.currentDestination === 'VAULT';
    const isVaultUnlocked = vaultManager.isUnlocked();
    const isMobileEditorOpen = this.activeNote !== null;

    this.appEl.innerHTML = `
      <header class="app-header">
        <button class="brand-btn" id="brand-home-btn" title="Astral Notes Home">
          ${getLogoSvg(26)}
          <span class="brand-title">Astral Notes</span>
        </button>

        <div class="sync-pill" id="sync-pill-indicator" title="Sync Status: ${syncEngine.getStatus()}">
          <span class="sync-dot ${this.getSyncStatusClass()}"></span>
          <span class="sync-text" id="sync-text-label">${this.getSyncStatusLabel()}</span>
        </div>

        <div class="header-meta">
          <div class="theme-switcher" title="Choose color theme">
            ${THEMES.map(t => `
              <button
                type="button"
                class="theme-swatch-btn ${this.currentTheme === t.id ? 'active' : ''}"
                data-theme-id="${t.id}"
                title="${t.name} Theme"
                style="background: ${t.gradient};"
              ></button>
            `).join('')}
          </div>

          <span class="user-email-badge" title="${this.currentUser.email || ''}">
            ${this.currentUser.email || this.currentUser.displayName || 'Astral Explorer'}
          </span>

          ${isVaultSection && isVaultUnlocked ? `
            <button class="btn btn-soft btn-sm" id="header-relock-vault-btn" title="Relock Private Vault">
              ${getIconSvg('lock', 14)}
              <span>Lock Vault</span>
            </button>
          ` : ''}

          <button class="btn btn-ghost btn-icon-sm" id="auth-signout-btn" title="Sign Out">
            ${getIconSvg('log-out', 16)}
          </button>
        </div>
      </header>

      <div class="app-workspace ${isMobileEditorOpen ? 'mode-editor' : 'mode-list'}" id="app-workspace">
        <aside class="notes-sidebar" id="notes-sidebar">
          <div class="sidebar-controls">
            <div class="search-wrap">
              <span class="search-icon">${getIconSvg('search', 16)}</span>
              <input
                type="text"
                id="search-input"
                class="input-base search-input"
                placeholder="Search notes, tags, content... (Ctrl+F)"
                value="${this.searchQuery}"
              />
            </div>

            <div class="nav-destination-chips">
              <button type="button" class="dest-chip ${this.currentDestination === 'NOTES' && !this.selectedTag ? 'active' : ''}" data-dest="NOTES">
                ${getIconSvg('file', 13)}
                <span>Notes</span>
                <span class="dest-count" id="count-notes">0</span>
              </button>
              <button type="button" class="dest-chip ${this.currentDestination === 'PINNED' ? 'active' : ''}" data-dest="PINNED">
                ${getIconSvg('pin', 13)}
                <span>Pinned</span>
                <span class="dest-count" id="count-pinned">0</span>
              </button>
              <button type="button" class="dest-chip ${this.currentDestination === 'VAULT' ? 'active' : ''}" data-dest="VAULT">
                ${getIconSvg(isVaultUnlocked ? 'unlock' : 'lock', 13)}
                <span>Vault</span>
                <span class="dest-count" id="count-vault">${isVaultUnlocked ? '0' : '—'}</span>
              </button>
              <button type="button" class="dest-chip ${this.currentDestination === 'ARCHIVE' ? 'active' : ''}" data-dest="ARCHIVE">
                ${getIconSvg('archive', 13)}
                <span>Archive</span>
                <span class="dest-count" id="count-archive">0</span>
              </button>
              <button type="button" class="dest-chip ${this.currentDestination === 'TRASH' ? 'active' : ''}" data-dest="TRASH">
                ${getIconSvg('trash', 13)}
                <span>Trash</span>
                <span class="dest-count" id="count-trash">0</span>
              </button>
              <button type="button" class="dest-chip ${this.currentDestination === 'ANALYTICS' ? 'active' : ''}" data-dest="ANALYTICS">
                ${getIconSvg('analytics', 13)}
                <span>Analytics</span>
              </button>
              <button type="button" class="dest-chip ${this.currentDestination === 'BACKUP' ? 'active' : ''}" data-dest="BACKUP">
                ${getIconSvg('download', 13)}
                <span>Backup</span>
              </button>
            </div>

            <div class="tags-filter-bar" id="tags-filter-bar" style="display: none;"></div>

            <div style="display: flex; gap: 0.5rem; align-items: center;">
              <button class="btn btn-primary btn-sm" id="sidebar-new-note-btn" style="flex: 1;">
                ${getIconSvg('plus', 16)}
                <span>New Note</span>
              </button>
              <button class="btn btn-soft btn-sm" id="sidebar-import-btn" title="Import Markdown or Text">
                ${getIconSvg('upload', 15)}
                <span>Import</span>
              </button>
              <input type="file" id="file-importer" style="display: none;" accept=".md,.txt,text/plain,text/markdown" />
            </div>
          </div>

          <div class="notes-list-scroll" id="notes-list-scroll"></div>
        </aside>

        <section class="editor-detail-pane" id="editor-detail-pane">
          ${this.renderDetailContentHtml()}
        </section>
      </div>

      <nav class="mobile-bottom-dock">
        <button class="mobile-dock-btn ${this.currentDestination === 'NOTES' ? 'active' : ''}" data-dock="NOTES">
          ${getIconSvg('file', 18)}
          <span>Notes</span>
        </button>
        <button class="mobile-dock-btn ${this.currentDestination === 'VAULT' ? 'active' : ''}" data-dock="VAULT">
          ${getIconSvg(isVaultUnlocked ? 'unlock' : 'lock', 18)}
          <span>Vault</span>
        </button>
        <button class="mobile-dock-btn" id="dock-action-new" style="color: var(--primary); font-weight: 600;">
          ${getIconSvg('plus', 20)}
          <span>New</span>
        </button>
        <button class="mobile-dock-btn" id="dock-action-task">
          ${getIconSvg('check-square', 18)}
          <span>Tasks</span>
        </button>
        <button class="mobile-dock-btn ${this.currentDestination === 'BACKUP' ? 'active' : ''}" data-dock="BACKUP">
          ${getIconSvg('download', 18)}
          <span>Backup</span>
        </button>
      </nav>

      <div id="modal-mount"></div>
      <div id="context-mount"></div>
    `;

    this.bindGlobalEvents();
    this.renderNotesList();
    this.renderNavCounts();
    this.bindDetailEvents();
  }

  private renderDetailContentHtml(): string {
    if (this.currentDestination === 'ANALYTICS') {
      return `<div id="analytics-mount" style="flex: 1; overflow-y: auto; padding: 1.5rem 2rem;"></div>`;
    }

    if (this.currentDestination === 'BACKUP') {
      return `<div id="backup-mount" style="flex: 1; overflow-y: auto; padding: 1.5rem 2rem;"></div>`;
    }

    if (this.currentDestination === 'VAULT' && !vaultManager.isUnlocked()) {
      return `
        <div style="flex: 1; display: flex; align-items: center; justify-content: center; padding: 1.5rem;">
          <div id="vault-auth-card-mount" class="card-surface modal-dialog" style="max-width: 440px; box-shadow: none;"></div>
        </div>
      `;
    }

    if (!this.activeNote) {
      return `
        <div class="empty-detail-state">
          <div style="color: var(--primary); opacity: 0.8; margin-bottom: 0.5rem;">
            ${getLogoSvg(56)}
          </div>
          <h2 style="font-size: 1.25rem; font-weight: 600; color: var(--foreground); margin-bottom: 0.4rem;">
            No Note Selected
          </h2>
          <p style="font-size: 0.875rem; color: var(--muted-foreground); max-width: 280px; margin-bottom: 1.5rem; line-height: 1.5;">
            Select a note from the list or create a new note to start writing.
          </p>
          <button class="btn btn-primary btn-sm" id="detail-empty-new-note-btn">
            ${getIconSvg('plus', 16)}
            <span>Create New Note</span>
          </button>
        </div>
      `;
    }

    const note = this.activeNote;
    const isLockedAndHidden = note.isLocked && !vaultManager.isUnlocked();
    const wordCount = note.content.trim() ? note.content.trim().split(/\s+/).length : 0;
    const charCount = note.content.length;
    const updatedStr = new Date(note.updatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    return `
      <div class="editor-toolbar">
        <button class="btn btn-ghost btn-icon-sm mobile-back-btn" id="editor-back-btn" title="Back to Notes">
          ${getIconSvg('arrow-left', 18)}
        </button>

        <div class="mode-toggle-pill">
          <button type="button" class="mode-toggle-btn ${this.editorMode === 'edit' ? 'active' : ''}" id="editor-tab-edit">
            ${getIconSvg('pencil', 14)}
            <span>Write</span>
          </button>
          <button type="button" class="mode-toggle-btn ${this.editorMode === 'preview' ? 'active' : ''}" id="editor-tab-preview">
            ${getIconSvg('eye', 14)}
            <span>Preview</span>
          </button>
        </div>

        <div style="display: flex; align-items: center; gap: 0.25rem; margin-left: 0.25rem;">
          <button class="btn btn-ghost btn-icon-sm" data-tool="bold" title="Bold">${getIconSvg('edit', 14)}</button>
          <button class="btn btn-ghost btn-icon-sm" data-tool="heading" title="Heading">${getIconSvg('code', 14)}</button>
          <button class="btn btn-ghost btn-icon-sm" data-tool="task" title="Checklist item">${getIconSvg('check-square', 14)}</button>
          <button class="btn btn-ghost btn-icon-sm" data-tool="image" title="Insert Image">${getIconSvg('image', 14)}</button>
        </div>

        <div class="editor-toolbar-actions">
          <div style="position: relative;">
            <button class="btn btn-ghost btn-icon-sm" id="editor-color-btn" title="Note Color">
              <span id="editor-color-dot" style="width: 12px; height: 12px; border-radius: 50%; background: ${this.getColorDisplayHex(note.colorHex)}; border: 1px solid var(--border-strong); display: inline-block;"></span>
            </button>
            <div id="editor-color-menu" style="display: none; position: absolute; top: calc(100% + 4px); right: 0; background: var(--card); border: 1px solid var(--border-strong); border-radius: var(--radius-lg); padding: 0.4rem; z-index: 100; box-shadow: 0 10px 25px rgba(0,0,0,0.5); width: 140px;">
              ${NOTE_COLORS.map(c => `
                <div class="editor-color-item" data-hex="${c.hex}" style="display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: var(--radius-sm); cursor: pointer; font-size: 0.8rem; color: var(--foreground);">
                  <span style="width: 12px; height: 12px; border-radius: 50%; background: ${c.hex === '#DEFAULT' ? 'var(--surface-2)' : c.hex}; border: 1px solid var(--border-strong); display: inline-block;"></span>
                  <span>${c.name}</span>
                </div>
              `).join('')}
            </div>
          </div>

          <button class="btn btn-ghost btn-icon-sm ${note.isPinned ? 'active' : ''}" id="editor-pin-btn" title="${note.isPinned ? 'Unpin' : 'Pin'}">
            ${getIconSvg(note.isPinned ? 'pin-off' : 'pin', 16)}
          </button>

          <button class="btn btn-ghost btn-icon-sm" id="editor-share-btn" title="Share Note">
            ${getIconSvg('share', 16)}
          </button>

          <div style="position: relative;">
            <button class="btn btn-ghost btn-icon-sm" id="editor-export-btn" title="Export Note">
              ${getIconSvg('download', 16)}
            </button>
            <div id="editor-export-menu" style="display: none; position: absolute; top: calc(100% + 4px); right: 0; background: var(--card); border: 1px solid var(--border-strong); border-radius: var(--radius-lg); padding: 0.4rem; z-index: 100; box-shadow: 0 10px 25px rgba(0,0,0,0.5); width: 155px;">
              <div class="editor-export-item" data-format="md" style="display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: var(--radius-sm); cursor: pointer; font-size: 0.8rem; color: var(--foreground);">
                ${getIconSvg('file', 13)} <span>Markdown (.md)</span>
              </div>
              <div class="editor-export-item" data-format="html" style="display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: var(--radius-sm); cursor: pointer; font-size: 0.8rem; color: var(--foreground);">
                ${getIconSvg('code', 13)} <span>HTML (.html)</span>
              </div>
              <div class="editor-export-item" data-format="pdf" style="display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: var(--radius-sm); cursor: pointer; font-size: 0.8rem; color: var(--foreground);">
                ${getIconSvg('printer', 13)} <span>Print / PDF</span>
              </div>
            </div>
          </div>

          <button class="btn btn-ghost btn-icon-sm" id="editor-trash-btn" title="Delete Note" style="color: var(--destructive);">
            ${getIconSvg('trash', 16)}
          </button>

          <button class="btn btn-primary btn-sm" id="editor-save-btn" title="Save Note">
            ${getIconSvg('check', 15)}
            <span>Save</span>
          </button>
        </div>
      </div>

      <div class="editor-form">
        <input
          type="text"
          id="editor-title"
          class="editor-title-input"
          placeholder="Note Title..."
          value="${note.title}"
          ${isLockedAndHidden ? 'disabled' : ''}
        />

        <div class="editor-tags-row" id="editor-tags-row">
          ${note.tags.map(t => `
            <span class="editor-tag-chip" data-tag="${t}">
              <span>#${t}</span>
              <button type="button" class="editor-tag-remove" data-remove-tag="${t}" title="Remove tag">&times;</button>
            </span>
          `).join('')}
          <input type="text" id="editor-add-tag-input" class="editor-tag-add-input" placeholder="+ Add tag" />
        </div>

        ${this.editorMode === 'edit' ? `
          <textarea
            id="editor-textarea"
            class="editor-body-textarea"
            placeholder="Start typing in Markdown..."
            ${isLockedAndHidden ? 'disabled' : ''}
          >${note.content}</textarea>
        ` : `
          <div id="editor-preview" class="prose-note" style="flex: 1; overflow-y: auto; padding-right: 0.5rem;">
            ${isLockedAndHidden ? '<p style="color: var(--muted-foreground);">Unlock your private vault to view this encrypted note.</p>' : renderMarkdown(note.content)}
          </div>
        `}

        <div style="display: flex; align-items: center; gap: 1rem; font-size: 0.75rem; color: var(--muted-foreground); margin-top: 0.75rem; padding-top: 0.75rem; border-top: 1px solid var(--border);">
          <span>${wordCount} words</span>
          <span>${charCount} characters</span>
          <span style="margin-left: auto;">Last saved at ${updatedStr}</span>
        </div>
      </div>
    `;
  }

  private renderSignInGate(): void {
    this.appEl.innerHTML = `
      <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; width: 100vw; padding: 1.5rem; background: radial-gradient(circle at 50% 35%, color-mix(in oklab, var(--primary) 12%, transparent) 0%, transparent 65%), var(--background);">
        <div class="card-surface modal-dialog" style="max-width: 440px; text-align: center; padding: 2.5rem 2rem;">
          <div style="color: var(--primary); margin: 0 auto 1.25rem;">
            ${getLogoSvg(54)}
          </div>
          <h1 style="font-size: 1.6rem; font-weight: 700; color: var(--foreground); margin-bottom: 0.5rem; letter-spacing: -0.02em;">
            Astral Notes
          </h1>
          <p style="font-size: 0.9rem; color: var(--muted-foreground); margin-bottom: 2rem; line-height: 1.55;">
            Sign in with your Google account to synchronize your encrypted notes and private locked vault across Android and Web.
          </p>
          <button class="btn btn-primary" id="gate-signin-btn" style="width: 100%; padding: 0.75rem 1.25rem; font-size: 0.95rem;">
            ${getIconSvg('log-in', 18)}
            <span>Sign In with Google</span>
          </button>
          <div id="gate-signin-error" style="color: var(--destructive); font-size: 0.85rem; margin-top: 0.85rem; font-weight: 500;"></div>
        </div>
      </div>
    `;

    document.getElementById('gate-signin-btn')?.addEventListener('click', async () => {
      const btn = document.getElementById('gate-signin-btn') as HTMLButtonElement;
      const errEl = document.getElementById('gate-signin-error')!;
      errEl.textContent = '';
      btn.disabled = true;
      btn.textContent = 'Connecting...';
      try {
        await signInWithPopup(auth, googleProvider);
      } catch (err) {
        errEl.textContent = (err as Error).message || 'Sign in failed. Please try again.';
        btn.disabled = false;
        btn.innerHTML = `${getIconSvg('log-in', 18)} <span>Sign In with Google</span>`;
      }
    });
  }

  private bindGlobalEvents(): void {
    document.getElementById('brand-home-btn')?.addEventListener('click', () => {
      this.currentDestination = 'NOTES';
      this.selectedTag = null;
      this.searchQuery = '';
      this.render();
    });

    this.appEl.querySelectorAll('.theme-swatch-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        const themeId = btn.getAttribute('data-theme-id') as ThemeId;
        if (themeId) this.setTheme(themeId);
      });
    });

    document.getElementById('auth-signout-btn')?.addEventListener('click', async () => {
      vaultManager.lockVault();
      await signOut(auth);
    });

    document.getElementById('header-relock-vault-btn')?.addEventListener('click', () => {
      vaultManager.lockVault();
    });

    this.appEl.querySelectorAll('.dest-chip').forEach((chip) => {
      chip.addEventListener('click', () => {
        const dest = chip.getAttribute('data-dest') as DrawerDestination;
        if (dest) {
          this.currentDestination = dest;
          this.selectedTag = null;
          if (dest === 'ANALYTICS' || dest === 'BACKUP') {
            this.activeNote = null;
          }
          this.render();
        }
      });
    });

    this.appEl.querySelectorAll('.mobile-dock-btn[data-dock]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const dest = btn.getAttribute('data-dock') as DrawerDestination;
        if (dest) {
          this.currentDestination = dest;
          this.selectedTag = null;
          this.activeNote = null;
          this.render();
        }
      });
    });

    document.getElementById('dock-action-new')?.addEventListener('click', () => {
      this.openNewNote();
    });

    document.getElementById('dock-action-task')?.addEventListener('click', () => {
      const taskNote = this.createNoteModel('Tasks', '- [ ] ');
      if (this.currentDestination === 'VAULT') taskNote.isLocked = true;
      this.openNote(taskNote);
    });

    const searchInput = document.getElementById('search-input') as HTMLInputElement;
    searchInput?.addEventListener('input', (e) => {
      this.searchQuery = (e.target as HTMLInputElement).value;
      this.renderNotesList();
    });

    document.getElementById('sidebar-new-note-btn')?.addEventListener('click', () => {
      this.openNewNote();
    });

    const fileImporter = document.getElementById('file-importer') as HTMLInputElement;
    document.getElementById('sidebar-import-btn')?.addEventListener('click', () => {
      fileImporter.click();
    });

    fileImporter?.addEventListener('change', async (e) => {
      const files = (e.target as HTMLInputElement).files;
      if (!files || files.length === 0) return;
      const file = files[0];
      const text = await file.text();
      const firstHeadingMatch = text.match(/^#+\s*(.*)$/m);
      const title = firstHeadingMatch ? firstHeadingMatch[1].trim() : file.name.replace(/\.[^/.]+$/, '');
      const newNote = this.createNoteModel(title, text);
      if (this.currentDestination === 'VAULT') {
        newNote.isLocked = true;
      }
      await syncEngine.uploadNote(newNote);
      this.notes.unshift(newNote);
      this.renderNotesList();
      this.openNote(newNote);
    });
  }

  private bindDetailEvents(): void {
    if (this.currentDestination === 'ANALYTICS') {
      const mount = document.getElementById('analytics-mount');
      if (mount) this.renderAnalyticsView(mount);
      return;
    }

    if (this.currentDestination === 'BACKUP') {
      const mount = document.getElementById('backup-mount');
      if (mount) this.renderBackupView(mount);
      return;
    }

    if (this.currentDestination === 'VAULT' && !vaultManager.isUnlocked()) {
      const mount = document.getElementById('vault-auth-card-mount');
      if (mount) this.renderVaultAuthScreen(mount);
      return;
    }

    if (!this.activeNote) {
      document.getElementById('detail-empty-new-note-btn')?.addEventListener('click', () => {
        this.openNewNote();
      });
      return;
    }

    document.getElementById('editor-back-btn')?.addEventListener('click', () => {
      this.closeEditor();
    });

    const tabEdit = document.getElementById('editor-tab-edit');
    const tabPreview = document.getElementById('editor-tab-preview');

    tabEdit?.addEventListener('click', () => {
      if (this.editorMode !== 'edit') {
        this.editorMode = 'edit';
        this.renderDetailPane();
      }
    });

    tabPreview?.addEventListener('click', () => {
      if (this.editorMode !== 'preview') {
        this.editorMode = 'preview';
        this.renderDetailPane();
      }
    });

    const textarea = document.getElementById('editor-textarea') as HTMLTextAreaElement | null;
    const titleInput = document.getElementById('editor-title') as HTMLInputElement | null;

    if (textarea && this.activeNote) {
      textarea.addEventListener('input', () => {
        if (this.activeNote) {
          this.activeNote.content = textarea.value;
        }
      });
    }

    if (titleInput && this.activeNote) {
      titleInput.addEventListener('input', () => {
        if (this.activeNote) {
          this.activeNote.title = titleInput.value.trim();
        }
      });
    }

    if (this.editorMode === 'preview') {
      const previewEl = document.getElementById('editor-preview');
      if (previewEl) {
        this.bindInteractiveChecklist(previewEl);
      }
    }

    this.appEl.querySelectorAll('[data-tool]').forEach(btn => {
      btn.addEventListener('click', () => {
        const tool = btn.getAttribute('data-tool');
        if (textarea) this.applyToolbarAction(tool, textarea);
      });
    });

    const colorBtn = document.getElementById('editor-color-btn');
    const colorMenu = document.getElementById('editor-color-menu');
    colorBtn?.addEventListener('click', (e) => {
      e.stopPropagation();
      if (colorMenu) {
        colorMenu.style.display = colorMenu.style.display === 'none' ? 'block' : 'none';
      }
    });

    colorMenu?.querySelectorAll('.editor-color-item').forEach(item => {
      item.addEventListener('click', (e) => {
        e.stopPropagation();
        const hex = item.getAttribute('data-hex') || '#DEFAULT';
        if (this.activeNote) {
          this.activeNote.colorHex = hex;
          const dot = document.getElementById('editor-color-dot');
          if (dot) dot.style.background = this.getColorDisplayHex(hex);
          this.saveActiveNote();
        }
        if (colorMenu) colorMenu.style.display = 'none';
      });
    });

    document.getElementById('editor-pin-btn')?.addEventListener('click', () => {
      if (!this.activeNote) return;
      this.activeNote.isPinned = !this.activeNote.isPinned;
      this.saveActiveNote();
      this.renderDetailPane();
    });

    document.getElementById('editor-share-btn')?.addEventListener('click', () => {
      if (!this.activeNote) return;
      this.openShareModal(this.activeNote);
    });

    const exportBtn = document.getElementById('editor-export-btn');
    const exportMenu = document.getElementById('editor-export-menu');
    exportBtn?.addEventListener('click', (e) => {
      e.stopPropagation();
      if (exportMenu) {
        exportMenu.style.display = exportMenu.style.display === 'none' ? 'block' : 'none';
      }
    });

    exportMenu?.querySelectorAll('.editor-export-item').forEach(item => {
      item.addEventListener('click', (e) => {
        e.stopPropagation();
        if (!this.activeNote) return;
        const fmt = item.getAttribute('data-format');
        if (fmt === 'md') exportAsMarkdown(this.activeNote);
        else if (fmt === 'html') exportAsHtml(this.activeNote);
        else if (fmt === 'pdf') exportAsPdf(this.activeNote);
        if (exportMenu) exportMenu.style.display = 'none';
      });
    });

    document.addEventListener('click', () => {
      if (colorMenu) colorMenu.style.display = 'none';
      if (exportMenu) exportMenu.style.display = 'none';
    });

    document.getElementById('editor-trash-btn')?.addEventListener('click', async () => {
      if (!this.activeNote) return;
      if (this.activeNote.isTrash) {
        if (confirm('Permanently delete this note?')) {
          await syncEngine.deleteNote(this.activeNote.id);
          this.notes = this.notes.filter(n => n.id !== this.activeNote!.id);
          this.closeEditor();
          this.renderNotesList();
          this.renderNavCounts();
        }
      } else {
        this.activeNote.isTrash = true;
        this.activeNote.isPinned = false;
        await this.saveActiveNote();
        this.closeEditor();
        this.renderNotesList();
        this.renderNavCounts();
      }
    });

    document.getElementById('editor-save-btn')?.addEventListener('click', async () => {
      await this.saveActiveNote();
      this.showToast('Note saved and synchronized');
    });

    const tagInput = document.getElementById('editor-add-tag-input') as HTMLInputElement | null;
    tagInput?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ',') {
        e.preventDefault();
        const val = tagInput.value.trim().replace(/^#/, '');
        if (val && this.activeNote) {
          if (!this.activeNote.tags.includes(val)) {
            this.activeNote.tags.push(val);
            tagInput.value = '';
            this.saveActiveNote();
            this.renderDetailPane();
          }
        }
      }
    });

    this.appEl.querySelectorAll('[data-remove-tag]').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const tag = btn.getAttribute('data-remove-tag');
        if (tag && this.activeNote) {
          this.activeNote.tags = this.activeNote.tags.filter(t => t !== tag);
          this.saveActiveNote();
          this.renderDetailPane();
        }
      });
    });
  }

  private renderDetailPane(): void {
    const pane = document.getElementById('editor-detail-pane');
    if (pane) {
      pane.innerHTML = this.renderDetailContentHtml();
      this.bindDetailEvents();
    }
  }

  private async saveActiveNote(): Promise<void> {
    if (!this.activeNote) return;
    const titleInput = document.getElementById('editor-title') as HTMLInputElement | null;
    const textarea = document.getElementById('editor-textarea') as HTMLTextAreaElement | null;

    if (titleInput) this.activeNote.title = titleInput.value.trim();
    if (textarea) this.activeNote.content = textarea.value;
    this.activeNote.updatedAt = Date.now();

    await syncEngine.uploadNote(this.activeNote);

    const idx = this.notes.findIndex(n => n.id === this.activeNote!.id);
    if (idx !== -1) {
      this.notes[idx] = { ...this.activeNote };
    } else {
      this.notes.unshift({ ...this.activeNote });
    }

    this.renderNotesList();
    this.renderNavCounts();
  }

  private openNewNote(): void {
    const note = this.createNoteModel();
    if (this.currentDestination === 'VAULT') {
      note.isLocked = true;
    }
    this.openNote(note);
  }

  private openNote(note: Note): void {
    this.activeNote = { ...note };
    this.editorMode = note.content ? 'preview' : 'edit';

    const workspace = document.getElementById('app-workspace');
    if (workspace) {
      workspace.classList.remove('mode-list');
      workspace.classList.add('mode-editor');
    }

    this.renderDetailPane();
    this.renderNotesList();
  }

  private closeEditor(): void {
    this.activeNote = null;
    const workspace = document.getElementById('app-workspace');
    if (workspace) {
      workspace.classList.remove('mode-editor');
      workspace.classList.add('mode-list');
    }
    this.renderDetailPane();
    this.renderNotesList();
  }

  private bindInteractiveChecklist(preview: HTMLElement): void {
    preview.querySelectorAll('.task-checkbox').forEach(cb => {
      cb.addEventListener('change', async (e) => {
        if (!this.activeNote) return;
        const lineIdx = parseInt((e.target as HTMLElement).getAttribute('data-line') || '-1', 10);
        if (lineIdx >= 0) {
          const updated = toggleChecklistInMarkdown(this.activeNote.content, lineIdx);
          this.activeNote.content = updated;
          preview.innerHTML = renderMarkdown(updated);
          this.bindInteractiveChecklist(preview);
          await this.saveActiveNote();
        }
      });
    });
  }

  private applyToolbarAction(action: string | null, textarea: HTMLTextAreaElement): void {
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const text = textarea.value;
    const selected = text.substring(start, end);

    let insertion = '';
    let offset = 0;

    switch (action) {
      case 'bold':
        insertion = `**${selected || 'bold text'}**`;
        offset = selected ? insertion.length : 2;
        break;
      case 'heading':
        insertion = `\n## ${selected || 'Heading'}\n`;
        offset = insertion.length;
        break;
      case 'task':
        insertion = `\n- [ ] ${selected || 'Task item'}`;
        offset = insertion.length;
        break;
      case 'image': {
        const url = prompt('Enter Image URL:');
        if (url) {
          insertion = `\n![${selected || 'Image'}](${url.trim()})\n`;
          offset = insertion.length;
        } else return;
        break;
      }
      case 'code':
        insertion = `\n\`\`\`\n${selected || 'code here'}\n\`\`\`\n`;
        offset = insertion.length;
        break;
    }

    textarea.value = text.substring(0, start) + insertion + text.substring(end);
    textarea.focus();
    textarea.setSelectionRange(start + offset, start + offset);
    if (this.activeNote) {
      this.activeNote.content = textarea.value;
    }
  }

  private getFilteredNotes(): Note[] {
    return this.notes.filter((note) => {
      if (!note || typeof note !== 'object' || !note.id) return false;
      if (note.isDeleted) return false;

      if (this.currentDestination === 'TRASH') {
        if (!note.isTrash) return false;
      } else {
        if (note.isTrash) return false;
        if (this.currentDestination === 'ARCHIVE') {
          if (!note.isArchived) return false;
        } else if (this.currentDestination === 'VAULT') {
          if (!note.isLocked) return false;
          if (!vaultManager.isUnlocked()) return false;
        } else if (this.currentDestination === 'PINNED') {
          if (!note.isPinned || note.isArchived || note.isLocked) return false;
        } else {
          if (note.isArchived || note.isLocked) return false;
        }
      }

      if (this.selectedTag && !note.tags.includes(this.selectedTag)) {
        return false;
      }

      if (this.searchQuery.trim()) {
        const q = this.searchQuery.toLowerCase();
        const matchesTitle = note.title.toLowerCase().includes(q);
        const matchesContent = note.content.toLowerCase().includes(q);
        const matchesTag = note.tags.some(t => t.toLowerCase().includes(q));
        if (!matchesTitle && !matchesContent && !matchesTag) return false;
      }

      return true;
    });
  }

  private renderNotesList(): void {
    const container = document.getElementById('notes-list-scroll');
    if (!container) return;

    if (this.currentDestination === 'ANALYTICS' || this.currentDestination === 'BACKUP') {
      container.innerHTML = `
        <div class="notes-empty-state">
          <p>${this.currentDestination === 'ANALYTICS' ? 'Analytics dashboard active' : 'Backup and restore manager active'}</p>
        </div>
      `;
      return;
    }

    if (this.currentDestination === 'VAULT' && !vaultManager.isUnlocked()) {
      container.innerHTML = `
        <div class="notes-empty-state">
          <p>Vault is locked. Enter your password to view notes.</p>
        </div>
      `;
      return;
    }

    const filtered = this.getFilteredNotes();

    if (filtered.length === 0) {
      container.innerHTML = `
        <div class="notes-empty-state">
          <p>No notes found in ${this.currentDestination.toLowerCase()}</p>
        </div>
      `;
      return;
    }

    container.innerHTML = filtered.map(n => this.renderNoteItemCardHtml(n)).join('');

    container.querySelectorAll('.note-item-card').forEach((card) => {
      const id = card.getAttribute('data-id');
      const note = this.notes.find(n => n.id === id);
      if (!note) return;

      card.addEventListener('click', () => {
        if (note.isLocked && !vaultManager.isUnlocked()) {
          this.showToast('Please unlock your private vault first');
          this.currentDestination = 'VAULT';
          this.render();
          return;
        }
        this.openNote(note);
      });

      card.addEventListener('contextmenu', (e) => {
        e.preventDefault();
        this.openContextMenu(e as MouseEvent, note);
      });
    });
  }

  private renderNoteItemCardHtml(note: Note): string {
    const isLockedAndHidden = note.isLocked && !vaultManager.isUnlocked();
    const displayTitle = isLockedAndHidden ? 'Encrypted Note' : (note.title || 'Untitled');
    const displaySnippet = isLockedAndHidden
      ? 'Locked vault note. Unlock to read.'
      : (note.content.trim().substring(0, 90) || 'Empty note...');
    const displayTags = isLockedAndHidden ? [] : note.tags;
    const isActive = this.activeNote && this.activeNote.id === note.id;

    const formattedDate = new Date(note.updatedAt).toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric'
    });

    const borderStyle = note.colorHex && note.colorHex !== '#DEFAULT'
      ? `style="border-left: 3px solid ${note.colorHex};"`
      : '';

    return `
      <article class="note-item-card ${isActive ? 'active' : ''}" data-id="${note.id}" ${borderStyle}>
        <div class="note-card-title-row">
          <h3 class="note-card-title">${displayTitle}</h3>
          ${note.isPinned ? `<span class="note-pin-indicator" title="Pinned"></span>` : ''}
        </div>
        <div class="note-card-snippet">${displaySnippet}</div>
        <div class="note-card-footer">
          <span class="note-card-date">${formattedDate}</span>
          <div class="note-card-badges">
            ${displayTags.slice(0, 2).map(t => `<span class="tag-chip">#${t}</span>`).join('')}
            ${note.isLocked ? getIconSvg('lock', 12) : ''}
          </div>
        </div>
      </article>
    `;
  }

  private renderNavCounts(): void {
    const active = this.notes.filter(n => !n.isTrash && !n.isDeleted && !n.isArchived && !n.isLocked);
    const pinned = this.notes.filter(n => n.isPinned && !n.isTrash && !n.isDeleted && !n.isArchived && !n.isLocked);
    const vault = this.notes.filter(n => n.isLocked && !n.isTrash && !n.isDeleted);
    const archive = this.notes.filter(n => n.isArchived && !n.isTrash && !n.isDeleted);
    const trash = this.notes.filter(n => n.isTrash && !n.isDeleted);

    const countNotes = document.getElementById('count-notes');
    const countPinned = document.getElementById('count-pinned');
    const countVault = document.getElementById('count-vault');
    const countArchive = document.getElementById('count-archive');
    const countTrash = document.getElementById('count-trash');

    if (countNotes) countNotes.textContent = active.length.toString();
    if (countPinned) countPinned.textContent = pinned.length.toString();
    if (countVault) countVault.textContent = vaultManager.isUnlocked() ? vault.length.toString() : '—';
    if (countArchive) countArchive.textContent = archive.length.toString();
    if (countTrash) countTrash.textContent = trash.length.toString();

    const allTags = new Set<string>();
    this.notes.forEach(n => {
      if (!n.isTrash && !n.isDeleted && (!n.isLocked || vaultManager.isUnlocked())) {
        n.tags.forEach(t => allTags.add(t));
      }
    });

    const tagsBar = document.getElementById('tags-filter-bar');
    if (tagsBar) {
      if (allTags.size > 0) {
        tagsBar.style.display = 'flex';
        tagsBar.innerHTML = Array.from(allTags).map(t => `
          <button type="button" class="tag-chip ${this.selectedTag === t ? 'active' : ''}" data-tag="${t}">
            #${t}
          </button>
        `).join('');

        tagsBar.querySelectorAll('.tag-chip').forEach(chip => {
          chip.addEventListener('click', () => {
            const tag = chip.getAttribute('data-tag');
            this.selectedTag = this.selectedTag === tag ? null : tag;
            this.render();
          });
        });
      } else {
        tagsBar.style.display = 'none';
      }
    }
  }

  private updateSyncUI(status: SyncStatus): void {
    const dot = this.appEl.querySelector('#sync-pill-indicator .sync-dot');
    const label = document.getElementById('sync-text-label');
    const pill = document.getElementById('sync-pill-indicator');
    if (pill) {
      pill.title = `Sync Status: ${status}`;
    }
    if (dot) {
      dot.className = `sync-dot ${this.getSyncStatusClass()}`;
    }
    if (label) {
      label.textContent = this.getSyncStatusLabel();
    }
  }

  private getSyncStatusClass(): string {
    const s = syncEngine.getStatus();
    if (s === 'SYNCED') return 'synced';
    if (s === 'SYNCING') return 'syncing';
    if (s === 'OFFLINE_PENDING') return 'offline';
    return 'error';
  }

  private getSyncStatusLabel(): string {
    const s = syncEngine.getStatus();
    if (s === 'SYNCED') return 'Synced';
    if (s === 'SYNCING') return 'Syncing';
    if (s === 'OFFLINE_PENDING') return 'Offline';
    return 'Sync error';
  }

  private getColorDisplayHex(hex: string): string {
    if (hex === '#DEFAULT' || !hex) return 'var(--surface-2)';
    return hex;
  }

  private createNoteModel(title = '', content = ''): Note {
    return {
      id: crypto.randomUUID(),
      title,
      content,
      colorHex: '#DEFAULT',
      isPinned: false,
      isArchived: false,
      isLocked: false,
      isTrash: false,
      tags: [],
      imageUrls: [],
      reminderTime: null,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      revision: 1,
      deviceId: 'web',
      isDeleted: false,
      isSynced: false
    };
  }

  private async renderVaultAuthScreen(container: HTMLElement): Promise<void> {
    const hasVault = await vaultManager.checkVaultExists();

    container.innerHTML = `
      <div style="text-align: center;">
        <div style="color: var(--primary); margin: 0 auto 0.75rem;">
          ${getIconSvg('lock', 32)}
        </div>
        <h2 style="font-size: 1.25rem; font-weight: 600; color: var(--foreground); margin-bottom: 0.35rem;">
          ${hasVault ? 'Unlock Private Vault' : 'Set Up Private Vault'}
        </h2>
        <p style="font-size: 0.85rem; color: var(--muted-foreground); margin-bottom: 1.5rem; line-height: 1.5;">
          ${hasVault 
            ? 'Enter your private vault password to decrypt your locked notes.' 
            : 'Create a private vault password to encrypt your secret notes on Android and Web.'}
        </p>

        <div class="field-block" style="text-align: left; margin-bottom: 1rem;">
          <label class="field-label">${hasVault ? 'Vault Password' : 'New Vault Password'}</label>
          <input type="password" id="vault-pass-input" class="input-base" placeholder="Enter password..." />
        </div>

        ${!hasVault ? `
          <div class="field-block" style="text-align: left; margin-bottom: 1rem;">
            <label class="field-label">Confirm Vault Password</label>
            <input type="password" id="vault-confirm-input" class="input-base" placeholder="Confirm password..." />
          </div>
        ` : ''}

        <button class="btn btn-primary" id="vault-auth-submit-btn" style="width: 100%; padding: 0.65rem 1rem; margin-top: 0.5rem;">
          ${getIconSvg(hasVault ? 'unlock' : 'check', 16)}
          <span>${hasVault ? 'Unlock Vault' : 'Create Vault'}</span>
        </button>

        <div id="vault-auth-error" style="color: var(--destructive); font-size: 0.85rem; margin-top: 0.75rem; text-align: center; font-weight: 500;"></div>
      </div>
    `;

    const passInput = document.getElementById('vault-pass-input') as HTMLInputElement;
    const confirmInput = document.getElementById('vault-confirm-input') as HTMLInputElement | null;
    const submitBtn = document.getElementById('vault-auth-submit-btn') as HTMLButtonElement;
    const errorEl = document.getElementById('vault-auth-error')!;

    const handleSubmit = async () => {
      const pass = passInput.value.trim();
      errorEl.textContent = '';

      if (hasVault) {
        if (!pass) {
          errorEl.textContent = 'Please enter your vault password.';
          return;
        }
        submitBtn.disabled = true;
        submitBtn.textContent = 'Unwrapping vault key...';
        try {
          await vaultManager.unlockVault(pass);
          this.render();
        } catch (err) {
          errorEl.textContent = (err as Error).message || 'Incorrect vault password.';
          submitBtn.disabled = false;
          submitBtn.textContent = 'Unlock Vault';
        }
      } else {
        if (pass.length < 4) {
          errorEl.textContent = 'Password must be at least 4 characters.';
          return;
        }
        const confirmPass = confirmInput?.value.trim() || '';
        if (pass !== confirmPass) {
          errorEl.textContent = 'Passwords do not match.';
          return;
        }
        submitBtn.disabled = true;
        submitBtn.textContent = 'Creating Vault...';
        try {
          await vaultManager.setupNewVault(pass);
          this.render();
        } catch (err) {
          errorEl.textContent = (err as Error).message || 'Failed to create vault.';
          submitBtn.disabled = false;
          submitBtn.textContent = 'Create Vault';
        }
      }
    };

    submitBtn.addEventListener('click', handleSubmit);
    passInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') handleSubmit();
    });
    confirmInput?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') handleSubmit();
    });
  }

  private openContextMenu(e: MouseEvent, note: Note): void {
    const mount = document.getElementById('context-mount')!;

    mount.innerHTML = `
      <div class="card-surface" style="position: fixed; left: ${e.clientX}px; top: ${e.clientY}px; z-index: 1000; padding: 0.35rem; min-width: 175px; box-shadow: 0 10px 25px rgba(0,0,0,0.5);">
        <div class="btn btn-ghost" data-action="pin" style="width: 100%; justify-content: flex-start; padding: 0.4rem 0.6rem; font-size: 0.8125rem;">
          ${getIconSvg(note.isPinned ? 'pin-off' : 'pin', 14)}
          <span>${note.isPinned ? 'Unpin' : 'Pin'}</span>
        </div>
        <div class="btn btn-ghost" data-action="vault" style="width: 100%; justify-content: flex-start; padding: 0.4rem 0.6rem; font-size: 0.8125rem;">
          ${getIconSvg(note.isLocked ? 'unlock' : 'lock', 14)}
          <span>${note.isLocked ? 'Unlock to Public' : 'Move to Vault'}</span>
        </div>
        <div class="btn btn-ghost" data-action="archive" style="width: 100%; justify-content: flex-start; padding: 0.4rem 0.6rem; font-size: 0.8125rem;">
          ${getIconSvg(note.isArchived ? 'archive-restore' : 'archive', 14)}
          <span>${note.isArchived ? 'Unarchive' : 'Archive'}</span>
        </div>
        <div class="btn btn-ghost" data-action="share" style="width: 100%; justify-content: flex-start; padding: 0.4rem 0.6rem; font-size: 0.8125rem;">
          ${getIconSvg('share', 14)}
          <span>Share Note</span>
        </div>
        <div class="btn btn-ghost btn-danger" data-action="trash" style="width: 100%; justify-content: flex-start; padding: 0.4rem 0.6rem; font-size: 0.8125rem;">
          ${getIconSvg('trash', 14)}
          <span>${note.isTrash ? 'Delete Forever' : 'Move to Trash'}</span>
        </div>
      </div>
    `;

    mount.querySelectorAll('[data-action]').forEach(item => {
      item.addEventListener('click', (ev) => {
        ev.stopPropagation();
        const act = item.getAttribute('data-action');
        this.handleContextMenuAction(act, note);
        this.closeContextMenu();
      });
    });
  }

  private async handleContextMenuAction(action: string | null, note: Note): Promise<void> {
    switch (action) {
      case 'pin':
        note.isPinned = !note.isPinned;
        note.updatedAt = Date.now();
        await syncEngine.uploadNote(note);
        this.render();
        break;
      case 'vault':
        if (note.isLocked) {
          if (!vaultManager.isUnlocked()) {
            this.showToast('Unlock your private vault first');
            this.currentDestination = 'VAULT';
            this.render();
            return;
          }
          note.isLocked = false;
        } else {
          const hasVault = await vaultManager.checkVaultExists();
          if (!hasVault) {
            this.showToast('Please set up your private vault first');
            this.currentDestination = 'VAULT';
            this.render();
            return;
          }
          if (!vaultManager.isUnlocked()) {
            this.showToast('Please unlock your private vault first');
            this.currentDestination = 'VAULT';
            this.render();
            return;
          }
          note.isLocked = true;
          note.isPinned = false;
        }
        note.updatedAt = Date.now();
        await syncEngine.uploadNote(note);
        this.render();
        break;
      case 'archive':
        note.isArchived = !note.isArchived;
        note.isPinned = false;
        note.updatedAt = Date.now();
        await syncEngine.uploadNote(note);
        this.render();
        break;
      case 'share':
        this.openShareModal(note);
        break;
      case 'trash':
        if (note.isTrash) {
          if (confirm('Permanently delete this note?')) {
            await syncEngine.deleteNote(note.id);
            this.notes = this.notes.filter(n => n.id !== note.id);
            if (this.activeNote?.id === note.id) {
              this.closeEditor();
            } else {
              this.render();
            }
          }
        } else {
          note.isTrash = true;
          note.isPinned = false;
          note.updatedAt = Date.now();
          await syncEngine.uploadNote(note);
          if (this.activeNote?.id === note.id) {
            this.closeEditor();
          } else {
            this.render();
          }
        }
        break;
    }
  }

  private closeContextMenu(): void {
    const mount = document.getElementById('context-mount');
    if (mount) mount.innerHTML = '';
  }

  private openShareModal(note: Note): void {
    const mount = document.getElementById('modal-mount')!;
    mount.innerHTML = `
      <div class="modal-backdrop" id="share-modal">
        <div class="modal-dialog" style="max-width: 480px;">
          <div style="display: flex; align-items: center; justify-content: space-between;">
            <h3 class="modal-title">Share Note</h3>
            <button class="btn btn-ghost btn-icon-sm" id="modal-close-btn">${getIconSvg('close', 16)}</button>
          </div>
          <p class="modal-description">
            Generate an encrypted read-only web link or export "<strong>${note.title || 'Untitled'}</strong>".
          </p>

          <div id="share-form">
            <div class="field-block" style="margin-bottom: 0.85rem;">
              <label class="field-label">Password Protection (Optional)</label>
              <input type="password" id="share-password" class="input-base" placeholder="Leave empty for public link, or set password..." />
            </div>

            <div class="field-block" style="margin-bottom: 1rem;">
              <label class="field-label">Link Expiration</label>
              <select id="share-expiry-select" class="input-base">
                <option value="never">Never (Does not expire)</option>
                <option value="1h">1 Hour</option>
                <option value="1d">1 Day</option>
                <option value="7d">7 Days</option>
              </select>
            </div>

            <button class="btn btn-primary" id="generate-share-btn" style="width: 100%; padding: 0.65rem 1rem; margin-bottom: 1.25rem;">
              ${getIconSvg('link', 16)}
              <span>Create Share Link</span>
            </button>

            <div style="border-top: 1px solid var(--border); padding-top: 1rem;">
              <div style="font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted-foreground); margin-bottom: 0.5rem;">Quick File Exports</div>
              <div style="display: flex; gap: 0.5rem;">
                <button type="button" class="btn btn-soft btn-sm" id="modal-export-md" style="flex: 1;">
                  ${getIconSvg('file', 14)} <span>Markdown</span>
                </button>
                <button type="button" class="btn btn-soft btn-sm" id="modal-export-html" style="flex: 1;">
                  ${getIconSvg('code', 14)} <span>HTML</span>
                </button>
                <button type="button" class="btn btn-soft btn-sm" id="modal-export-pdf" style="flex: 1;">
                  ${getIconSvg('printer', 14)} <span>PDF</span>
                </button>
              </div>
            </div>
          </div>

          <div id="share-result" style="display: none; margin-top: 0.5rem;">
            <div style="display: flex; gap: 0.5rem; align-items: center;">
              <input type="text" id="share-url-input" class="input-base" readonly style="font-family: 'JetBrains Mono', monospace; font-size: 0.8125rem;" />
              <button class="btn btn-primary btn-sm" id="copy-share-btn" title="Copy Link">${getIconSvg('copy', 14)} <span>Copy</span></button>
            </div>
            <div id="share-type-badge" style="font-size: 0.8rem; margin-top: 0.5rem; color: var(--primary); font-weight: 500;"></div>
          </div>
        </div>
      </div>
    `;

    document.getElementById('modal-close-btn')?.addEventListener('click', () => this.closeModal());

    document.getElementById('modal-export-md')?.addEventListener('click', () => exportAsMarkdown(note));
    document.getElementById('modal-export-html')?.addEventListener('click', () => exportAsHtml(note));
    document.getElementById('modal-export-pdf')?.addEventListener('click', () => exportAsPdf(note));

    const expirySelect = document.getElementById('share-expiry-select') as HTMLSelectElement;

    document.getElementById('generate-share-btn')?.addEventListener('click', async () => {
      const passInput = document.getElementById('share-password') as HTMLInputElement;
      const pass = passInput.value.trim();

      let expiresAt: number | null = null;
      const expChoice = expirySelect.value;
      if (expChoice === '1h') expiresAt = Date.now() + 3600 * 1000;
      else if (expChoice === '1d') expiresAt = Date.now() + 24 * 3600 * 1000;
      else if (expChoice === '7d') expiresAt = Date.now() + 7 * 24 * 3600 * 1000;

      const generateBtn = document.getElementById('generate-share-btn') as HTMLButtonElement;
      generateBtn.disabled = true;
      generateBtn.textContent = 'Generating...';

      try {
        const { shareUrl } = await createShare(note, pass, expiresAt);
        document.getElementById('share-form')!.style.display = 'none';
        const resultBox = document.getElementById('share-result')!;
        resultBox.style.display = 'block';
        const urlInput = document.getElementById('share-url-input') as HTMLInputElement;
        urlInput.value = shareUrl;

        const badge = document.getElementById('share-type-badge')!;
        badge.textContent = pass ? 'Protected: Password required for viewing.' : 'Public: Anyone with the link can view.';

        document.getElementById('copy-share-btn')?.addEventListener('click', () => {
          navigator.clipboard.writeText(shareUrl);
          this.showToast('Share link copied to clipboard');
        });
      } catch (err) {
        alert(`Failed to create share: ${(err as Error).message}`);
        generateBtn.disabled = false;
        generateBtn.textContent = 'Create Share Link';
      }
    });
  }

  private async renderShareReader(shareId: string): Promise<void> {
    this.appEl.innerHTML = `
      <div style="min-height: 100vh; width: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 1.5rem; overflow-y: auto;">
        <div class="card-surface modal-dialog" id="reader-auth-card" style="max-width: 440px; text-align: center;">
          <div style="color: var(--primary); margin: 0 auto 0.75rem;">${getIconSvg('lock', 32)}</div>
          <h2 style="font-size: 1.25rem; font-weight: 600; color: var(--foreground); margin-bottom: 0.35rem;">Shared Note</h2>
          <p id="reader-subtitle" style="font-size: 0.85rem; color: var(--muted-foreground); margin-bottom: 1.25rem; line-height: 1.5;">
            Loading note...
          </p>
          <div id="reader-pass-form" style="display: none;">
            <div class="field-block" style="margin-bottom: 1rem;">
              <input type="password" id="reader-password" class="input-base" placeholder="Enter share password..." />
            </div>
            <button class="btn btn-primary" id="reader-unlock-btn" style="width: 100%; padding: 0.65rem 1rem;">
              ${getIconSvg('unlock', 16)}
              <span>Unlock Note</span>
            </button>
          </div>
          <div id="reader-error" style="color: var(--destructive); font-size: 0.85rem; margin-top: 0.75rem; text-align: center; font-weight: 500;"></div>
        </div>

        <div id="reader-content-card" class="card-surface" style="display: none; width: 100%; max-width: 760px; padding: 2rem; margin: 2rem 0;">
          <div style="display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--border); padding-bottom: 1rem; margin-bottom: 1.5rem; gap: 1rem; flex-wrap: wrap;">
            <div>
              <span style="font-size: 0.7rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--primary); background: color-mix(in oklab, var(--primary) 15%, transparent); padding: 0.2rem 0.5rem; border-radius: 999px;">Read-Only</span>
              <h1 id="reader-title" style="font-size: 1.5rem; font-weight: 700; color: var(--foreground); margin-top: 0.35rem;"></h1>
            </div>
            <div style="display: flex; align-items: center; gap: 0.5rem;">
              <button class="btn btn-soft btn-sm" id="reader-copy-btn">
                ${getIconSvg('copy', 14)}
                <span>Copy Text</span>
              </button>
              <a href="#" class="btn btn-soft btn-sm" style="text-decoration: none;">
                ${getIconSvg('cloud', 14)}
                <span>Open Astral Notes</span>
              </a>
            </div>
          </div>
          <div id="reader-body" class="prose-note" style="line-height: 1.75;"></div>
        </div>
      </div>
    `;

    const authCard = document.getElementById('reader-auth-card')!;
    const subtitle = document.getElementById('reader-subtitle')!;
    const passForm = document.getElementById('reader-pass-form')!;
    const errEl = document.getElementById('reader-error')!;
    const contentCard = document.getElementById('reader-content-card')!;
    const readerTitle = document.getElementById('reader-title')!;
    const readerBody = document.getElementById('reader-body')!;

    try {
      const meta = await getSharedNoteMeta(shareId);

      if (meta.isDeleted) {
        subtitle.textContent = 'This shared note has been deleted by its author.';
        return;
      }

      if (meta.isExpired) {
        subtitle.textContent = 'This shared link has expired.';
        return;
      }

      if (!meta.isPasswordProtected) {
        const decrypted = await unlockSharedNote(shareId);
        authCard.style.display = 'none';
        contentCard.style.display = 'block';
        readerTitle.textContent = decrypted.title || 'Untitled Note';
        readerBody.innerHTML = renderMarkdown(decrypted.content);
        readerBody.querySelectorAll('.task-checkbox').forEach(cb => {
          (cb as HTMLInputElement).disabled = true;
        });
        document.getElementById('reader-copy-btn')?.addEventListener('click', () => {
          navigator.clipboard.writeText(`${decrypted.title}\n\n${decrypted.content}`);
          this.showToast('Note copied to clipboard');
        });
        return;
      }

      subtitle.textContent = 'This note is password-protected. Enter the password to unlock and read.';
      passForm.style.display = 'block';

      document.getElementById('reader-unlock-btn')?.addEventListener('click', async () => {
        const pass = (document.getElementById('reader-password') as HTMLInputElement).value;
        errEl.textContent = '';
        try {
          const decrypted = await unlockSharedNote(shareId, pass);
          authCard.style.display = 'none';
          contentCard.style.display = 'block';
          readerTitle.textContent = decrypted.title || 'Untitled Note';
          readerBody.innerHTML = renderMarkdown(decrypted.content);
          readerBody.querySelectorAll('.task-checkbox').forEach(cb => {
            (cb as HTMLInputElement).disabled = true;
          });
          document.getElementById('reader-copy-btn')?.addEventListener('click', () => {
            navigator.clipboard.writeText(`${decrypted.title}\n\n${decrypted.content}`);
            this.showToast('Note copied to clipboard');
          });
        } catch (err) {
          errEl.textContent = (err as Error).message;
        }
      });
    } catch (err) {
      subtitle.textContent = (err as Error).message;
    }
  }

  private async renderAnalyticsView(container: HTMLElement): Promise<void> {
    const active = this.notes.filter(n => !n.isTrash && !n.isDeleted && !n.isArchived && !n.isLocked);
    const pinned = this.notes.filter(n => n.isPinned && !n.isTrash && !n.isDeleted && !n.isArchived && !n.isLocked);
    const vault = this.notes.filter(n => n.isLocked && !n.isTrash && !n.isDeleted);
    const archive = this.notes.filter(n => n.isArchived && !n.isTrash && !n.isDeleted);
    const trash = this.notes.filter(n => n.isTrash && !n.isDeleted);

    let totalWords = 0;
    this.notes.forEach(n => {
      if (!n.isTrash && !n.isDeleted && (!n.isLocked || vaultManager.isUnlocked())) {
        totalWords += n.content.trim().split(/\s+/).filter(Boolean).length;
      }
    });

    const allTags = new Set<string>();
    this.notes.forEach(n => {
      if (!n.isTrash && !n.isDeleted && (!n.isLocked || vaultManager.isUnlocked())) {
        n.tags.forEach(t => allTags.add(t));
      }
    });

    const shares = await listUserShares();

    container.innerHTML = `
      <div style="max-width: 900px; margin: 0 auto;">
        <h2 style="font-size: 1.4rem; font-weight: 700; color: var(--foreground); margin-bottom: 1.5rem;">
          Analytics & Shared Links
        </h2>

        <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 1rem; margin-bottom: 2rem;">
          <div class="card-surface" style="padding: 1rem 1.25rem;">
            <span style="font-size: 1.6rem; font-weight: 700; color: var(--foreground); display: block;">${active.length}</span>
            <span style="font-size: 0.78rem; color: var(--muted-foreground);">Active Notes</span>
          </div>
          <div class="card-surface" style="padding: 1rem 1.25rem;">
            <span style="font-size: 1.6rem; font-weight: 700; color: var(--foreground); display: block;">${totalWords.toLocaleString()}</span>
            <span style="font-size: 0.78rem; color: var(--muted-foreground);">Total Words</span>
          </div>
          <div class="card-surface" style="padding: 1rem 1.25rem;">
            <span style="font-size: 1.6rem; font-weight: 700; color: var(--foreground); display: block;">${vaultManager.isUnlocked() ? vault.length : '—'}</span>
            <span style="font-size: 0.78rem; color: var(--muted-foreground);">Vault Notes ${vaultManager.isUnlocked() ? '(Unlocked)' : '(Locked)'}</span>
          </div>
          <div class="card-surface" style="padding: 1rem 1.25rem;">
            <span style="font-size: 1.6rem; font-weight: 700; color: var(--foreground); display: block;">${pinned.length}</span>
            <span style="font-size: 0.78rem; color: var(--muted-foreground);">Pinned Notes</span>
          </div>
          <div class="card-surface" style="padding: 1rem 1.25rem;">
            <span style="font-size: 1.6rem; font-weight: 700; color: var(--foreground); display: block;">${allTags.size}</span>
            <span style="font-size: 0.78rem; color: var(--muted-foreground);">Unique Tags</span>
          </div>
          <div class="card-surface" style="padding: 1rem 1.25rem;">
            <span style="font-size: 1.6rem; font-weight: 700; color: var(--foreground); display: block;">${archive.length}</span>
            <span style="font-size: 0.78rem; color: var(--muted-foreground);">Archived Notes</span>
          </div>
          <div class="card-surface" style="padding: 1rem 1.25rem;">
            <span style="font-size: 1.6rem; font-weight: 700; color: var(--foreground); display: block;">${trash.length}</span>
            <span style="font-size: 0.78rem; color: var(--muted-foreground);">Trash Notes</span>
          </div>
          <div class="card-surface" style="padding: 1rem 1.25rem;">
            <span style="font-size: 1.6rem; font-weight: 700; color: var(--foreground); display: block;">${shares.length}</span>
            <span style="font-size: 0.78rem; color: var(--muted-foreground);">Active Shares</span>
          </div>
        </div>

        <div class="card-surface" style="padding: 1.25rem;">
          <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 1rem; padding-bottom: 0.75rem; border-bottom: 1px solid var(--border);">
            <h3 style="font-size: 1.05rem; font-weight: 600; color: var(--foreground);">Shared Links</h3>
            <span style="font-size: 0.8rem; color: var(--muted-foreground);">${shares.length} links active</span>
          </div>

          <div>
            ${shares.length === 0 ? `
              <div style="padding: 2rem; text-align: center; color: var(--muted-foreground); font-size: 0.875rem;">
                No active shared links. Use the Share button on any note to generate web links.
              </div>
            ` : shares.map(s => {
              const isExpired = Boolean(s.expiresAt && Date.now() > s.expiresAt);
              const expiryStr = s.expiresAt
                ? (isExpired ? 'Expired' : `Expires ${new Date(s.expiresAt).toLocaleDateString()}`)
                : 'Never expires';
              const base = `${window.location.origin}${window.location.pathname}`;
              const shareUrl = `${base}#/share/${s.shareId}`;
              return `
                <div style="display: flex; align-items: center; justify-content: space-between; padding: 0.75rem 0; border-bottom: 1px solid var(--border); gap: 0.75rem;">
                  <div style="flex: 1; min-width: 0;">
                    <div style="font-weight: 600; font-size: 0.9rem; color: var(--foreground); overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                      ${s.title}
                    </div>
                    <div style="display: flex; gap: 0.75rem; font-size: 0.75rem; color: var(--muted-foreground); margin-top: 0.2rem;">
                      <span style="color: ${s.isPasswordProtected ? 'var(--primary)' : 'inherit'};">
                        ${s.isPasswordProtected ? 'Password Protected' : 'Public'}
                      </span>
                      <span>Created: ${new Date(s.createdAt).toLocaleDateString()}</span>
                      <span style="color: ${isExpired ? 'var(--destructive)' : 'inherit'};">
                        ${expiryStr}
                      </span>
                    </div>
                  </div>
                  <div style="display: flex; gap: 0.5rem;">
                    <button class="btn btn-soft btn-sm share-copy-btn" data-url="${shareUrl}">
                      ${getIconSvg('copy', 13)} <span>Copy</span>
                    </button>
                    <button class="btn btn-ghost btn-icon-sm share-revoke-btn" data-id="${s.shareId}" title="Revoke Link" style="color: var(--destructive);">
                      ${getIconSvg('trash', 14)}
                    </button>
                  </div>
                </div>
              `;
            }).join('')}
          </div>
        </div>
      </div>
    `;

    container.querySelectorAll('.share-copy-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const url = btn.getAttribute('data-url');
        if (url) {
          navigator.clipboard.writeText(url);
          this.showToast('Share link copied to clipboard');
        }
      });
    });

    container.querySelectorAll('.share-revoke-btn').forEach(btn => {
      btn.addEventListener('click', async () => {
        const shareId = btn.getAttribute('data-id');
        if (!shareId) return;
        if (confirm('Revoke this share link? Recipients will lose access.')) {
          await revokeShare(shareId);
          this.renderAnalyticsView(container);
        }
      });
    });
  }

  private renderBackupView(container: HTMLElement): void {
    container.innerHTML = `
      <div style="max-width: 900px; margin: 0 auto;">
        <h2 style="font-size: 1.4rem; font-weight: 700; color: var(--foreground); margin-bottom: 0.35rem;">
          Backup & Restore
        </h2>
        <p style="font-size: 0.875rem; color: var(--muted-foreground); margin-bottom: 1.75rem;">
          Export your complete notes library to a portable JSON backup, or restore a backup created on Android or Web.
        </p>

        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 1.25rem;">
          <div class="card-surface" style="padding: 1.5rem; display: flex; flex-direction: column;">
            <div style="display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.75rem;">
              <div style="padding: 0.5rem; border-radius: var(--radius-lg); background: color-mix(in oklab, var(--primary) 15%, transparent); color: var(--primary);">
                ${getIconSvg('download', 22)}
              </div>
              <h3 style="font-size: 1.1rem; font-weight: 600; color: var(--foreground);">Export Library</h3>
            </div>
            <p style="color: var(--muted-foreground); font-size: 0.875rem; line-height: 1.5; margin-bottom: 1.5rem; flex: 1;">
              Download a structured JSON backup of your notes, checklists, tags, and colors. If you have locked vault notes, you will be prompted for your vault password to decrypt them for export.
            </p>
            <button class="btn btn-primary" id="btn-export-backup" style="width: 100%; justify-content: center;">
              ${getIconSvg('download', 16)}
              <span>Export Library (.json)</span>
            </button>
          </div>

          <div class="card-surface" style="padding: 1.5rem; display: flex; flex-direction: column;">
            <div style="display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.75rem;">
              <div style="padding: 0.5rem; border-radius: var(--radius-lg); background: color-mix(in oklab, var(--success) 15%, transparent); color: var(--success);">
                ${getIconSvg('upload', 22)}
              </div>
              <h3 style="font-size: 1.1rem; font-weight: 600; color: var(--foreground);">Import Library</h3>
            </div>
            <p style="color: var(--muted-foreground); font-size: 0.875rem; line-height: 1.5; margin-bottom: 1.5rem; flex: 1;">
              Restore an AstralNotes backup file (.json) from your computer or Android phone. Vault notes inside the backup will be unlocked with their backup password and added to your library.
            </p>
            <input type="file" id="backup-file-input" accept=".json,application/json" style="display: none;" />
            <button class="btn btn-soft" id="btn-import-backup" style="width: 100%; justify-content: center;">
              ${getIconSvg('upload', 16)}
              <span>Select Backup File to Import</span>
            </button>
          </div>
        </div>
      </div>
    `;

    document.getElementById('btn-export-backup')?.addEventListener('click', async () => {
      const allNotes: Note[] = await getLocalNotes();
      const hasVaultNotes = allNotes.some((n: Note) => n.isLocked);

      if (hasVaultNotes) {
        this.showPromptModal({
          title: 'Vault Password Required',
          message: 'Your library contains locked vault notes. Enter your current vault password to decrypt and package them into the backup file:',
          isPassword: true,
          confirmLabel: 'Export Backup',
          onConfirm: async (password) => {
            if (!password || !password.trim()) {
              this.showToast('Vault password is required for export');
              return;
            }
            try {
              const count = await exportLibrary(password);
              this.showToast(`Successfully exported ${count} notes to backup file!`);
            } catch (err: any) {
              this.showToast(`Export failed: ${err.message}`);
            }
          }
        });
      } else {
        try {
          const count = await exportLibrary();
          this.showToast(`Successfully exported ${count} notes to backup file!`);
        } catch (err: any) {
          this.showToast(`Export failed: ${err.message}`);
        }
      }
    });

    const fileInput = document.getElementById('backup-file-input') as HTMLInputElement;
    document.getElementById('btn-import-backup')?.addEventListener('click', () => {
      if (fileInput) {
        fileInput.value = '';
        fileInput.click();
      }
    });

    fileInput?.addEventListener('change', async () => {
      const file = fileInput.files?.[0];
      if (!file) return;

      try {
        const text = await file.text();
        const inspection = inspectBackup(text);

        if (inspection.vaultCount > 0) {
          this.showPromptModal({
            title: 'Imported Vault Password',
            message: `This backup contains ${inspection.vaultCount} locked vault notes. Enter the vault password of the imported backup file to unlock and restore them:`,
            isPassword: true,
            confirmLabel: 'Unlock & Import',
            onConfirm: async (importedPassword) => {
              if (!importedPassword || !importedPassword.trim()) {
                this.showToast('Password is required for imported vault notes');
                return;
              }
              try {
                const res = await importLibrary(text, importedPassword);
                this.notes = await getLocalNotes();
                this.render();
                this.showToast(`Imported ${res.regularImported} regular notes and ${res.vaultImported} vault notes!`);
              } catch (err: any) {
                this.showToast(`Import failed: ${err.message}`);
              }
            }
          });
        } else {
          const res = await importLibrary(text);
          this.notes = await getLocalNotes();
          this.render();
          this.showToast(`Imported ${res.regularImported} notes successfully!`);
        }
      } catch (err) {
        this.showToast(`Failed to parse backup file: ${(err as Error).message}`);
      }
    });
  }

  private showPromptModal(options: {
    title: string;
    message: string;
    isPassword?: boolean;
    confirmLabel?: string;
    onConfirm: (val: string) => void | Promise<void>;
  }): void {
    const mount = document.getElementById('modal-mount');
    if (!mount) return;

    mount.innerHTML = `
      <div class="modal-backdrop">
        <div class="modal-dialog" style="max-width: 440px;">
          <div style="display: flex; align-items: center; justify-content: space-between;">
            <h3 class="modal-title">${options.title}</h3>
            <button class="btn btn-ghost btn-icon-sm" id="prompt-modal-close">${getIconSvg('close', 16)}</button>
          </div>
          <p class="modal-description">${options.message}</p>
          <div class="field-block" style="margin-bottom: 0.5rem;">
            <input type="${options.isPassword ? 'password' : 'text'}" id="prompt-modal-input" class="input-base" placeholder="Enter password..." />
          </div>
          <div style="display: flex; gap: 0.5rem; justify-content: flex-end; margin-top: 0.5rem;">
            <button class="btn btn-soft" id="prompt-modal-cancel">Cancel</button>
            <button class="btn btn-primary" id="prompt-modal-confirm">${options.confirmLabel || 'Confirm'}</button>
          </div>
        </div>
      </div>
    `;

    const close = () => this.closeModal();
    document.getElementById('prompt-modal-close')?.addEventListener('click', close);
    document.getElementById('prompt-modal-cancel')?.addEventListener('click', close);
    const input = document.getElementById('prompt-modal-input') as HTMLInputElement;
    input?.focus();

    document.getElementById('prompt-modal-confirm')?.addEventListener('click', async () => {
      const val = input ? input.value : '';
      close();
      await options.onConfirm(val);
    });

    input?.addEventListener('keydown', async (e) => {
      if (e.key === 'Enter') {
        const val = input.value;
        close();
        await options.onConfirm(val);
      }
    });
  }

  private showToast(msg: string): void {
    let container = document.querySelector('.toast-container') as HTMLElement | null;
    if (!container) {
      container = document.createElement('div');
      container.className = 'toast-container';
      document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = 'toast-item';
    toast.textContent = msg;
    container.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      toast.style.transition = 'opacity 200ms ease, transform 200ms ease';
      setTimeout(() => toast.remove(), 250);
    }, 3200);
  }

  private closeModal(): void {
    const mount = document.getElementById('modal-mount');
    if (mount) mount.innerHTML = '';
  }
}

new AstralNotesApp();
