/**
 * 用户相关类型定义
 */

export interface User {
  id: number
  username: string
  passwordHash?: string // 前端不接收密码哈希
  nickname?: string
  email?: string
  phone?: string
  familyId?: number
  isActive: boolean
  lastLoginAt?: string
  createdAt: string
  updatedAt: string
}

export interface Family {
  id: number
  familyCode: string
  familyName: string
  adminUserId: number
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface UserFamilyRole {
  id: number
  userId: number
  familyId: number
  role: 'ADMIN' | 'MEMBER'
  createdAt: string
  updatedAt: string
}

export interface AuthRequest {
  username: string
  password: string
}

export interface AuthResponse {
  token: string
  user: UserInfo
}

export interface UserInfo {
  id: number
  username: string
  nickname?: string
  email?: string
  phone?: string
  familyId?: number
}
