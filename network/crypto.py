"""
Crypto utilities for Fast Pin Pon Micro:bit network.

XOR encryption for radio data confidentiality.
Uses simple XOR cipher optimized for short radio packets (< 128 bytes).
"""

import base64
import os

# Shared secret key - loaded from environment or use default
# In production, CRYPTO_KEY environment variable should be set
_DEFAULT_KEY = "FPP2024XORKEY"


def _get_key() -> str:
    """Get encryption key from environment or use default."""
    return os.environ.get("CRYPTO_KEY", _DEFAULT_KEY)


def xor_encrypt(data: str, key: str = None) -> str:
    """
    XOR encrypt a string and return base64-encoded result.
    
    Args:
        data: Plain text to encrypt
        key: Secret key for XOR operation (optional)
    
    Returns:
        Base64 encoded encrypted string (safe for transmission)
    """
    if key is None:
        key = _get_key()
    
    encrypted_bytes = bytearray()
    for i, char in enumerate(data):
        key_char = key[i % len(key)]
        encrypted_bytes.append(ord(char) ^ ord(key_char))
    
    # Base64 encode to ensure only printable ASCII characters
    return base64.b64encode(bytes(encrypted_bytes)).decode('ascii')


def xor_decrypt(encrypted_data: str, key: str = None) -> str:
    """
    Decrypt base64-encoded XOR encrypted string.
    
    Args:
        encrypted_data: Base64 encoded encrypted string
        key: Secret key for XOR operation (optional)
    
    Returns:
        Decrypted plain text string
    """
    if key is None:
        key = _get_key()
    
    try:
        encrypted_bytes = base64.b64decode(encrypted_data)
        decrypted_chars = []
        for i, byte_val in enumerate(encrypted_bytes):
            key_char = key[i % len(key)]
            decrypted_chars.append(chr(byte_val ^ ord(key_char)))
        return ''.join(decrypted_chars)
    except Exception:
        # Return original if decryption fails (for backward compatibility)
        return encrypted_data
