"""
Token Manager for Keycloak OAuth2 Client Credentials authentication.

Handles automatic token refresh for the Python bridges.
"""

import os
import time
import requests
from typing import Optional
import threading
from pathlib import Path


def load_dotenv():
    """Load environment variables from .env file in project root."""
    # Find .env file (look in current dir and parent dirs)
    current = Path(__file__).parent
    for _ in range(3):  # Check up to 3 levels up
        env_file = current / ".env"
        if env_file.exists():
            print(f"[ENV] Loading {env_file}")
            with open(env_file, 'r') as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith('#') and '=' in line:
                        key, _, value = line.partition('=')
                        key = key.strip()
                        value = value.strip().strip('"').strip("'")
                        if key and key not in os.environ:  # Don't override existing env vars
                            os.environ[key] = value
            return True
        current = current.parent
    print("[ENV] No .env file found")
    return False


class TokenManager:
    """Manages OAuth2 access tokens with automatic refresh."""
    
    def __init__(self, token_url: str, client_id: str, client_secret: str):
        self.token_url = token_url
        self.client_id = client_id
        self.client_secret = client_secret
        self.access_token: Optional[str] = None
        self.expires_at: float = 0
        self._lock = threading.Lock()
    
    def get_token(self) -> Optional[str]:
        """Get a valid access token, refreshing if necessary."""
        with self._lock:
            # Refresh if token expires in less than 30 seconds
            if self.access_token is None or time.time() > (self.expires_at - 30):
                self._fetch_token()
            return self.access_token
    
    def _fetch_token(self) -> None:
        """Fetch a new access token from Keycloak."""
        try:
            response = requests.post(
                self.token_url,
                data={
                    "grant_type": "client_credentials",
                    "client_id": self.client_id,
                    "client_secret": self.client_secret,
                },
                headers={"Content-Type": "application/x-www-form-urlencoded"},
                timeout=10
            )
            
            if response.status_code == 200:
                data = response.json()
                self.access_token = data.get("access_token")
                expires_in = data.get("expires_in", 300)
                self.expires_at = time.time() + expires_in
                print(f"[AUTH] Token refreshed, expires in {expires_in}s")
            else:
                print(f"[AUTH] Token fetch failed: {response.status_code} - {response.text[:100]}")
                self.access_token = None
                
        except Exception as e:
            print(f"[AUTH] Token fetch error: {e}")
            self.access_token = None


class AuthenticatedSession:
    """Requests session with automatic Bearer token injection."""
    
    def __init__(self, token_manager: Optional[TokenManager] = None):
        self.token_manager = token_manager
        self.session = requests.Session()
    
    def _get_headers(self) -> dict:
        """Get headers with Bearer token if available."""
        headers = {}
        if self.token_manager:
            token = self.token_manager.get_token()
            if token:
                headers["Authorization"] = f"Bearer {token}"
        return headers
    
    def get(self, url: str, **kwargs) -> requests.Response:
        """Authenticated GET request."""
        headers = kwargs.pop("headers", {})
        headers.update(self._get_headers())
        return self.session.get(url, headers=headers, **kwargs)
    
    def post(self, url: str, **kwargs) -> requests.Response:
        """Authenticated POST request."""
        headers = kwargs.pop("headers", {})
        headers.update(self._get_headers())
        return self.session.post(url, headers=headers, **kwargs)
    
    def patch(self, url: str, **kwargs) -> requests.Response:
        """Authenticated PATCH request."""
        headers = kwargs.pop("headers", {})
        headers.update(self._get_headers())
        return self.session.patch(url, headers=headers, **kwargs)
    
    def put(self, url: str, **kwargs) -> requests.Response:
        """Authenticated PUT request."""
        headers = kwargs.pop("headers", {})
        headers.update(self._get_headers())
        return self.session.put(url, headers=headers, **kwargs)


def create_authenticated_session() -> AuthenticatedSession:
    """Create an authenticated session using environment variables.
    
    Environment variables:
        KEYCLOAK_URL: Keycloak server URL (e.g., http://localhost:8082)
        KEYCLOAK_REALM: Keycloak realm name
        KEYCLOAK_CLIENT_ID: OAuth2 client ID
        KEYCLOAK_CLIENT_SECRET: OAuth2 client secret
    
    Returns:
        AuthenticatedSession with token management, or plain session if auth not configured.
    """
    keycloak_url = os.environ.get("KEYCLOAK_URL", "")
    realm = os.environ.get("KEYCLOAK_REALM", "sdmis-realm")
    client_id = os.environ.get("KEYCLOAK_CLIENT_ID", "")
    client_secret = os.environ.get("KEYCLOAK_CLIENT_SECRET", "")
    
    if keycloak_url and client_id and client_secret:
        token_url = f"{keycloak_url}/realms/{realm}/protocol/openid-connect/token"
        print(f"[AUTH] Configured with Keycloak: {token_url}")
        token_manager = TokenManager(token_url, client_id, client_secret)
        return AuthenticatedSession(token_manager)
    else:
        print("[AUTH] No Keycloak config found, using unauthenticated requests")
        return AuthenticatedSession(None)
