import { vi } from 'vitest'

export function stubDocumentDownload() {
  const createObjectURL = vi.fn(() => 'blob:current-document')
  const revokeObjectURL = vi.fn()
  const downloads: Array<{ filename: string; url: string }> = []
  vi.stubGlobal('URL', class extends URL {
    static createObjectURL = createObjectURL
    static revokeObjectURL = revokeObjectURL
  })
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    downloads.push({ filename: this.download, url: this.href })
  })
  return { createObjectURL, revokeObjectURL, downloads }
}
