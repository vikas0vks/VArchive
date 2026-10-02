# Security policy

## Reporting a vulnerability

Please report security issues privately through the repository's GitHub Security Advisories page instead of opening a public issue with exploit details.

Include the affected VArchive version, Android version, archive format, minimal reproduction steps, and a harmless proof-of-concept archive when possible. Remove passwords, tokens, personal documents, and device identifiers before attaching evidence.

## Security scope

Useful reports include archive path traversal, unsafe link handling, archive-bomb control bypasses, unintended file overwrite, password persistence, malformed-archive crashes, and Storage Access Framework boundary violations.

VArchive is a local archive utility and does not request the Android internet permission. Unsupported-format reports are not security vulnerabilities unless they cross a stated trust boundary.

## Supported version

Security fixes currently target the latest release published on GitHub Releases.
