# WebDAV Credential Hardening Plan

## Goal

Remove plain-text password persistence from `webdav_slots_json` while preserving:

- multi-slot WebDAV configuration
- “remember password” behavior
- current-session authentication for Glide / OkHttp / repository access
- backward migration from existing installs

## Current Problems

1. `webdav_slots_json` currently stores `username`, `password`, and `rememberPassword` together.
2. Passwords are readable from ordinary app preferences storage.
3. UI writes passwords unconditionally, even when “remember password” is false.
4. Runtime consumers (`SettingsActivity`, `WebDAVToonApplication`, repository/image loading code) treat stored config as the source of truth.

## Target Storage Model

Split WebDAV state into **non-secret config**, **secret storage**, and **session-only cache**.

### 1) Non-secret config: DataStore

Keep the following in `AppSettingsStore` / DataStore:

- slot id
- alias
- protocol
- host / base path
- port
- username
- enabled
- rememberPassword

Do **not** store password in `webdav_slots_json`.

Suggested shape:

```json
{
  "0": {
    "enabled": true,
    "protocol": "https",
    "url": "example.com/dav",
    "port": 443,
    "username": "alice",
    "rememberPassword": true,
    "alias": "NAS"
  }
}
```

### 2) Secret storage: encrypted local store

Persist remembered passwords in an encrypted store keyed by slot id.

Chosen implementation:

- use Android Keystore directly for an AES/GCM key
- store encrypted blobs in ordinary `SharedPreferences`
- store values under stable keys like:
  - `webdav_password_slot_0`
  - `webdav_password_slot_1`

Rationale:

- AndroidX Security Crypto is now deprecated in official Android guidance
- Android Keystore remains the root of trust
- we keep the runtime read path simple for networking glue code

### 3) Session-only cache: in-memory

When `rememberPassword == false`, keep the password only in memory for the running process.

Suggested behavior:

- store in a process-local map keyed by slot id
- clear on process death automatically
- optionally clear on explicit logout / slot delete

This lets connection testing and image loading work during the current session without persisting the secret.

## Remember Password Semantics

### When checked

- save non-secret config to DataStore
- save password to encrypted store
- populate in-memory cache as well

### When unchecked

- save non-secret config to DataStore with `rememberPassword = false`
- remove any previously remembered password from encrypted store
- keep password only in in-memory session cache

### On app restart

- remembered password: restored from encrypted store
- non-remembered password: unavailable
- UI should show an empty password field for non-remembered slots

## Runtime Read Policy

All runtime password consumers should resolve credentials using a single policy:

1. check in-memory session cache
2. if not found and `rememberPassword == true`, check encrypted store
3. otherwise return empty / unavailable

This avoids different code paths making different persistence assumptions.

## Glide / OkHttp / Repository Behavior

### Glide / OkHttp

`WebDAVToonApplication` should read credentials through the shared credential policy, not directly from plain preferences.

Expected behavior:

- if session cache has password, authenticated requests work immediately
- if not cached but slot is remembered, encrypted password is used
- if neither exists, request proceeds without auth and may 401

### Repository / connection test / delete APIs

Repository and WebDAV actions should use the same resolver so all network paths behave consistently.

## Migration Plan

On first launch after the change:

1. parse existing `webdav_slots_json`
2. for each slot:
   - copy non-secret fields into the new slot model
   - if `rememberPassword == true` and password is non-empty:
     - write password into encrypted store
   - if `rememberPassword == false`:
     - do **not** persist the password
3. rewrite `webdav_slots_json` without password fields
4. mark credential migration complete

### Important migration rule

If legacy data contains a password while `rememberPassword == false`, treat that password as legacy leakage and do **not** preserve it across restart.

## Delete / Edit Semantics

### Editing a slot

- changing password with remember on: update encrypted store
- changing password with remember off: update session cache only
- turning remember off: delete encrypted password immediately
- turning remember on: persist the current entered password

### Deleting a slot

- remove slot config from DataStore
- remove encrypted password for that slot
- remove session-cached password for that slot

## Test Matrix

Minimum tests to add:

1. URL normalization is unchanged by the credential split
2. remembered password survives process restart
3. non-remembered password does not survive process restart
4. toggling remember from on -> off deletes encrypted secret
5. migration moves remembered passwords out of JSON
6. migration strips leaked non-remembered passwords from JSON
7. deleting a slot clears both encrypted and session secrets

## Rollout Notes

- keep migration idempotent
- avoid logging passwords or full auth headers
- keep password reads centralized behind one resolver API
- prefer backward-compatible slot JSON parsing during rollout so older installs do not break
- allow cleartext HTTP only in debug builds; release builds should not globally opt into cleartext
