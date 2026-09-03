import './style.css';
import { auth, googleProvider, signInWithPopup, signOut, onAuthStateChanged, type User } from './firebase';
import type { Note, DrawerDestination, SyncStatus } from './types';
import { getLocalNotes } from './db';
import { syncEngine } from './sync';
import { renderMarkdown, toggleChecklistInMarkdown } from './markdown';
import { getIconSvg } from './icons';
import { createPasswordProtectedShare, unlockSharedNote } from './share';
import { vaultManager } from './vault';

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

    syncEngine.onNotesUpdate((updatedNotes) => {
      this.notes = updatedNotes;
      this.renderNotesList();
      this.renderNavCounts();
    });

    vaultManager.onVaultStateChange(() => {
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
            <span class="nav-item-count" id="count-vault">0</span>
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
      document.getElementById('sidebar')?.classList.toggle('open');
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

    const cleanSnippet = note.content
      .replace(/#+\s*/g, '')
      .replace(/!\[.*?\]\(.*?\)/g, '[Image]')
      .slice(0, 180);

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
        <p class="note-card-snippet">${cleanSnippet || 'Empty note...'}</p>
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
    if (countVault) countVault.textContent = vault.length.toString();
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
    if (isNew && this.currentDestination === 'VAULT') {
      this.activeNote.isLocked = true;
    }

    const mount = document.getElementById('editor-mount')!;
    mount.innerHTML = `
      <div class="editor-modal" id="editor-modal">
        <div class="editor-surface">
          <div class="editor-header">
            <input type="text" id="editor-title" class="editor-title-input" placeholder="Note Title..." value="${this.activeNote.title}" />
            <div style="display: flex; align-items: center; gap: 8px;">
              <select id="editor-color-select" class="btn btn-secondary" style="padding: 7px 12px; font-size: 0.82rem;">
                ${NOTE_COLORS.map(c => `
                  <option value="${c.hex}" ${this.activeNote?.colorHex === c.hex ? 'selected' : ''}>${c.name}</option>
                `).join('')}
              </select>

              <button class="btn btn-secondary" id="editor-vault-btn" title="${this.activeNote.isLocked ? 'Move to Public' : 'Move to Vault'}">
                ${getIconSvg(this.activeNote.isLocked ? 'unlock' : 'lock', 16)}
                <span id="editor-vault-text">${this.activeNote.isLocked ? 'In Vault' : 'Normal'}</span>
              </button>
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
            <div class="editor-pane-right" id="editor-preview">
              ${renderMarkdown(this.activeNote.content)}
            </div>
          </div>
        </div>
      </div>
    `;

    const textarea = document.getElementById('editor-textarea') as HTMLTextAreaElement;
    const titleInput = document.getElementById('editor-title') as HTMLInputElement;
    const preview = document.getElementById('editor-preview')!;
    const colorSelect = document.getElementById('editor-color-select') as HTMLSelectElement;

    colorSelect?.addEventListener('change', () => {
      if (this.activeNote) {
        this.activeNote.colorHex = colorSelect.value;
      }
    });

    document.getElementById('editor-vault-btn')?.addEventListener('click', () => {
      if (!this.activeNote) return;
      this.activeNote.isLocked = !this.activeNote.isLocked;
      const textSpan = document.getElementById('editor-vault-text');
      if (textSpan) {
        textSpan.textContent = this.activeNote.isLocked ? 'In Vault' : 'Normal';
      }
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
      this.activeNote.title = titleInput.value.trim();
      this.activeNote.content = textarea.value;
      this.activeNote.colorHex = colorSelect.value;
      this.activeNote.updatedAt = Date.now();

      await syncEngine.uploadNote(this.activeNote);

      if (isNew) {
        this.notes.unshift(this.activeNote);
      } else {
        const idx = this.notes.findIndex(n => n.id === this.activeNote!.id);
        if (idx !== -1) this.notes[idx] = this.activeNote;
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
        <div class="modal-card">
          <div class="modal-header">
            <h3 class="modal-title">Password-Protected Read-Only Share</h3>
            <button class="btn-icon" id="modal-close-btn">${getIconSvg('close', 18)}</button>
          </div>
          <p style="font-size: 0.9rem; color: var(--text-secondary); margin-bottom: 18px; line-height: 1.6;">
            Create an encrypted, read-only link for "<strong>${note.title || 'Untitled'}</strong>".
            Recipients can decrypt and read the note using the password, but cannot edit or alter your content.
          </p>
          <div id="share-form">
            <div class="form-group">
              <label class="form-label">Set Share Password</label>
              <input type="password" id="share-password" class="form-input" placeholder="Enter password for recipient..." />
            </div>
            <button class="btn btn-primary" id="generate-share-btn" style="width: 100%; padding: 12px;">
              ${getIconSvg('lock', 16)}
              <span>Generate Read-Only Link</span>
            </button>
          </div>
          <div id="share-result" style="display: none; margin-top: 18px;">
            <div class="share-link-box">
              <input type="text" id="share-url-input" class="share-link-input" readonly />
              <button class="btn-icon" id="copy-share-btn" title="Copy Link">${getIconSvg('copy', 16)}</button>
            </div>
            <div style="font-size: 0.85rem; color: var(--success); display: flex; align-items: center; gap: 8px; font-weight: 600;">
              ${getIconSvg('check', 16)}
              <span>Read-only link ready! Share the link and password with the recipient.</span>
            </div>
          </div>
        </div>
      </div>
    `;

    document.getElementById('modal-close-btn')?.addEventListener('click', () => this.closeModal());

    document.getElementById('generate-share-btn')?.addEventListener('click', async () => {
      const passInput = document.getElementById('share-password') as HTMLInputElement;
      const pass = passInput.value.trim();
      if (!pass) {
        alert('Please enter a share password.');
        return;
      }

      const generateBtn = document.getElementById('generate-share-btn') as HTMLButtonElement;
      generateBtn.disabled = true;
      generateBtn.textContent = 'Encrypting & Generating...';

      try {
        const { shareUrl } = await createPasswordProtectedShare(note, pass);
        document.getElementById('share-form')!.style.display = 'none';
        const resultBox = document.getElementById('share-result')!;
        resultBox.style.display = 'block';
        const urlInput = document.getElementById('share-url-input') as HTMLInputElement;
        urlInput.value = shareUrl;

        document.getElementById('copy-share-btn')?.addEventListener('click', () => {
          navigator.clipboard.writeText(shareUrl);
          alert('Share link copied to clipboard!');
        });
      } catch (err) {
        alert(`Failed to create share: ${(err as Error).message}`);
        generateBtn.disabled = false;
        generateBtn.textContent = 'Generate Read-Only Link';
      }
    });
  }

  private renderShareReader(shareId: string): void {
    this.appEl.innerHTML = `
      <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; width: 100%; background: radial-gradient(circle at 50% 25%, rgba(240, 120, 138, 0.12) 0%, transparent 60%), var(--bg-dark);">
        <div class="modal-card" id="reader-auth-card" style="max-width: 440px;">
          <div class="brand-icon" style="margin: 0 auto 16px; width: 50px; height: 50px;">${getIconSvg('lock', 24)}</div>
          <h2 style="text-align: center; margin-bottom: 8px; font-size: 1.4rem; font-family: var(--font-display);">Protected Shared Note</h2>
          <p style="text-align: center; font-size: 0.9rem; color: var(--text-secondary); margin-bottom: 22px; line-height: 1.6;">
            This note is encrypted and read-only. Enter the shared password to unlock and read.
          </p>
          <div class="form-group">
            <input type="password" id="reader-password" class="form-input" placeholder="Enter share password..." />
          </div>
          <button class="btn btn-primary" id="reader-unlock-btn" style="width: 100%; padding: 12px;">
            ${getIconSvg('unlock', 16)}
            <span>Unlock Note</span>
          </button>
          <div id="reader-error" style="color: var(--danger); font-size: 0.88rem; margin-top: 14px; text-align: center; font-weight: 600;"></div>
        </div>

        <div id="reader-content-card" style="display: none; width: 100%; max-width: 820px; background: var(--bg-surface); border: 1px solid var(--border-active); border-radius: var(--radius-xl); padding: 36px; box-shadow: var(--shadow-glass);">
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
          <div id="reader-body" style="line-height: 1.8;"></div>
        </div>
      </div>
    `;

    document.getElementById('reader-unlock-btn')?.addEventListener('click', async () => {
      const pass = (document.getElementById('reader-password') as HTMLInputElement).value;
      const errEl = document.getElementById('reader-error')!;
      errEl.textContent = '';

      try {
        const decrypted = await unlockSharedNote(shareId, pass);
        document.getElementById('reader-auth-card')!.style.display = 'none';
        const contentCard = document.getElementById('reader-content-card')!;
        contentCard.style.display = 'block';
        document.getElementById('reader-title')!.textContent = decrypted.title || 'Untitled Note';
        
        const rendered = renderMarkdown(decrypted.content);
        const readerBody = document.getElementById('reader-body')!;
        readerBody.innerHTML = rendered;
        readerBody.querySelectorAll('.task-checkbox').forEach((cb) => {
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
  }

  private closeModal(): void {
    const mount = document.getElementById('modal-mount');
    if (mount) mount.innerHTML = '';
  }
}

new AstralNotesApp();
