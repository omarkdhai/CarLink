import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'
import { PageLoader } from '@/components/shared/PageLoader'
import { AppLayout } from '@/components/layout/AppLayout'
import { PublicLayout } from '@/components/layout/PublicLayout'
import { AdminLayout } from '@/components/layout/AdminLayout'
import { RequireAuth, RequireAdmin, RedirectIfAuthed } from '@/routes/guards'

// ---- Public (no auth) ----
const HomePage = lazy(() => import('@/features/landing/HomePage'))

// ---- Auth ----
const LoginPage = lazy(() => import('@/features/auth/LoginPage'))
const RegisterPage = lazy(() => import('@/features/auth/RegisterPage'))
const ForgotPasswordPage = lazy(() => import('@/features/auth/ForgotPasswordPage'))
const ResetPasswordPage = lazy(() => import('@/features/auth/ResetPasswordPage'))
const VerifyEmailPage = lazy(() => import('@/features/auth/VerifyEmailPage'))
const PublicContactPage = lazy(() => import('@/features/contact/PublicContactPage'))

// ---- Owner dashboard ----
const DashboardPage = lazy(() => import('@/features/dashboard/DashboardPage'))
const VehiclesPage = lazy(() => import('@/features/vehicles/VehiclesPage'))
const VehicleFormPage = lazy(() => import('@/features/vehicles/VehicleFormPage'))
const VehicleDetailPage = lazy(() => import('@/features/vehicles/VehicleDetailPage'))
const QrManagementPage = lazy(() => import('@/features/qr/QrManagementPage'))
const MessagesPage = lazy(() => import('@/features/conversations/MessagesPage'))
const ConversationDetailPage = lazy(() => import('@/features/conversations/ConversationDetailPage'))
const NotificationsPage = lazy(() => import('@/features/notifications/NotificationsPage'))
const ProfilePage = lazy(() => import('@/features/user/ProfilePage'))
const SettingsPage = lazy(() => import('@/features/user/SettingsPage'))

// ---- Admin ----
const AdminOverviewPage = lazy(() => import('@/features/admin/AdminOverviewPage'))
const AdminUsersPage = lazy(() => import('@/features/admin/AdminUsersPage'))
const AdminVehiclesPage = lazy(() => import('@/features/admin/AdminVehiclesPage'))
const AdminQrCodesPage = lazy(() => import('@/features/admin/AdminQrCodesPage'))
const AdminReportsPage = lazy(() => import('@/features/admin/AdminReportsPage'))
const AdminAuditLogsPage = lazy(() => import('@/features/admin/AdminAuditLogsPage'))

// ---- Utility ----
const ForbiddenPage = lazy(() => import('@/features/error/ForbiddenPage'))
const NotFoundPage = lazy(() => import('@/features/error/NotFoundPage'))

function withLoader(children: React.ReactNode) {
  return <Suspense fallback={<PageLoader />}>{children}</Suspense>
}

export function AppRouter() {
  return (
    <Routes>
      {/* Public */}
      <Route
        path="/"
        element={
          <Suspense fallback={<PageLoader />}>
            <PublicLayout>
              <HomePage />
            </PublicLayout>
          </Suspense>
        }
      />
      <Route
        path="/login"
        element={
          <RedirectIfAuthed>
            <Suspense fallback={<PageLoader />}>
              <LoginPage />
            </Suspense>
          </RedirectIfAuthed>
        }
      />
      <Route
        path="/register"
        element={
          <RedirectIfAuthed>
            <Suspense fallback={<PageLoader />}>
              <RegisterPage />
            </Suspense>
          </RedirectIfAuthed>
        }
      />
      <Route
        path="/forgot-password"
        element={
          <Suspense fallback={<PageLoader />}>
            <ForgotPasswordPage />
          </Suspense>
        }
      />
      <Route
        path="/reset-password"
        element={
          <Suspense fallback={<PageLoader />}>
            <ResetPasswordPage />
          </Suspense>
        }
      />
      <Route
        path="/verify-email"
        element={
          <RedirectIfAuthed>
            <Suspense fallback={<PageLoader />}>
              <VerifyEmailPage />
            </Suspense>
          </RedirectIfAuthed>
        }
      />

      {/* Public QR contact — core product, no layout chrome */}
      <Route
        path="/c/:token"
        element={
          <Suspense fallback={<PageLoader />}>
            <PublicContactPage />
          </Suspense>
        }
      />

      {/* Authenticated app */}
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/dashboard" element={withLoader(<DashboardPage />)} />
        <Route path="/vehicles" element={withLoader(<VehiclesPage />)} />
        <Route path="/vehicles/new" element={withLoader(<VehicleFormPage />)} />
        <Route path="/vehicles/:id/edit" element={withLoader(<VehicleFormPage />)} />
        <Route path="/vehicles/:id" element={withLoader(<VehicleDetailPage />)} />
        <Route path="/vehicles/:id/qr" element={withLoader(<QrManagementPage />)} />
        <Route path="/messages" element={withLoader(<MessagesPage />)} />
        <Route path="/messages/:id" element={withLoader(<ConversationDetailPage />)} />
        <Route path="/notifications" element={withLoader(<NotificationsPage />)} />
        <Route path="/profile" element={withLoader(<ProfilePage />)} />
        <Route path="/settings" element={withLoader(<SettingsPage />)} />
        <Route path="/403" element={withLoader(<ForbiddenPage />)} />
      </Route>

      {/* Admin */}
      <Route
        element={
          <RequireAdmin>
            <AdminLayout />
          </RequireAdmin>
        }
      >
        <Route path="/admin" element={withLoader(<AdminOverviewPage />)} />
        <Route path="/admin/users" element={withLoader(<AdminUsersPage />)} />
        <Route path="/admin/vehicles" element={withLoader(<AdminVehiclesPage />)} />
        <Route path="/admin/qr-codes" element={withLoader(<AdminQrCodesPage />)} />
        <Route path="/admin/reports" element={withLoader(<AdminReportsPage />)} />
        <Route path="/admin/audit-logs" element={withLoader(<AdminAuditLogsPage />)} />
      </Route>

      {/* Fallback */}
      <Route path="*" element={withLoader(<NotFoundPage />)} />
    </Routes>
  )
}