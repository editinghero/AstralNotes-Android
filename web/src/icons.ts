import {
  createElement,
  Search,
  Menu,
  Plus,
  Pin,
  PinOff,
  Archive,
  ArchiveRestore,
  Trash2,
  Lock,
  Unlock,
  Palette,
  Share2,
  Edit3,
  Pencil,
  Eye,
  Code,
  List,
  ListOrdered,
  CheckSquare,
  Image,
  ImagePlus,
  LogIn,
  LogOut,
  Check,
  RefreshCw,
  AlertCircle,
  Cloud,
  ExternalLink,
  Copy,
  X,
  Tag,
  FileText,
  Upload,
  LayoutGrid,
  ListFilter,
  ShieldAlert,
  ShieldCheck,
  ArrowLeft,
  ChevronDown,
  BarChart3,
  Download,
  Printer,
  Clock,
  Link2
} from 'lucide';

const ICONS: Record<string, unknown> = {
  search: Search,
  menu: Menu,
  plus: Plus,
  pin: Pin,
  'pin-off': PinOff,
  archive: Archive,
  'archive-restore': ArchiveRestore,
  trash: Trash2,
  lock: Lock,
  unlock: Unlock,
  palette: Palette,
  share: Share2,
  edit: Edit3,
  pencil: Pencil || Edit3,
  eye: Eye,
  code: Code,
  list: List,
  'list-ordered': ListOrdered,
  'check-square': CheckSquare,
  image: Image,
  'image-plus': ImagePlus || Image,
  'log-in': LogIn,
  'log-out': LogOut,
  check: Check,
  refresh: RefreshCw,
  alert: AlertCircle,
  cloud: Cloud,
  'external-link': ExternalLink,
  copy: Copy,
  close: X,
  tag: Tag,
  file: FileText,
  upload: Upload,
  grid: LayoutGrid,
  'list-view': ListFilter,
  shield: ShieldAlert,
  'shield-check': ShieldCheck || ShieldAlert,
  back: ArrowLeft,
  'arrow-left': ArrowLeft,
  'chevron-down': ChevronDown,
  analytics: BarChart3,
  download: Download,
  printer: Printer,
  clock: Clock,
  link: Link2
};

export function getIconSvg(name: string, size = 18, className = ''): string {
  const iconDef = ICONS[name];
  if (!iconDef) return '';
  const el = createElement(iconDef as Parameters<typeof createElement>[0], {
    width: size,
    height: size,
    class: className
  });
  return el.outerHTML;
}

export function getLogoSvg(size = 32, className = ''): string {
  return `<svg viewBox="0 0 512 512" width="${size}" height="${size}" class="${className}" role="img" aria-label="Astral Notes">
    <rect width="512" height="512" rx="112" fill="currentColor" fill-opacity="0.16" />
    <path d="M186 226v-34a70 70 0 0 1 140 0v34" fill="none" stroke="currentColor" stroke-width="34" stroke-linecap="round" />
    <rect x="152" y="226" width="208" height="160" rx="44" fill="currentColor" />
    <circle cx="256" cy="292" r="20" fill="var(--background)" />
    <rect x="246" y="300" width="20" height="46" rx="10" fill="var(--background)" />
  </svg>`;
}
