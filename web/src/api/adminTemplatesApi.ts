import type { MemeProfile, Slot, TemplateStatus, TemplateSummary, TemplateView } from './types';
import { enc, request } from './http';

/** What an administrator does with the templates of the library: read, draft, describe, approve, retire. */
export const adminTemplatesApi = {
  listTemplates: (status?: TemplateStatus) =>
    request<TemplateSummary[]>('GET', `/api/admin/templates${status ? `?status=${status}` : ''}`),

  getTemplate: (id: string) => request<TemplateView>('GET', `/api/admin/templates/${enc(id)}`),

  draftTemplate(name: string, file: File) {
    const form = new FormData();
    form.append('name', name);
    form.append('file', file);
    return request<{ id: string }>('POST', '/api/admin/templates', { form });
  },

  saveProfile: (id: string, profile: MemeProfile) =>
    request<void>('PUT', `/api/admin/templates/${enc(id)}/profile`, { json: profile }),

  defineSlot: (id: string, slot: Slot) =>
    request<void>('POST', `/api/admin/templates/${enc(id)}/slots`, { json: slot }),

  redefineSlot: (id: string, slot: Slot) =>
    request<void>('PUT', `/api/admin/templates/${enc(id)}/slots/${slot.slotNo}`, { json: slot }),

  removeSlot: (id: string, slotNo: number) =>
    request<void>('DELETE', `/api/admin/templates/${enc(id)}/slots/${slotNo}`),

  approve: (id: string) => request<void>('POST', `/api/admin/templates/${enc(id)}/approve`),

  retire: (id: string) => request<void>('POST', `/api/admin/templates/${enc(id)}/retire`),
};
