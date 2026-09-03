import { marked } from 'marked';
import DOMPurify from 'dompurify';

marked.setOptions({
  gfm: true,
  breaks: true
});

export function renderMarkdown(markdown: string): string {
  if (!markdown.trim()) {
    return '<p class="empty-preview">No content to preview.</p>';
  }

  const lines = markdown.split('\n');
  const processedLines = lines.map((line, idx) => {
    const taskMatch = line.match(/^(\s*[-*+]\s+)\[([ xX])\]\s*(.*)$/);
    if (taskMatch) {
      const indent = taskMatch[1];
      const isChecked = taskMatch[2].toLowerCase() === 'x';
      const text = taskMatch[3];
      const checkedAttr = isChecked ? 'checked' : '';
      const doneClass = isChecked ? 'task-done' : '';
      return `${indent}<span class="interactive-task ${doneClass}"><input type="checkbox" ${checkedAttr} data-line="${idx}" class="task-checkbox" /> ${text}</span>`;
    }
    return line;
  });

  const rawHtml = marked.parse(processedLines.join('\n')) as string;

  return DOMPurify.sanitize(rawHtml, {
    ADD_ATTR: ['data-line', 'checked', 'type', 'target']
  });
}

export function toggleChecklistInMarkdown(markdown: string, lineIndex: number): string {
  const lines = markdown.split('\n');
  if (lineIndex >= 0 && lineIndex < lines.length) {
    const line = lines[lineIndex];
    if (line.match(/\[x\]/i)) {
      lines[lineIndex] = line.replace(/\[x\]/i, '[ ]');
    } else if (line.match(/\[ \]/)) {
      lines[lineIndex] = line.replace(/\[ \]/, '[x]');
    }
    return lines.join('\n');
  }
  return markdown;
}
