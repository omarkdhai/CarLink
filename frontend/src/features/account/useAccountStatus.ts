import { useQuery } from '@tanstack/react-query'
import { conversationApi, conversationQueryKeys } from '@/services/conversationApi'
import { stickerApi, stickerQueryKeys } from '@/services/stickerApi'
import { userApi, userQueryKeys } from '@/services/userApi'
import { useAuth } from '@/features/auth/AuthContext'

export type AccountNotice = 'addPhone' | 'activateSticker' | 'verifyEmail'

/** How often the shell re-checks for new messages while the tab is open. */
const MESSAGES_REFRESH_MS = 60_000

/**
 * Owner-side account state shared by the shell (unread badges), the dashboard
 * (setup checklist) and notifications (account notices). Every query reuses
 * the pages' own query keys, so React Query fetches each one only once.
 */
export function useAccountStatus() {
  const { user } = useAuth()

  const conversations = useQuery({
    queryKey: conversationQueryKeys.list(),
    queryFn: () => conversationApi.list(),
    refetchInterval: MESSAGES_REFRESH_MS,
  })
  const stickers = useQuery({ queryKey: stickerQueryKeys.mine, queryFn: () => stickerApi.listMine() })
  const me = useQuery({ queryKey: userQueryKeys.me, queryFn: () => userApi.me() })

  const profile = me.data ?? user
  const list = stickers.data ?? []
  const unread = (conversations.data ?? []).filter((c) => c.unread).length

  const steps = {
    hasSticker: list.length > 0,
    stickerLinked: list.some((s) => s.status === 'BOUND'),
    hasPhone: Boolean(me.data?.phone),
    emailVerified: Boolean(profile?.emailVerified),
  }
  const done = Object.values(steps).filter(Boolean).length

  /** Account issues worth a notification, most important first. */
  const notices: AccountNotice[] = []
  if (me.isSuccess && !steps.hasPhone) notices.push('addPhone')
  if (stickers.isSuccess && steps.hasSticker && !steps.stickerLinked) notices.push('activateSticker')
  if (profile && !steps.emailVerified) notices.push('verifyEmail')

  return {
    notices,
    profile,
    conversations,
    stickers,
    me,
    unread,
    steps,
    setupDone: done,
    setupTotal: Object.keys(steps).length,
    /** Visitors can only reach the owner by SMS when a phone number is on file. */
    reachable: steps.hasPhone && steps.stickerLinked,
    loaded: conversations.isSuccess && stickers.isSuccess && me.isSuccess,
  }
}
