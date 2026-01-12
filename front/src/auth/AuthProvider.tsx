import type { JSX, ReactNode } from 'react'
import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import Keycloak from 'keycloak-js'
import type { KeycloakProfile } from 'keycloak-js'

interface AuthContextValue {
  isAuthenticated: boolean
  initializing: boolean
  token: string | null
  profile: KeycloakProfile | null
  error: string | null
  roles: string[]
  permissions: Permissions
  login: () => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL ?? 'https://auth.fast-pin-pon.4loop.org'
const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM ?? 'sdmis-realm'
const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'sdmis-front'

export type Permissions = {
  canCreateIncident: boolean
  canAssignUnits: boolean
  canUpdateIncidentStatus: boolean
  canDeleteIncident: boolean
  canDeleteUnit: boolean
  canUseAddressSearch: boolean
}

function extractRoles(kc: Keycloak): string[] {
  const realmRoles = kc.tokenParsed?.realm_access?.roles ?? []
  const clientRoles = kc.tokenParsed?.resource_access?.[KEYCLOAK_CLIENT_ID]?.roles ?? []
  return [...realmRoles, ...clientRoles]
}

function derivePermissions(roles: string[]): Permissions {
  const normalized = new Set(roles.map((r) => r.toLowerCase()))
  const isIt = normalized.has('it')
  const isSuperieur = normalized.has('superieur')

  return {
    canCreateIncident: true, // Tous les profils peuvent créer
    canAssignUnits: isIt || isSuperieur,
    canUpdateIncidentStatus: isIt,
    canDeleteIncident: isIt,
    canDeleteUnit: isIt,
    canUseAddressSearch: isIt || isSuperieur,
  }
}

export function AuthProvider({ children }: Readonly<{ children: ReactNode }>): JSX.Element {
  const [keycloak] = useState(() => new Keycloak({
    url: KEYCLOAK_URL,
    realm: KEYCLOAK_REALM,
    clientId: KEYCLOAK_CLIENT_ID,
  }))
  const [initializing, setInitializing] = useState(true)
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [token, setToken] = useState<string | null>(null)
  const [profile, setProfile] = useState<KeycloakProfile | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [roles, setRoles] = useState<string[]>([])

  useEffect(() => {
    let isMounted = true

    const loadProfile = async () => {
      try {
        const userProfile = await keycloak.loadUserProfile()
        if (isMounted) setProfile(userProfile)
      } catch {
        if (isMounted) setProfile(null)
      }
    }

    const init = async () => {
      try {
        const authenticated = await keycloak.init({
          onLoad: 'login-required',
          pkceMethod: 'S256',
          checkLoginIframe: false,
          silentCheckSsoRedirectUri: `${globalThis.location.origin}/silent-check-sso.html`,
        })

        if (!isMounted) return

        setIsAuthenticated(authenticated)
        setToken(keycloak.token ?? null)
        setRoles(authenticated ? extractRoles(keycloak) : [])
        if (authenticated) await loadProfile()
      } catch (err) {
        if (!isMounted) return
        setError(err instanceof Error ? err.message : 'Authentication failed')
      } finally {
        if (isMounted) setInitializing(false)
      }
    }

    keycloak.onAuthSuccess = () => {
      setIsAuthenticated(true)
      setToken(keycloak.token ?? null)
      setRoles(extractRoles(keycloak))
      void loadProfile()
    }

    keycloak.onAuthLogout = () => {
      setIsAuthenticated(false)
      setToken(null)
      setRoles([])
      setProfile(null)
    }

    keycloak.onTokenExpired = () => {
      keycloak
        .updateToken(30)
        .then((refreshed) => {
          if (refreshed) setToken(keycloak.token ?? null)
          setRoles(extractRoles(keycloak))
        })
        .catch(() => keycloak.logout())
    }

    void init()

    return () => {
      isMounted = false
    }
  }, [keycloak])

  const value = useMemo<AuthContextValue>(
    () => ({
      isAuthenticated,
      initializing,
      token,
      profile,
      error,
      roles,
      permissions: derivePermissions(roles),
      login: () => keycloak.login({ redirectUri: globalThis.location.href }),
      logout: () => keycloak.logout({ redirectUri: globalThis.location.origin }),
    }),
    [error, initializing, isAuthenticated, keycloak, profile, roles, token],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
