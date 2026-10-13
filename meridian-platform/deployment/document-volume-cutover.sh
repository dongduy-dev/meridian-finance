#!/bin/sh
# Run only in the constrained one-shot helper from MER-DEP-003 after matched backup.
set -eu
test "${1:-}" = 'CONFIRM-DOCUMENT-OWNERSHIP-10001' || exit 2
test "$(id -u)" = 0 || exit 2
root=/var/lib/meridian/documents
test -d "$root" && test ! -L "$root" || exit 2
# Require exactly this volume mount, never a parent or another filesystem path.
awk '$2 == "/var/lib/meridian/documents" {found=1} END {exit !found}' /proc/mounts
test -z "$(find "$root" -type l -print -quit)" || exit 2
test -z "$(find "$root" -type f -links +1 -print -quit)" || exit 2
test -z "$(find "$root" ! -type f ! -type d -print -quit)" || exit 2
for path in "$root"/* "$root"/.[!.]* "$root"/..?*; do
    test -e "$path" || continue
    case "$path" in "$root/staging"|"$root/objects") test -d "$path" ;; *) exit 2 ;; esac
done
# Existing bytes and paths remain intact; only ownership/modes change.
mkdir -p "$root/staging" "$root/objects"
find "$root" -type d -exec chown 10001:10001 {} + -exec chmod 700 {} +
find "$root" -type f -exec chown 10001:10001 {} + -exec chmod 600 {} +
echo 'PASS: validated document mount assigned to UID/GID 10001'
