import './style.css';
import { auth, googleProvider, signInWithPopup, signOut, onAuthStateChanged, type User } from './firebase';
import type { Note, DrawerDestination, SyncStatus } from './types';
import { getLocalNotes } from './db';
import { syncEngine } from './sync';
import { renderMarkdown, toggleChecklistInMarkdown } from './markdown';
import { getIconSvg } from './icons';
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
  private isGridView = true;
  private currentUser: User | null = null;
  private activeNote: Note | null = null;
  private editorMode: 'edit' | 'preview' = 'edit';

  private appEl: HTMLElement;

  constructor() {
    this.appEl = document.getElementById('app')!;
    this.init();
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
        if (this.currentUser) this.openEditor();
      } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'f') {
        e.preventDefault();
        const searchInput = document.getElementById('search-input') as HTMLInputElement | null;
        searchInput?.focus();
      } else if (e.key === 'Escape') {
        this.closeEditor();
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

    this.appEl.innerHTML = `
      <aside class="sidebar" id="sidebar">
        <div class="sidebar-header">
          <div class="brand-icon">${getIconSvg('cloud', 22)}</div>
          <span class="brand-title">Astral Notes</span>
        </div>

        <ul class="nav-links">
          <li class="nav-item ${this.currentDestination === 'NOTES' && !this.selectedTag ? 'active' : ''}" data-dest="NOTES">
            ${getIconSvg('file', 18)}
            <span>All Notes</span>
            <span class="nav-item-count" id="count-notes">0</span>
          </li>
          <li class="nav-item ${this.currentDestination === 'PINNED' ? 'active' : ''}" data-dest="PINNED">
            ${getIconSvg('pin', 18)}
            <span>Pinned</span>
            <span class="nav-item-count" id="count-pinned">0</span>
          </li>
          <li class="nav-item ${this.currentDestination === 'VAULT' ? 'active' : ''}" data-dest="VAULT">
            ${getIconSvg(isVaultUnlocked ? 'unlock' : 'lock', 18)}
            <span>Vault ${isVaultUnlocked ? '(Open)' : ''}</span>
            <span class="nav-item-count" id="count-vault">${isVaultUnlocked ? '0' : '—'}</span>
          </li>
          <li class="nav-item ${this.currentDestination === 'ARCHIVE' ? 'active' : ''}" data-dest="ARCHIVE">
            ${getIconSvg('archive', 18)}
            <span>Archive</span>
            <span class="nav-item-count" id="count-archive">0</span>
          </li>
          <li class="nav-item ${this.currentDestination === 'TRASH' ? 'active' : ''}" data-dest="TRASH">
            ${getIconSvg('trash', 18)}
            <span>Trash</span>
            <span class="nav-item-count" id="count-trash">0</span>
          </li>
          <li class="nav-item ${this.currentDestination === 'ANALYTICS' ? 'active' : ''}" data-dest="ANALYTICS">
            ${getIconSvg('analytics', 18)}
            <span>Analytics & Shares</span>
          </li>
          <li class="nav-item ${this.currentDestination === 'BACKUP' ? 'active' : ''}" data-dest="BACKUP">
            ${getIconSvg('download', 18)}
            <span>Backup & Restore</span>
          </li>

          <div class="nav-section-title">Tags</div>
          <div class="tags-list" id="sidebar-tags"></div>
        </ul>

        <div class="sidebar-footer" id="sidebar-footer"></div>
      </aside>

      <main class="main-view">
        <header class="top-bar">
          <button class="btn-icon" id="toggle-sidebar" title="Toggle Sidebar">
            ${getIconSvg('menu', 20)}
          </button>

          <div class="search-container">
            <span class="search-icon">${getIconSvg('search', 18)}</span>
            <input type="text" id="search-input" class="search-input" placeholder="Search notes, tags, content... (Ctrl+F)" value="${this.searchQuery}" />
          </div>

          <div class="top-actions">
            ${isVaultSection && isVaultUnlocked ? `
              <button class="btn btn-secondary" id="relock-vault-btn" title="Relock Private Vault">
                ${getIconSvg('lock', 16)}
                <span>Relock Vault</span>
              </button>
            ` : ''}
            <button class="btn-icon" id="toggle-layout" title="Toggle Grid/List">
              ${getIconSvg(this.isGridView ? 'list-view' : 'grid', 20)}
            </button>
            <button class="btn btn-secondary" id="import-btn" title="Import Markdown or Text">
              ${getIconSvg('upload', 18)}
              <span>Import</span>
            </button>
            <input type="file" id="file-importer" style="display: none;" accept=".md,.txt,text/plain,text/markdown" />
            <button class="btn btn-primary" id="new-note-btn">
              ${getIconSvg('plus', 18)}
              <span>New Note</span>
            </button>
          </div>
        </header>

        <div class="filter-bar-wrap" id="filter-bar-wrap">
          <div class="filter-chip ${this.currentDestination === 'NOTES' && !this.selectedTag ? 'active' : ''}" data-dest="NOTES">
            ${getIconSvg('file', 14)}
            <span>All Notes</span>
          </div>
          <div class="filter-chip ${this.currentDestination === 'PINNED' ? 'active' : ''}" data-dest="PINNED">
            ${getIconSvg('pin', 14)}
            <span>Pinned</span>
          </div>
          <div class="filter-chip ${this.currentDestination === 'VAULT' ? 'active' : ''}" data-dest="VAULT">
            ${getIconSvg(isVaultUnlocked ? 'unlock' : 'lock', 14)}
            <span>Vault ${isVaultUnlocked ? '(Open)' : ''}</span>
          </div>
          <div class="filter-chip ${this.currentDestination === 'ARCHIVE' ? 'active' : ''}" data-dest="ARCHIVE">
            ${getIconSvg('archive', 14)}
            <span>Archive</span>
          </div>
          <div class="filter-chip ${this.currentDestination === 'TRASH' ? 'active' : ''}" data-dest="TRASH">
            ${getIconSvg('trash', 14)}
            <span>Trash</span>
          </div>
          <div id="filter-chips-tags" style="display: flex; gap: 8px;"></div>
        </div>

        <section class="notes-container" id="notes-container"></section>

        <nav class="mobile-bottom-dock">
          <button class="dock-btn dock-btn-primary" id="dock-new-note" title="New Note">
            ${getIconSvg('plus', 16)}
            <span>New Note</span>
          </button>
          <button class="dock-btn" id="dock-new-task" title="New Checklist">
            ${getIconSvg('check-square', 16)}
            <span>Checklist</span>
          </button>
          <button class="dock-btn" id="dock-new-image" title="New Image Note">
            ${getIconSvg('image', 16)}
            <span>Image</span>
          </button>
        </nav>
      </main>

      <div id="editor-mount"></div>
      <div id="modal-mount"></div>
      <div id="context-mount"></div>
    `;

    this.bindEvents();
    this.renderUserSection();
    this.renderNotesList();
    this.renderNavCounts();
  }

  private renderSignInGate(): void {
    this.appEl.innerHTML = `
      <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; width: 100vw; padding: 24px; background: radial-gradient(circle at 50% 30%, rgba(240, 120, 138, 0.12) 0%, transparent 60%), var(--bg-dark);">
        <div class="modal-card" style="max-width: 440px; text-align: center; border: 1px solid var(--border-active); box-shadow: var(--shadow-glass), var(--shadow-glow); padding: 36px 32px;">
          <div class="brand-icon" style="margin: 0 auto 20px; width: 56px; height: 56px; border-radius: var(--radius-lg);">
            ${getIconSvg('cloud', 30)}
          </div>
          <h1 style="font-family: var(--font-display); font-size: 1.6rem; font-weight: 700; color: var(--text-ink); margin-bottom: 10px;">
            Astral Notes
          </h1>
          <p style="font-size: 0.92rem; color: var(--text-secondary); margin-bottom: 28px; line-height: 1.6;">
            Sign in with your Google account to synchronize your notes and private locked vault across Android and Web.
          </p>
          <button class="btn btn-primary" id="gate-signin-btn" style="width: 100%; padding: 13px 20px; font-size: 0.95rem;">
            ${getIconSvg('log-in', 18)}
            <span>Sign In with Google</span>
          </button>
          <div id="gate-signin-error" style="color: var(--danger); font-size: 0.85rem; margin-top: 14px; font-weight: 600;"></div>
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

  private bindEvents(): void {
    this.appEl.querySelectorAll('.nav-item').forEach((item) => {
      item.addEventListener('click', () => {
        const dest = item.getAttribute('data-dest') as DrawerDestination;
        if (dest) {
          this.currentDestination = dest;
          this.selectedTag = null;
          this.render();
        }
      });
    });

    this.appEl.querySelectorAll('.filter-chip').forEach((chip) => {
      chip.addEventListener('click', () => {
        const dest = chip.getAttribute('data-dest') as DrawerDestination;
        if (dest) {
          this.currentDestination = dest;
          this.selectedTag = null;
          this.render();
        }
      });
    });

    const searchInput = document.getElementById('search-input') as HTMLInputElement;
    searchInput?.addEventListener('input', (e) => {
      this.searchQuery = (e.target as HTMLInputElement).value;
      this.renderNotesList();
    });

    document.getElementById('toggle-layout')?.addEventListener('click', () => {
      this.isGridView = !this.isGridView;
      this.render();
    });

    document.getElementById('relock-vault-btn')?.addEventListener('click', () => {
      vaultManager.lockVault();
    });

    document.getElementById('new-note-btn')?.addEventListener('click', () => {
      this.openEditor();
    });
    document.getElementById('dock-new-note')?.addEventListener('click', () => {
      this.openEditor();
    });
    document.getElementById('dock-new-task')?.addEventListener('click', () => {
      const taskNote = this.createNoteModel('Tasks', '- [ ] ');
      if (this.currentDestination === 'VAULT') taskNote.isLocked = true;
      this.openEditor(taskNote);
    });
    document.getElementById('dock-new-image')?.addEventListener('click', () => {
      const url = prompt('Enter Image URL:');
      if (url) {
        const imgNote = this.createNoteModel('Image Note', `![Image](${url.trim()})\n\n`);
        if (this.currentDestination === 'VAULT') imgNote.isLocked = true;
        this.openEditor(imgNote);
      }
    });

    const fileImporter = document.getElementById('file-importer') as HTMLInputElement;
    document.getElementById('import-btn')?.addEventListener('click', () => {
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
      this.openEditor(newNote);
    });

    document.getElementById('toggle-sidebar')?.addEventListener('click', () => {
      const sidebar = document.getElementById('sidebar');
      if (window.innerWidth <= 820) {
        sidebar?.classList.toggle('open');
      } else {
        sidebar?.classList.toggle('collapsed');
      }
    });
  }

  private renderUserSection(): void {
    const footer = document.getElementById('sidebar-footer');
    if (!footer) return;

    if (this.currentUser) {
      const photo = this.currentUser.photoURL;
      const name = this.currentUser.displayName || this.currentUser.email || 'Astral Explorer';
      const statusClass = this.getSyncStatusClass();

      footer.innerHTML = `
        <div class="user-avatar-wrap">
          ${photo ? `<img src="${photo}" class="user-avatar-img" alt="${name}" />` : getIconSvg('shield', 36)}
          <span class="sync-badge ${statusClass}" id="sync-badge" title="Sync Status: ${syncEngine.getStatus()}"></span>
        </div>
        <div class="user-info">
          <span class="user-name">${name}</span>
          <span class="sync-label" id="sync-label">${this.getSyncStatusLabel()}</span>
        </div>
        <button class="btn-icon" id="auth-btn" title="Sign Out">
          ${getIconSvg('log-out', 18)}
        </button>
      `;

      document.getElementById('auth-btn')?.addEventListener('click', async () => {
        vaultManager.lockVault();
        await signOut(auth);
      });
    }
  }

  private updateSyncUI(status: SyncStatus): void {
    const badge = document.getElementById('sync-badge');
    const label = document.getElementById('sync-label');
    if (badge) {
      badge.className = `sync-badge ${this.getSyncStatusClass()}`;
      badge.title = `Sync Status: ${status}`;
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
    if (s === 'SYNCED') return 'Encrypted & Synced';
    if (s === 'SYNCING') return 'Syncing...';
    if (s === 'OFFLINE_PENDING') return 'Offline Cache';
    return 'Sync Warning';
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
    const container = document.getElementById('notes-container');
    if (!container) return;

    if (this.currentDestination === 'ANALYTICS') {
      this.renderAnalyticsView(container);
      return;
    }

    if (this.currentDestination === 'BACKUP') {
      this.renderBackupView(container);
      return;
    }

    if (this.currentDestination === 'VAULT' && !vaultManager.isUnlocked()) {
      this.renderVaultAuthScreen(container);
      return;
    }

    const filtered = this.getFilteredNotes();

    if (filtered.length === 0) {
      container.innerHTML = `
        <div class="empty-state">
          <div class="empty-state-icon">${getIconSvg('file', 54)}</div>
          <div class="empty-state-title">No notes found</div>
          <p>Create a note or change your filters to get started.</p>
        </div>
      `;
      return;
    }

    const pinnedNotes = filtered.filter(n => n.isPinned && this.currentDestination === 'NOTES');
    const otherNotes = filtered.filter(n => !n.isPinned || this.currentDestination !== 'NOTES');

    let html = '';

    if (pinnedNotes.length > 0) {
      html += `
        <div class="section-header">${getIconSvg('pin', 14)} Pinned</div>
        <div class="notes-grid ${this.isGridView ? '' : 'list-layout'}">
          ${pinnedNotes.map(n => this.renderNoteCardHtml(n)).join('')}
        </div>
      `;
    }

    if (otherNotes.length > 0) {
      if (pinnedNotes.length > 0) {
        html += `<div class="section-header">${this.currentDestination === 'VAULT' ? 'Protected Vault Notes' : 'Others'}</div>`;
      }
      html += `
        <div class="notes-grid ${this.isGridView ? '' : 'list-layout'}">
          ${otherNotes.map(n => this.renderNoteCardHtml(n)).join('')}
        </div>
      `;
    }

    container.innerHTML = html;

    container.querySelectorAll('.note-card').forEach((card) => {
      const id = card.getAttribute('data-id');
      const note = this.notes.find(n => n.id === id);
      if (!note) return;

      card.addEventListener('click', (e) => {
        if ((e.target as HTMLElement).closest('.pin-btn')) {
          e.stopPropagation();
          this.togglePin(note);
          return;
        }
        this.openEditor(note);
      });

      card.addEventListener('contextmenu', (e) => {
        e.preventDefault();
        this.openContextMenu(e as MouseEvent, note);
      });
    });
  }

  private async renderVaultAuthScreen(container: HTMLElement): Promise<void> {
    const hasVault = await vaultManager.checkVaultExists();

    container.innerHTML = `
      <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 60vh; padding: 20px;">
        <div class="modal-card" style="max-width: 450px;">
          <div class="brand-icon" style="margin: 0 auto 16px; width: 50px; height: 50px;">
            ${getIconSvg('lock', 26)}
          </div>
          <h2 style="text-align: center; margin-bottom: 8px; font-size: 1.4rem; font-family: var(--font-display);">
            ${hasVault ? 'Unlock Private Vault' : 'Set Up Private Vault'}
          </h2>
          <p style="text-align: center; font-size: 0.9rem; color: var(--text-secondary); margin-bottom: 22px; line-height: 1.6;">
            ${hasVault 
              ? 'Enter your separate private vault password to decrypt and read your hidden notes.' 
              : 'Create a private vault password to encrypt your hidden notes on Android and Web.'}
          </p>

          <div class="form-group">
            <label class="form-label">${hasVault ? 'Vault Password' : 'New Vault Password'}</label>
            <input type="password" id="vault-pass-input" class="form-input" placeholder="Enter password..." />
          </div>

          ${!hasVault ? `
            <div class="form-group">
              <label class="form-label">Confirm Vault Password</label>
              <input type="password" id="vault-confirm-input" class="form-input" placeholder="Confirm password..." />
            </div>
          ` : ''}

          <button class="btn btn-primary" id="vault-auth-submit-btn" style="width: 100%; margin-top: 8px; padding: 12px;">
            ${getIconSvg(hasVault ? 'unlock' : 'check', 16)}
            <span>${hasVault ? 'Unlock Vault' : 'Create Vault'}</span>
          </button>

          <div id="vault-auth-error" style="color: var(--danger); font-size: 0.88rem; margin-top: 14px; text-align: center; font-weight: 600;"></div>
        </div>
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
        submitBtn.textContent = 'Verifying and unwrapping...';
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
        submitBtn.textContent = 'Encrypting & Creating Vault...';
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

  private renderNoteCardHtml(note: Note): string {
    const formattedDate = new Date(note.updatedAt).toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric'
    });

    const borderStyle = note.colorHex && note.colorHex !== '#DEFAULT'
      ? `style="border-left: 4px solid ${note.colorHex};"`
      : '';

    return `
      <article class="note-card" data-id="${note.id}" ${borderStyle}>
        <div class="note-card-header">
          <h3 class="note-card-title">${note.title || 'Untitled'}</h3>
          <button class="pin-btn ${note.isPinned ? 'active' : ''}" title="${note.isPinned ? 'Unpin' : 'Pin'}">
            ${getIconSvg('pin', 16)}
          </button>
        </div>
        <div class="note-card-snippet markdown-preview">${renderMarkdown(note.content) || 'Empty note...'}</div>
        ${note.tags.length > 0 ? `
          <div class="note-card-tags">
            ${note.tags.map(t => `<span class="tag-chip">#${t}</span>`).join('')}
          </div>
        ` : ''}
        <div class="note-card-footer">
          <span>${formattedDate}</span>
          ${note.isLocked ? getIconSvg('lock', 14) : ''}
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
      if (!n.isTrash && !n.isDeleted) {
        n.tags.forEach(t => allTags.add(t));
      }
    });

    const tagsContainer = document.getElementById('sidebar-tags');
    if (tagsContainer) {
      tagsContainer.innerHTML = Array.from(allTags).map(t => `
        <li class="nav-item ${this.selectedTag === t ? 'active' : ''}" data-tag="${t}">
          ${getIconSvg('tag', 16)}
          <span>#${t}</span>
        </li>
      `).join('');

      tagsContainer.querySelectorAll('.nav-item').forEach(item => {
        item.addEventListener('click', () => {
          const tag = item.getAttribute('data-tag');
          this.selectedTag = this.selectedTag === tag ? null : tag;
          this.render();
        });
      });
    }

    const filterChipsTags = document.getElementById('filter-chips-tags');
    if (filterChipsTags) {
      filterChipsTags.innerHTML = Array.from(allTags).map(t => `
        <div class="filter-chip ${this.selectedTag === t ? 'active' : ''}" data-tag="${t}">
          ${getIconSvg('tag', 14)}
          <span>#${t}</span>
        </div>
      `).join('');

      filterChipsTags.querySelectorAll('.filter-chip').forEach(chip => {
        chip.addEventListener('click', () => {
          const tag = chip.getAttribute('data-tag');
          this.selectedTag = this.selectedTag === tag ? null : tag;
          this.render();
        });
      });
    }
  }

  private getColorDisplayHex(hex: string): string {
    if (hex === '#DEFAULT' || !hex) return '#3d2e30';
    return hex;
  }

  private getColorName(hex: string): string {
    const c = NOTE_COLORS.find(item => item.hex === hex);
    return c ? c.name : 'Default';
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

  private openEditor(note?: Note): void {
    const isNew = !note;
    this.activeNote = note ? { ...note } : this.createNoteModel();
    this.editorMode = note ? 'preview' : 'edit';
    if (isNew && this.currentDestination === 'VAULT') {
      this.activeNote.isLocked = true;
    }

    const mount = document.getElementById('editor-mount')!;
    mount.innerHTML = `
      <div class="editor-modal" id="editor-modal">
        <div class="editor-surface ${this.editorMode === 'preview' ? 'mode-preview' : ''}" id="editor-surface">
          <div class="editor-header">
            <input type="text" id="editor-title" class="editor-title-input" placeholder="Note Title..." value="${this.activeNote.title}" />
            <div style="display: flex; align-items: center; gap: 8px;">
              <div class="color-picker-wrap" style="position: relative;">
                <button type="button" class="btn btn-secondary" id="editor-color-trigger" style="padding: 7px 12px; font-size: 0.82rem; display: flex; align-items: center; gap: 8px;">
                  <span id="editor-color-dot" style="width: 12px; height: 12px; border-radius: 50%; background: ${this.getColorDisplayHex(this.activeNote.colorHex)}; border: 1px solid rgba(255,255,255,0.3); display: inline-block;"></span>
                  <span id="editor-color-label">${this.getColorName(this.activeNote.colorHex)}</span>
                  ${getIconSvg('chevron-down', 12)}
                </button>
                <div id="editor-color-menu" style="display: none; position: absolute; top: calc(100% + 6px); left: 0; background: #22191a; border: 1px solid rgba(240, 120, 138, 0.25); border-radius: 12px; padding: 6px; z-index: 1100; box-shadow: 0 10px 30px rgba(0,0,0,0.6); width: 145px;">
                  ${NOTE_COLORS.map(c => `
                    <div class="editor-color-item" data-hex="${c.hex}" style="display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 8px; cursor: pointer; font-size: 0.82rem; color: #fff3e0; transition: background 0.15s;">
                      <span style="width: 12px; height: 12px; border-radius: 50%; background: ${c.hex === '#DEFAULT' ? '#3d2e30' : c.hex}; border: 1px solid rgba(255,255,255,0.3); display: inline-block;"></span>
                      <span>${c.name}</span>
                    </div>
                  `).join('')}
                </div>
              </div>

              <button type="button" class="btn btn-secondary" id="editor-mode-toggle" title="Toggle Preview / Edit Mode">
                ${getIconSvg(this.editorMode === 'preview' ? 'edit' : 'eye', 16)}
                <span id="editor-mode-label">${this.editorMode === 'preview' ? 'Edit' : 'Preview'}</span>
              </button>

              <div style="position: relative;">
                <button type="button" class="btn btn-secondary" id="editor-export-trigger" title="Export Note" style="display: flex; align-items: center; gap: 6px;">
                  ${getIconSvg('download', 16)}
                  <span>Export</span>
                  ${getIconSvg('chevron-down', 12)}
                </button>
                <div id="editor-export-menu" style="display: none; position: absolute; top: calc(100% + 6px); right: 0; background: #22191a; border: 1px solid rgba(240, 120, 138, 0.25); border-radius: 12px; padding: 6px; z-index: 1100; box-shadow: 0 10px 30px rgba(0,0,0,0.6); width: 165px;">
                  <div class="editor-export-item" data-format="md" style="display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 8px; cursor: pointer; font-size: 0.82rem; color: #fff3e0; transition: background 0.15s;">
                    ${getIconSvg('file', 14)} <span>Markdown (.md)</span>
                  </div>
                  <div class="editor-export-item" data-format="html" style="display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 8px; cursor: pointer; font-size: 0.82rem; color: #fff3e0; transition: background 0.15s;">
                    ${getIconSvg('code', 14)} <span>HTML (.html)</span>
                  </div>
                  <div class="editor-export-item" data-format="pdf" style="display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 8px; cursor: pointer; font-size: 0.82rem; color: #fff3e0; transition: background 0.15s;">
                    ${getIconSvg('printer', 14)} <span>Print / PDF</span>
                  </div>
                </div>
              </div>

              <button class="btn btn-secondary" id="editor-share-btn">
                ${getIconSvg('share', 16)}
                <span>Share</span>
              </button>
              <button class="btn btn-primary" id="editor-save-btn">
                ${getIconSvg('check', 16)}
                <span>Save</span>
              </button>
              <button class="btn-icon" id="editor-close-btn" title="Close (Esc)">
                ${getIconSvg('close', 20)}
              </button>
            </div>
          </div>

          <div class="editor-toolbar">
            <button class="tool-btn" data-action="bold" title="Bold">${getIconSvg('edit', 14)} Bold</button>
            <button class="tool-btn" data-action="heading" title="Heading">${getIconSvg('code', 14)} Heading</button>
            <button class="tool-btn" data-action="bullet" title="Bullet List">${getIconSvg('list', 14)} Bullet</button>
            <button class="tool-btn" data-action="numbered" title="Numbered List">${getIconSvg('list-ordered', 14)} Numbered</button>
            <button class="tool-btn" data-action="task" title="Checklist">${getIconSvg('check-square', 14)} Task</button>
            <button class="tool-btn" data-action="image" title="Insert Image">${getIconSvg('image', 14)} Image</button>
            <button class="tool-btn" data-action="code" title="Code Block">${getIconSvg('code', 14)} Code</button>
          </div>

          <div class="editor-body" id="editor-body">
            <div class="editor-pane-left">
              <textarea id="editor-textarea" class="markdown-textarea" placeholder="Type Markdown here...">${this.activeNote.content}</textarea>
            </div>
            <div class="editor-pane-right markdown-preview" id="editor-preview">
              ${renderMarkdown(this.activeNote.content)}
            </div>
          </div>
        </div>
      </div>
    `;

    const textarea = document.getElementById('editor-textarea') as HTMLTextAreaElement;
    const titleInput = document.getElementById('editor-title') as HTMLInputElement;
    const preview = document.getElementById('editor-preview')!;
    const colorTrigger = document.getElementById('editor-color-trigger');
    const colorMenu = document.getElementById('editor-color-menu');
    const colorDot = document.getElementById('editor-color-dot');
    const colorLabel = document.getElementById('editor-color-label');

    colorTrigger?.addEventListener('click', (e) => {
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
        }
        if (colorDot) colorDot.style.background = this.getColorDisplayHex(hex);
        if (colorLabel) colorLabel.textContent = this.getColorName(hex);
        if (colorMenu) colorMenu.style.display = 'none';
      });
      item.addEventListener('mouseenter', () => {
        (item as HTMLElement).style.background = 'rgba(240, 120, 138, 0.2)';
      });
      item.addEventListener('mouseleave', () => {
        (item as HTMLElement).style.background = 'transparent';
      });
    });

    const exportTrigger = document.getElementById('editor-export-trigger');
    const exportMenu = document.getElementById('editor-export-menu');

    exportTrigger?.addEventListener('click', (e) => {
      e.stopPropagation();
      if (exportMenu) {
        exportMenu.style.display = exportMenu.style.display === 'none' ? 'block' : 'none';
      }
    });

    exportMenu?.querySelectorAll('.editor-export-item').forEach(item => {
      item.addEventListener('click', (e) => {
        e.stopPropagation();
        if (!this.activeNote) return;
        this.activeNote.title = titleInput.value.trim();
        this.activeNote.content = textarea.value;
        const fmt = item.getAttribute('data-format');
        if (fmt === 'md') exportAsMarkdown(this.activeNote);
        else if (fmt === 'html') exportAsHtml(this.activeNote);
        else if (fmt === 'pdf') exportAsPdf(this.activeNote);
        if (exportMenu) exportMenu.style.display = 'none';
      });
      item.addEventListener('mouseenter', () => {
        (item as HTMLElement).style.background = 'rgba(240, 120, 138, 0.2)';
      });
      item.addEventListener('mouseleave', () => {
        (item as HTMLElement).style.background = 'transparent';
      });
    });

    document.getElementById('editor-mode-toggle')?.addEventListener('click', () => {
      this.editorMode = this.editorMode === 'preview' ? 'edit' : 'preview';
      const surface = document.getElementById('editor-surface');
      const toggleBtn = document.getElementById('editor-mode-toggle');
      if (this.editorMode === 'preview') {
        surface?.classList.add('mode-preview');
        preview.innerHTML = renderMarkdown(textarea.value);
        this.bindInteractiveChecklist(preview, textarea);
      } else {
        surface?.classList.remove('mode-preview');
      }
      if (toggleBtn) {
        toggleBtn.innerHTML = `${getIconSvg(this.editorMode === 'preview' ? 'edit' : 'eye', 16)} <span>${this.editorMode === 'preview' ? 'Edit' : 'Preview'}</span>`;
      }
    });

    document.addEventListener('click', () => {
      if (colorMenu) colorMenu.style.display = 'none';
      if (exportMenu) exportMenu.style.display = 'none';
    });

    textarea.addEventListener('input', () => {
      if (this.activeNote) {
        this.activeNote.content = textarea.value;
        preview.innerHTML = renderMarkdown(textarea.value);
        this.bindInteractiveChecklist(preview, textarea);
      }
    });

    this.bindInteractiveChecklist(preview, textarea);

    mount.querySelectorAll('.tool-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const action = btn.getAttribute('data-action');
        this.applyToolbarAction(action, textarea);
      });
    });

    document.getElementById('editor-save-btn')?.addEventListener('click', async () => {
      if (!this.activeNote) return;
      const saveBtn = document.getElementById('editor-save-btn') as HTMLButtonElement | null;
      if (saveBtn) {
        if (saveBtn.disabled) return;
        saveBtn.disabled = true;
      }

      this.activeNote.title = titleInput.value.trim();
      this.activeNote.content = textarea.value;
      this.activeNote.updatedAt = Date.now();

      await syncEngine.uploadNote(this.activeNote);

      const existingIdx = this.notes.findIndex(n => n && n.id === this.activeNote!.id);
      if (existingIdx !== -1) {
        this.notes[existingIdx] = this.activeNote;
      } else {
        this.notes.unshift(this.activeNote);
      }

      this.renderNotesList();
      this.renderNavCounts();
      this.closeEditor();
    });

    document.getElementById('editor-share-btn')?.addEventListener('click', () => {
      if (!this.activeNote) return;
      this.activeNote.title = titleInput.value.trim();
      this.activeNote.content = textarea.value;
      this.openShareModal(this.activeNote);
    });

    document.getElementById('editor-close-btn')?.addEventListener('click', () => {
      this.closeEditor();
    });
  }

  private bindInteractiveChecklist(preview: HTMLElement, textarea: HTMLTextAreaElement): void {
    preview.querySelectorAll('.task-checkbox').forEach(cb => {
      cb.addEventListener('change', (e) => {
        const lineIdx = parseInt((e.target as HTMLElement).getAttribute('data-line') || '-1', 10);
        if (lineIdx >= 0) {
          const updated = toggleChecklistInMarkdown(textarea.value, lineIdx);
          textarea.value = updated;
          if (this.activeNote) this.activeNote.content = updated;
          preview.innerHTML = renderMarkdown(updated);
          this.bindInteractiveChecklist(preview, textarea);
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
      case 'bullet':
        insertion = `\n- ${selected || 'List item'}`;
        offset = insertion.length;
        break;
      case 'numbered':
        const before = text.substring(0, start);
        const lastLine = before.split('\n').filter(Boolean).pop() || '';
        const match = lastLine.match(/^(\s*)(\d+)\.\s*/);
        const nextNum = match ? parseInt(match[2], 10) + 1 : 1;
        insertion = `\n${nextNum}. ${selected || 'Item'}`;
        offset = insertion.length;
        break;
      case 'task':
        insertion = `\n- [ ] ${selected || 'Task item'}`;
        offset = insertion.length;
        break;
      case 'image':
        const url = prompt('Enter Image URL:');
        if (url) {
          insertion = `\n![${selected || 'Image'}](${url.trim()})\n`;
          offset = insertion.length;
        } else return;
        break;
      case 'code':
        insertion = `\n\`\`\`\n${selected || 'code here'}\n\`\`\`\n`;
        offset = insertion.length;
        break;
    }

    textarea.value = text.substring(0, start) + insertion + text.substring(end);
    textarea.focus();
    textarea.setSelectionRange(start + offset, start + offset);
    textarea.dispatchEvent(new Event('input'));
  }

  private closeEditor(): void {
    const mount = document.getElementById('editor-mount');
    if (mount) mount.innerHTML = '';
    this.activeNote = null;
  }

  private openContextMenu(e: MouseEvent, note: Note): void {
    const mount = document.getElementById('context-mount')!;

    mount.innerHTML = `
      <div class="context-menu" style="left: ${e.clientX}px; top: ${e.clientY}px;">
        <div class="context-item" data-action="pin">
          ${getIconSvg(note.isPinned ? 'pin-off' : 'pin', 16)}
          <span>${note.isPinned ? 'Unpin' : 'Pin'}</span>
        </div>
        <div class="context-item" data-action="vault">
          ${getIconSvg(note.isLocked ? 'unlock' : 'lock', 16)}
          <span>${note.isLocked ? 'Unlock to Public' : 'Move to Vault'}</span>
        </div>
        <div class="context-item" data-action="archive">
          ${getIconSvg(note.isArchived ? 'archive-restore' : 'archive', 16)}
          <span>${note.isArchived ? 'Unarchive' : 'Archive'}</span>
        </div>
        <div class="context-item" data-action="share">
          ${getIconSvg('share', 16)}
          <span>Share (Password Protected)</span>
        </div>
        <div class="context-item danger" data-action="trash">
          ${getIconSvg('trash', 16)}
          <span>${note.isTrash ? 'Delete Forever' : 'Move to Trash'}</span>
        </div>
      </div>
    `;

    mount.querySelectorAll('.context-item').forEach(item => {
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
        await this.togglePin(note);
        break;
      case 'vault':
        note.isLocked = !note.isLocked;
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
            this.render();
          }
        } else {
          note.isTrash = true;
          note.isPinned = false;
          note.updatedAt = Date.now();
          await syncEngine.uploadNote(note);
          this.render();
        }
        break;
    }
  }

  private closeContextMenu(): void {
    const mount = document.getElementById('context-mount');
    if (mount) mount.innerHTML = '';
  }

  private async togglePin(note: Note): Promise<void> {
    note.isPinned = !note.isPinned;
    note.updatedAt = Date.now();
    await syncEngine.uploadNote(note);
    this.render();
  }

  private openShareModal(note: Note): void {
    const mount = document.getElementById('modal-mount')!;
    mount.innerHTML = `
      <div class="modal-overlay" id="share-modal">
        <div class="modal-card" style="max-width: 480px;">
          <div class="modal-header">
            <h3 class="modal-title">Share Note</h3>
            <button class="btn-icon" id="modal-close-btn">${getIconSvg('close', 18)}</button>
          </div>
          <p style="font-size: 0.88rem; color: var(--text-secondary); margin-bottom: 18px; line-height: 1.5;">
            Generate a read-only link or export "<strong>${note.title || 'Untitled'}</strong>".
          </p>

          <div id="share-form">
            <div class="form-group" style="margin-bottom: 14px;">
              <label class="form-label">Password Protection (Optional)</label>
              <input type="password" id="share-password" class="form-input" placeholder="Leave empty for public link, or set password..." />
              <span style="font-size: 0.76rem; color: var(--text-muted); margin-top: 4px; display: block;">
                Leave empty for an open public link, or enter a password to encrypt.
              </span>
            </div>

            <div class="form-group" style="margin-bottom: 14px;">
              <label class="form-label">Link Expiration</label>
              <select id="share-expiry-select" class="form-input" style="background: #22191a; color: #fff3e0;">
                <option value="never">Never (Does not expire)</option>
                <option value="1h">1 Hour</option>
                <option value="1d">1 Day</option>
                <option value="7d">7 Days</option>
                <option value="custom">Custom Date & Time</option>
              </select>
            </div>

            <div class="form-group" id="custom-expiry-wrap" style="display: none; margin-bottom: 16px;">
              <label class="form-label">Select Expiration Date & Time</label>
              <input type="datetime-local" id="share-custom-expiry" class="form-input" style="background: #22191a; color: #fff3e0;" />
            </div>

            <button class="btn btn-primary" id="generate-share-btn" style="width: 100%; padding: 12px; margin-bottom: 16px;">
              ${getIconSvg('link', 16)}
              <span>Create Share Link</span>
            </button>

            <div style="border-top: 1px solid var(--border); padding-top: 14px;">
              <div style="font-size: 0.82rem; font-weight: 700; color: var(--text-secondary); margin-bottom: 10px;">Quick File Exports</div>
              <div style="display: flex; gap: 8px;">
                <button type="button" class="btn btn-secondary" id="modal-export-md" style="flex: 1; font-size: 0.8rem; padding: 8px;">
                  ${getIconSvg('file', 14)} <span>Markdown</span>
                </button>
                <button type="button" class="btn btn-secondary" id="modal-export-html" style="flex: 1; font-size: 0.8rem; padding: 8px;">
                  ${getIconSvg('code', 14)} <span>HTML</span>
                </button>
                <button type="button" class="btn btn-secondary" id="modal-export-pdf" style="flex: 1; font-size: 0.8rem; padding: 8px;">
                  ${getIconSvg('printer', 14)} <span>PDF</span>
                </button>
              </div>
            </div>
          </div>

          <div id="share-result" style="display: none; margin-top: 18px;">
            <div class="share-link-box">
              <input type="text" id="share-url-input" class="share-link-input" readonly />
              <button class="btn-icon" id="copy-share-btn" title="Copy Link">${getIconSvg('copy', 16)}</button>
            </div>
            <div id="share-type-badge" style="font-size: 0.82rem; margin-top: 10px; color: var(--primary); font-weight: 600;"></div>
            <div style="font-size: 0.85rem; color: var(--success); display: flex; align-items: center; gap: 8px; font-weight: 600; margin-top: 8px;">
              ${getIconSvg('check', 16)}
              <span>Share link generated!</span>
            </div>
          </div>
        </div>
      </div>
    `;

    document.getElementById('modal-close-btn')?.addEventListener('click', () => this.closeModal());

    const expirySelect = document.getElementById('share-expiry-select') as HTMLSelectElement;
    const customExpiryWrap = document.getElementById('custom-expiry-wrap');
    expirySelect?.addEventListener('change', () => {
      if (customExpiryWrap) {
        customExpiryWrap.style.display = expirySelect.value === 'custom' ? 'block' : 'none';
      }
    });

    document.getElementById('modal-export-md')?.addEventListener('click', () => {
      exportAsMarkdown(note);
    });
    document.getElementById('modal-export-html')?.addEventListener('click', () => {
      exportAsHtml(note);
    });
    document.getElementById('modal-export-pdf')?.addEventListener('click', () => {
      exportAsPdf(note);
    });

    document.getElementById('generate-share-btn')?.addEventListener('click', async () => {
      const passInput = document.getElementById('share-password') as HTMLInputElement;
      const pass = passInput.value.trim();

      let expiresAt: number | null = null;
      const expChoice = expirySelect.value;
      if (expChoice === '1h') {
        expiresAt = Date.now() + 3600 * 1000;
      } else if (expChoice === '1d') {
        expiresAt = Date.now() + 24 * 3600 * 1000;
      } else if (expChoice === '7d') {
        expiresAt = Date.now() + 7 * 24 * 3600 * 1000;
      } else if (expChoice === 'custom') {
        const customInput = document.getElementById('share-custom-expiry') as HTMLInputElement;
        if (customInput.value) {
          expiresAt = new Date(customInput.value).getTime();
        }
      }

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
        badge.textContent = pass ? 'Protected: Recipients require password to view.' : 'Public: Anyone with the link can view.';

        document.getElementById('copy-share-btn')?.addEventListener('click', () => {
          navigator.clipboard.writeText(shareUrl);
          alert('Share link copied to clipboard!');
        });
      } catch (err) {
        alert(`Failed to create share: ${(err as Error).message}`);
        generateBtn.disabled = false;
        generateBtn.textContent = 'Create Share Link';
      }
    });
  }

  private async renderShareReader(shareId: string): Promise<void> {
    this.appEl.style.overflow = 'auto';
    this.appEl.innerHTML = `
      <div class="share-reader-container">
        <div class="modal-card" id="reader-auth-card" style="max-width: 440px;">
          <div class="brand-icon" style="margin: 0 auto 16px; width: 50px; height: 50px;">${getIconSvg('lock', 24)}</div>
          <h2 style="text-align: center; margin-bottom: 8px; font-size: 1.4rem; font-family: var(--font-display);">Shared Note</h2>
          <p id="reader-subtitle" style="text-align: center; font-size: 0.9rem; color: var(--text-secondary); margin-bottom: 22px; line-height: 1.6;">
            Loading note...
          </p>
          <div id="reader-pass-form" style="display: none;">
            <div class="form-group">
              <input type="password" id="reader-password" class="form-input" placeholder="Enter share password..." />
            </div>
            <button class="btn btn-primary" id="reader-unlock-btn" style="width: 100%; padding: 12px;">
              ${getIconSvg('unlock', 16)}
              <span>Unlock Note</span>
            </button>
          </div>
          <div id="reader-error" style="color: var(--danger); font-size: 0.88rem; margin-top: 14px; text-align: center; font-weight: 600;"></div>
        </div>

        <div id="reader-content-card" style="display: none; width: 100%; max-width: 820px; background: var(--bg-surface); border: 1px solid var(--border-active); border-radius: var(--radius-xl); padding: 36px; box-shadow: var(--shadow-glass); margin-bottom: 40px;">
          <div style="display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--border); padding-bottom: 18px; margin-bottom: 24px; gap: 12px; flex-wrap: wrap;">
            <div>
              <span style="font-size: 0.72rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.1em; color: var(--primary); background: var(--color-accent-subtle); padding: 3px 8px; border-radius: var(--radius-pill); border: 1px solid var(--border-active);">Read-Only</span>
              <h1 id="reader-title" style="font-size: 1.7rem; font-weight: 800; font-family: var(--font-display); color: var(--text-ink); margin-top: 6px;"></h1>
            </div>
            <div style="display: flex; align-items: center; gap: 8px;">
              <button class="btn btn-secondary" id="reader-copy-btn" style="font-size: 0.85rem; padding: 7px 14px;">
                ${getIconSvg('copy', 14)}
                <span>Copy Text</span>
              </button>
              <a href="#" class="btn btn-secondary" style="font-size: 0.85rem; padding: 7px 14px; text-decoration: none;">
                ${getIconSvg('cloud', 14)}
                <span>Astral Notes</span>
              </a>
            </div>
          </div>
          <div id="reader-body" class="markdown-preview" style="line-height: 1.8;"></div>
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
          alert('Note copied to clipboard!');
        });
        return;
      }

      subtitle.textContent = 'This note is password-protected. Enter password to unlock and read.';
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
            alert('Note copied to clipboard!');
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
      if (!n.isTrash && !n.isDeleted) {
        totalWords += n.content.trim().split(/\s+/).filter(Boolean).length;
      }
    });

    const allTags = new Set<string>();
    this.notes.forEach(n => {
      if (!n.isTrash && !n.isDeleted) {
        n.tags.forEach(t => allTags.add(t));
      }
    });

    const shares = await listUserShares();

    container.innerHTML = `
      <div class="analytics-view">
        <h2 style="font-family: var(--font-display); font-size: 1.5rem; margin-bottom: 24px;">Analytics & Shared Links</h2>

        <div class="analytics-grid">
          <div class="analytics-card">
            <span class="analytics-card-val">${active.length}</span>
            <span class="analytics-card-label">Active Notes</span>
          </div>
          <div class="analytics-card">
            <span class="analytics-card-val">${totalWords.toLocaleString()}</span>
            <span class="analytics-card-label">Total Words Written</span>
          </div>
          <div class="analytics-card">
            <span class="analytics-card-val">${vaultManager.isUnlocked() ? vault.length : '—'}</span>
            <span class="analytics-card-label">Vault Notes ${vaultManager.isUnlocked() ? '(Unlocked)' : '(Locked)'}</span>
          </div>
          <div class="analytics-card">
            <span class="analytics-card-val">${pinned.length}</span>
            <span class="analytics-card-label">Pinned Notes</span>
          </div>
          <div class="analytics-card">
            <span class="analytics-card-val">${allTags.size}</span>
            <span class="analytics-card-label">Unique Tags</span>
          </div>
          <div class="analytics-card">
            <span class="analytics-card-val">${archive.length}</span>
            <span class="analytics-card-label">Archived Notes</span>
          </div>
          <div class="analytics-card">
            <span class="analytics-card-val">${trash.length}</span>
            <span class="analytics-card-label">Trash Notes</span>
          </div>
          <div class="analytics-card">
            <span class="analytics-card-val">${shares.length}</span>
            <span class="analytics-card-label">Active Shared Links</span>
          </div>
        </div>

        <div class="shares-table-container">
          <div class="shares-table-header">
            <h3 style="font-size: 1.1rem; font-family: var(--font-display);">Shared Links Management</h3>
            <span style="font-size: 0.85rem; color: var(--text-secondary);">${shares.length} total links</span>
          </div>

          <div id="shares-list">
            ${shares.length === 0 ? `
              <div style="padding: 32px; text-align: center; color: var(--text-secondary); font-size: 0.9rem;">
                No active shared links. Use the Share button on any note to generate web links.
              </div>
            ` : shares.map(s => {
              const isExpired = Boolean(s.expiresAt && Date.now() > s.expiresAt);
              const expiryStr = s.expiresAt
                ? (isExpired ? 'Expired' : `Expires ${new Date(s.expiresAt).toLocaleDateString()} ${new Date(s.expiresAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`)
                : 'Never expires';
              const base = `${window.location.origin}${window.location.pathname}`;
              const shareUrl = `${base}#/share/${s.shareId}`;
              return `
                <div class="share-item-row" data-share-id="${s.shareId}">
                  <div style="flex: 1; min-width: 0;">
                    <div style="font-weight: 700; color: var(--text-ink); font-size: 0.95rem; margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                      ${s.title}
                    </div>
                    <div style="display: flex; gap: 12px; font-size: 0.78rem; color: var(--text-secondary); align-items: center;">
                      <span style="color: ${s.isPasswordProtected ? 'var(--primary)' : 'var(--accent)'}; font-weight: 600;">
                        ${s.isPasswordProtected ? 'Password Protected' : 'Public'}
                      </span>
                      <span>Created: ${new Date(s.createdAt).toLocaleDateString()}</span>
                      <span style="color: ${isExpired ? 'var(--danger)' : 'inherit'}; font-weight: ${isExpired ? '700' : 'normal'};">
                        ${expiryStr}
                      </span>
                    </div>
                  </div>
                  <div style="display: flex; gap: 8px;">
                    <button class="btn btn-secondary share-copy-btn" data-url="${shareUrl}" style="padding: 6px 12px; font-size: 0.8rem;">
                      ${getIconSvg('copy', 14)} <span>Copy</span>
                    </button>
                    <button class="btn-icon share-revoke-btn" data-id="${s.shareId}" title="Revoke Link" style="color: var(--danger);">
                      ${getIconSvg('trash', 16)}
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
          alert('Share link copied to clipboard!');
        }
      });
    });

    container.querySelectorAll('.share-revoke-btn').forEach(btn => {
      btn.addEventListener('click', async () => {
        const shareId = btn.getAttribute('data-id');
        if (!shareId) return;
        if (confirm('Revoke this share link? Anyone with this link will no longer be able to access the note.')) {
          await revokeShare(shareId);
          this.renderAnalyticsView(container);
        }
      });
    });
  }

  private renderBackupView(container: HTMLElement): void {
    container.innerHTML = `
      <div class="analytics-view">
        <div class="analytics-header">
          <h2>Library Backup & Restore</h2>
          <p>Export your full notes database to a JSON backup, or restore a backup created on Android or Web.</p>
        </div>

        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 1.5rem; margin-top: 1.5rem;">
          <div class="analytics-card" style="padding: 1.75rem;">
            <div style="display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.85rem;">
              <div style="padding: 0.6rem; border-radius: 12px; background: rgba(99, 102, 241, 0.15); color: #6366f1;">
                ${getIconSvg('download', 24)}
              </div>
              <h3 style="font-size: 1.15rem; font-weight: 700; margin: 0; color: var(--text-ink);">Export Full Library</h3>
            </div>
            <p style="color: var(--text-secondary); font-size: 0.9rem; line-height: 1.6; margin-bottom: 1.5rem;">
              Download a complete structured JSON backup of all your notes, checklists, tags, and colors. If you have locked vault notes, you will be prompted for your vault password to decrypt and package them into the backup.
            </p>
            <button class="btn btn-primary" id="btn-export-backup" style="width: 100%; justify-content: center; padding: 12px;">
              ${getIconSvg('download', 18)}
              <span>Export Library (.json)</span>
            </button>
          </div>

          <div class="analytics-card" style="padding: 1.75rem;">
            <div style="display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.85rem;">
              <div style="padding: 0.6rem; border-radius: 12px; background: rgba(16, 185, 129, 0.15); color: #10b981;">
                ${getIconSvg('upload', 24)}
              </div>
              <h3 style="font-size: 1.15rem; font-weight: 700; margin: 0; color: var(--text-ink);">Import Full Library</h3>
            </div>
            <p style="color: var(--text-secondary); font-size: 0.9rem; line-height: 1.6; margin-bottom: 1.5rem;">
              Restore an AstralNotes backup file (.json) from your computer or phone. If the backup contains vault notes, you will be asked for the vault password of the imported file to decrypt and restore them into your library.
            </p>
            <input type="file" id="backup-file-input" accept=".json,application/json" style="display: none;" />
            <button class="btn btn-secondary" id="btn-import-backup" style="width: 100%; justify-content: center; padding: 12px;">
              ${getIconSvg('upload', 18)}
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
          message: 'Your library contains locked vault notes. Enter your current vault password to decrypt and securely package them into the backup file:',
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
            title: 'Imported File Vault Password',
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
      } catch (err: any) {
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
        <div class="modal-card" style="max-width: 440px;">
          <div class="modal-header">
            <h3>${options.title}</h3>
            <button class="btn-icon" id="prompt-modal-close">${getIconSvg('close', 18)}</button>
          </div>
          <p style="font-size: 0.9rem; color: var(--text-secondary); margin-bottom: 1rem; line-height: 1.5;">${options.message}</p>
          <div class="form-group" style="margin-bottom: 1.25rem;">
            <input type="${options.isPassword ? 'password' : 'text'}" id="prompt-modal-input" class="form-input" style="width: 100%;" placeholder="Enter password..." />
          </div>
          <div style="display: flex; gap: 0.75rem; justify-content: flex-end;">
            <button class="btn btn-secondary" id="prompt-modal-cancel">Cancel</button>
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
    let toast = document.getElementById('app-toast');
    if (!toast) {
      toast = document.createElement('div');
      toast.id = 'app-toast';
      toast.style.cssText = `
        position: fixed;
        bottom: 24px;
        right: 24px;
        background: var(--bg-surface);
        color: var(--text-ink);
        border: 1px solid var(--border-active);
        border-radius: var(--radius-md);
        padding: 12px 20px;
        box-shadow: var(--shadow-glass);
        font-size: 0.9rem;
        font-weight: 600;
        z-index: 10000;
        transition: opacity 0.3s ease, transform 0.3s ease;
        opacity: 0;
        transform: translateY(10px);
        pointer-events: none;
      `;
      document.body.appendChild(toast);
    }
    toast.textContent = msg;
    toast.style.opacity = '1';
    toast.style.transform = 'translateY(0)';
    setTimeout(() => {
      if (toast) {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(10px)';
      }
    }, 3500);
  }

  private closeModal(): void {
    const mount = document.getElementById('modal-mount');
    if (mount) mount.innerHTML = '';
  }
}

new AstralNotesApp();
