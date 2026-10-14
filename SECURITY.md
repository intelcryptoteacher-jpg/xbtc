# Security status and handling

This repository is a fork of an educational C++ Bitcoin implementation.
It is not the XBTC exchange HTML application and is not approved for custody,
payments, token issuance, or production deployment.

## Review on 2026-10-14

Reviewed the 88 tracked files at commit
243c29574651d4365cd5313b75043463afc80950 with a limited pattern scan for
common GitHub/AWS credentials, PEM private-key headers, and quoted secret
assignments. No matches were found. This is not proof that no secrets exist:
history, other accounts, deployed services, and files outside this repository
were not scanned. No runtime security audit or dependency audit was completed.

The repository was public, master was unprotected, and no repository rulesets
were returned at review time. These settings were not changed.

The README describes an incomplete learning implementation and lists an old
test environment. The exchange HTML, login/subscription interface, embedded
wallet, and token-minting contract are absent from the reviewed tree.
No on-chain issuance or balances were verified.

## Safeguards added

The root .gitignore excludes common environment-secret files, private keys,
wallet data, selected recovery-phrase filenames, local configuration, node
data, logs, and build output from normal Git staging. It does not encrypt
files, remove already tracked files, prevent forced staging, scan history,
or restrict access to this public repository.

## Remaining owner actions

- Enable two-factor authentication or a passkey on the GitHub account.
- Review account sessions, collaborators, installed apps, and access tokens;
  remove access that is no longer needed.
- Configure master branch protection or a ruleset to require pull requests,
  restrict force pushes and deletions, and require relevant checks.
- Enable secret scanning and push protection where available.
- Keep the exchange's private source and operational data in an appropriate
  private repository; check GitHub's fork visibility constraints first.
- Locate and review the actual exchange application before collecting
  passwords, accepting funds, or connecting a funded wallet.

These administrative controls were not applied by this commit.

## Sensitive information

Never put passwords, API credentials, private keys, wallet backups, or seed
phrases in repository files, issues, pull requests, logs, or chat.
Use an appropriate secret manager for service credentials and a wallet's
supported secure storage for wallet keys.

If a credential is exposed, revoke or rotate it with its issuer promptly.
Deleting the file or making a repository private does not invalidate copies.
For an exposed wallet recovery phrase or private key, follow the wallet's
recovery procedure and move assets to a newly generated secure wallet.

Do not post sensitive vulnerability details in public issues. Use a verified
private channel to the repository owner, or GitHub private vulnerability
reporting if the owner enables it.
