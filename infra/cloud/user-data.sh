#!/bin/bash
# User data of the demo instance (infra/terraform/demo, ADR 0006). It runs once, as
# root, on the first boot of every new instance.
#
# Terraform prepends a header that sets, as readonly variables:
#   DATA_VOLUME_SERIAL  id of the data volume without its hyphen (vol0123...)
#   COMPOSE_VERSION     Docker Compose release to install
#   COMPOSE_SHA256      SHA-256 of that release's linux-aarch64 binary
#
# The data volume outlives the instance. Its filesystem is created only when the
# volume is blank, so a relaunch mounts the same data instead of formatting it.
# shellcheck disable=SC2154
set -euo pipefail

: "${DATA_VOLUME_SERIAL:?}" "${COMPOSE_VERSION:?}" "${COMPOSE_SHA256:?}"

readonly LOG=/var/log/vortex-user-data.log
readonly DATA_DIR=/var/lib/docker/volumes
readonly REPO_URL=https://github.com/caiocv18/vortex.git
readonly REPO_DIR=/opt/vortex

exec >>"$LOG" 2>&1

log() {
  printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"
}

# $BASH_COMMAND names the failing command. $LINENO counts the 4-line header that
# Terraform prepends, so it is 4 more than the line in this file.
trap 'rc=$?; log "abort: $BASH_COMMAND exited $rc (line $LINENO of the user data)"' ERR

# SSH off first, before any step that can abort: user data never runs twice, so
# a failed first boot must not leave sshd on. Access goes through Session Manager.
log "disable sshd"
systemctl disable --now sshd

if [ "$(uname -m)" != aarch64 ]; then
  log "abort: $(uname -m) instance, but the compose checksum is for aarch64"
  exit 1
fi

log "install docker and git"
dnf install -y docker git

# 1. Wait for the data volume. Terraform attaches it after the instance starts.
device="/dev/disk/by-id/nvme-Amazon_Elastic_Block_Store_${DATA_VOLUME_SERIAL}"
log "wait for the data volume at $device"
for _ in $(seq 1 60); do
  [ -e "$device" ] && break
  sleep 5
done
if [ ! -e "$device" ]; then
  log "abort: data volume $DATA_VOLUME_SERIAL did not show up within 5 minutes"
  exit 1
fi
dev=$(readlink -f "$device")

# 2. Guard: format the volume only when it is blank.
#    blkid -p probes the device itself (no cache) and exits with
#      0  a signature was found (a filesystem, a partition table, a RAID member...)
#      2  nothing was found, OR the device could not be opened or read: blkid can't
#         tell these apart, so mkfs.xfs below probes again before writing anything
#      8  ambivalent result (more than one signature); 4 usage or other error
#    Under set -e, a plain blkid call (or $(blkid ...)) would stop the script on the
#    code 2 of a blank volume, so the exit code is captured with || instead.
rc=0
blkid -p "$dev" >/dev/null 2>&1 || rc=$?
case "$rc" in
  0)
    fstype=$(blkid -p -s TYPE -o value "$dev" || true)
    if [ "$fstype" != xfs ]; then
      log "abort: $dev holds ${fstype:-a non-filesystem signature} instead of xfs; leaving it untouched"
      exit 1
    fi
    log "xfs found, mounting $dev"
    ;;
  2)
    log "blank, formatting $dev as xfs"
    # Never add -f: mkfs.xfs probes again and refuses when it finds a signature or
    # cannot probe the device. That is the lock that holds when code 2 came from a
    # failed probe rather than a blank volume.
    mkfs.xfs "$dev"
    ;;
  *)
    log "abort: blkid -p exited $rc on $dev; leaving it untouched"
    exit 1
    ;;
esac

# 3. Mount by UUID, which survives device renames. nofail keeps a boot without the
#    volume from hanging; the drop-in keeps docker down until the volume is mounted,
#    so containers never write their volumes to the root disk.
uuid=$(blkid -p -s UUID -o value "$dev")
mkdir -p "$DATA_DIR"
if ! grep -q "^UUID=$uuid " /etc/fstab; then
  printf 'UUID=%s %s xfs defaults,nofail 0 2\n' "$uuid" "$DATA_DIR" >>/etc/fstab
fi
systemctl daemon-reload
findmnt -rn "$DATA_DIR" >/dev/null || mount "$DATA_DIR"
# The volume can only grow (data_volume_size_gb). Grow the filesystem online to
# match; when it already fills the volume, this changes nothing.
xfs_growfs -d "$DATA_DIR" >/dev/null || log "warning: xfs_growfs on $DATA_DIR exited $?"
install -d -m 0755 /etc/systemd/system/docker.service.d
printf '[Unit]\nRequiresMountsFor=%s\n' "$DATA_DIR" >/etc/systemd/system/docker.service.d/10-volumes.conf
systemctl daemon-reload
log "data volume $uuid mounted at $DATA_DIR"

# 4. 2 GiB of swap: the t4g.small has 2 GiB of memory for two JVMs and a database.
if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  chmod 600 /swapfile
  mkswap /swapfile
  echo '/swapfile none swap defaults 0 0' >>/etc/fstab
fi
swapon --show=NAME --noheadings | grep -qx /swapfile || swapon /swapfile

# 5. Docker Compose as a CLI plugin, checked against the pinned SHA-256.
log "install docker compose $COMPOSE_VERSION"
compose_tmp=$(mktemp)
curl --proto '=https' --tlsv1.2 -fsSL --retry 3 -o "$compose_tmp" \
  "https://github.com/docker/compose/releases/download/v${COMPOSE_VERSION}/docker-compose-linux-aarch64"
echo "${COMPOSE_SHA256}  $compose_tmp" | sha256sum -c -
install -D -m 0755 "$compose_tmp" /usr/local/lib/docker/cli-plugins/docker-compose
rm -f "$compose_tmp"

# 6. Docker on, now that its volumes have a home.
systemctl enable --now docker

# 7. Only infra/cloud from the default branch: shallow, sparse and without blobs
#    outside it.
log "clone infra/cloud into $REPO_DIR"
git clone --quiet --depth 1 --filter=blob:none --sparse "$REPO_URL" "$REPO_DIR"
git -C "$REPO_DIR" sparse-checkout set infra/cloud
if [ -d "$REPO_DIR/infra/cloud" ]; then
  log "infra/cloud checked out at $(git -C "$REPO_DIR" rev-parse --short HEAD)"
else
  log "warning: infra/cloud is not on the default branch yet; nothing to run from $REPO_DIR"
fi

log "done"
