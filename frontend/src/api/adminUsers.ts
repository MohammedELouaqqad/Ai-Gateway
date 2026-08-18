import { apiFetch } from './client'
import type { ApiAdminUser } from './types'

export function fetchUsers(): Promise<ApiAdminUser[]> {
  return apiFetch<ApiAdminUser[]>('/admin/users')
}

export function toggleUserStatus(id: number): Promise<ApiAdminUser> {
  return apiFetch<ApiAdminUser>(`/admin/users/${id}/status`, { method: 'PATCH' })
}
