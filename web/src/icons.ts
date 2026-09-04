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
  Link2,
  Settings
} from 'lucide';

const ICONS: Record<string, unknown> = {
  settings: Settings,
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

export function getLogoSvg(size = 22, className = ''): string {
  return `<svg viewBox="0 0 24 24" width="${size}" height="${size}" class="${className}" role="img" aria-label="Astral Notes" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
    <g transform="translate(1.2, 1.2) scale(0.9)">
      <path d="M18 2l4 4M2 22l1.276-4.68c.083-.305.125-.458.189-.6.057-.127.126-.247.208-.359.092-.126.204-.238.428-.462L14.434 5.566c.198-.198.297-.297.411-.334.1-.033.209-.033.309 0 .114.037.213.136.411.334l2.869 2.869c.198.198.297-.297.334.411.033.1.033.209 0 .309-.037.114-.136.213-.334.411L8.1 19.899c-.224.224-.336.336-.462.428-.112.082-.232.151-.359.208-.142.064-.295.106-.6.189L2 22z" />
    </g>
  </svg>`;
}
