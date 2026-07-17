//! Process-global Tokio runtime.
//!
//! A single multi-threaded runtime is shared by every `Repository` instance
//! (main + preview databases) and the loopback media proxy. A persistent
//! runtime is required because the SMB client keeps background connection
//! tasks alive between FFI calls, and the proxy must serve requests while no
//! FFI call is in flight — neither works with a `block_on`-driven
//! current-thread runtime.

use std::sync::OnceLock;
use tokio::runtime::Runtime;

pub fn global() -> &'static Runtime {
    static RUNTIME: OnceLock<Runtime> = OnceLock::new();
    RUNTIME.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .worker_threads(2)
            .thread_name("rust-core-rt")
            .enable_all()
            .build()
            .expect("Failed to create global Tokio runtime")
    })
}
