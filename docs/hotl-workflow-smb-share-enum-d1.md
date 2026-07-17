# HOTL Workflow: SMB Share Enumeration D1

1. Confirm actual branch state versus requested design inputs.
2. Add the missing Rust SMB module and shared client-config helper without touching existing WebDAV paths.
3. Expose the new UniFFI record and function.
4. Regenerate Kotlin bindings from the built host library.
5. Run Rust tests, Rust build, and Kotlin compile gate.
6. Commit the result with a conventional message.
