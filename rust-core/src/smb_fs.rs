use crate::models::SmbShare;
use smb::{Client, ClientConfig, ConnectionConfig, Error as SmbError};
use smb_msg::{Dialect, Status};
use std::net::ToSocketAddrs;

/// SMB Android compatibility profile:
/// - force NTLM on
/// - force Kerberos off
/// - constrain dialect negotiation to SMB 3.0 through 3.1.1
/// - only override the port when the caller does not use 445
pub(crate) fn smb_client_config(port: u16) -> ClientConfig {
    let mut connection = ConnectionConfig {
        min_dialect: Some(Dialect::Smb030),
        max_dialect: Some(Dialect::Smb0311),
        ..ConnectionConfig::default()
    };
    connection.auth_methods.ntlm = true;
    connection.auth_methods.kerberos = false;
    if port != 445 {
        connection.port = Some(port);
    }

    ClientConfig {
        connection,
        ..ClientConfig::default()
    }
}

pub(crate) fn qualify_username(username: &str, domain: Option<&str>) -> String {
    match domain.map(str::trim).filter(|value| !value.is_empty()) {
        Some(domain) => format!(r"{domain}\{username}"),
        None => username.to_string(),
    }
}

fn ndr_string_to_string(value: &smb_rpc::ndr64::NdrPtr<smb_rpc::ndr64::NdrString<u16>>) -> String {
    value
        .as_ref()
        .map(|inner| inner.value.to_string().trim_end_matches('\0').to_string())
        .unwrap_or_default()
}

pub(crate) fn map_disk_share(
    share: &smb_rpc::interface::ShareInfo1,
) -> Option<SmbShare> {
    if share.share_type.kind() != smb_rpc::interface::ShareKind::Disk {
        return None;
    }

    let name = ndr_string_to_string(&share.netname);
    if name.ends_with('$') {
        return None;
    }

    Some(SmbShare {
        name,
        remark: ndr_string_to_string(&share.remark),
    })
}

fn classify_ipc_connect_error(server: &str, error: SmbError) -> String {
    match error {
        SmbError::SspiError(inner) => {
            format!("SMB authentication failed for {server}: {inner}")
        }
        SmbError::ReceivedErrorMessage(status, _) if status == Status::U32_LOGON_FAILURE => {
            format!("SMB authentication failed for {server}: server rejected the credentials")
        }
        other => format!("SMB connection or authentication failed for {server}: {other}"),
    }
}

fn classify_srvsvc_error(server: &str, error: SmbError) -> String {
    match error {
        SmbError::RpcError(inner) => format!(
            "SMB server {server} refused or does not expose SRVSVC share enumeration: {inner}"
        ),
        SmbError::ReceivedErrorMessage(_, _)
        | SmbError::InvalidMessage(_)
        | SmbError::UnexpectedMessageStatus(_)
        | SmbError::UnexpectedMessageCommand(_) => format!(
            "SMB server {server} refused or does not expose SRVSVC share enumeration: {error}"
        ),
        other => format!("SMB share enumeration failed on {server}: {other}"),
    }
}

fn resolve_server_target(host: &str, port: u16) -> Result<String, String> {
    if port == 445 {
        return Ok(host.to_string());
    }

    let mut addrs = (host, port)
        .to_socket_addrs()
        .map_err(|error| format!("Failed to resolve SMB host {host}:{port}: {error}"))?;

    addrs
        .next()
        .map(|addr| addr.to_string())
        .ok_or_else(|| format!("Failed to resolve SMB host {host}:{port}: no address found"))
}

pub async fn enumerate_shares(
    host: String,
    port: u16,
    username: String,
    password: String,
    domain: Option<String>,
) -> Result<Vec<SmbShare>, String> {
    let server = resolve_server_target(&host, port)?;
    let qualified_username = qualify_username(&username, domain.as_deref());
    let client = Client::new(smb_client_config(port));

    client
        .ipc_connect(&server, &qualified_username, password)
        .await
        .map_err(|error| classify_ipc_connect_error(&server, error))?;

    client
        .list_shares(&server)
        .await
        .map(|shares| shares.iter().filter_map(map_disk_share).collect())
        .map_err(|error| classify_srvsvc_error(&server, error))
}

#[cfg(test)]
mod tests {
    use super::*;
    use smb_rpc::interface::{ShareInfo1, ShareType};
    use smb_rpc::ndr64::{NdrPtr, NdrString};
    fn share_info(name: &str, remark: &str, share_type: ShareType) -> ShareInfo1 {
        ShareInfo1 {
            netname: NdrPtr::from(name.parse::<NdrString<u16>>().unwrap()),
            share_type: share_type.into(),
            remark: NdrPtr::from(remark.parse::<NdrString<u16>>().unwrap()),
        }
    }

    #[test]
    fn qualify_username_adds_domain_only_when_present() {
        assert_eq!(qualify_username("alice", None), "alice");
        assert_eq!(qualify_username("alice", Some("")), "alice");
        assert_eq!(qualify_username("alice", Some("WORKGROUP")), r"WORKGROUP\alice");
    }

    #[test]
    fn map_disk_share_keeps_normal_disk_shares() {
        let share = share_info("Media", "Main library", ShareType::new());
        let mapped = map_disk_share(&share).expect("disk share should be kept");

        assert_eq!(mapped.name, "Media");
        assert_eq!(mapped.remark, "Main library");
    }

    #[test]
    fn map_disk_share_drops_admin_shares() {
        let share = share_info("C$", "Default share", ShareType::new().with_special(true));
        assert!(map_disk_share(&share).is_none());
    }

    #[test]
    fn map_disk_share_drops_non_disk_shares() {
        let share = share_info(
            "IPC$",
            "Remote IPC",
            ShareType::new()
                .with_kind(smb_rpc::interface::ShareKind::IPC)
                .with_special(true),
        );
        assert!(map_disk_share(&share).is_none());
    }
}
