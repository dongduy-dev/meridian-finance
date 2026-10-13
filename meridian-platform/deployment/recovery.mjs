// Manual matched backup / isolated restore. Node 22, local Docker, no provider access.
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { chmodSync, closeSync, createReadStream, existsSync, lstatSync, mkdirSync, openSync, readFileSync,
  readdirSync, realpathSync, writeFileSync } from 'node:fs'
import { dirname, isAbsolute, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const sourceRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const within = (parent, child) => {
  const path = relative(parent, child)
  return path === '' || (!path.startsWith('..') && !isAbsolute(path))
}

function docker(args, outputFile) {
  const fd = outputFile ? openSync(outputFile, 'wx', 0o600) : null
  try {
    const r = spawnSync('docker', args, { encoding: 'utf8',
      stdio: ['ignore', fd ?? 'pipe', 'pipe'], timeout: 300000, maxBuffer: 8 * 1024 * 1024 })
    assert.equal(r.status, 0, 'Docker recovery operation failed; keep traffic stopped and inspect privately')
    return r.stdout?.trim()
  } finally { if (fd !== null) closeSync(fd) }
}
const inspect = id => JSON.parse(docker(['inspect', id]))[0]
export async function hash(file) {
  const digest = createHash('sha256')
  try {
    for await (const chunk of createReadStream(file, { highWaterMark: 64 * 1024 })) digest.update(chunk)
    return digest.digest('hex')
  } catch {
    throw new Error('Backup checksum could not be calculated; inspect privately')
  }
}
const checksumFiles = ['database.dump', 'documents.tgz', 'private.tgz', 'release.json']
function safeDirectory(path, fresh = false) {
  assert.ok(isAbsolute(path), 'Use absolute paths')
  if (fresh) { assert.ok(!existsSync(path), 'Destination must not exist'); mkdirSync(path, { mode: 0o700 }) }
  assert.ok(lstatSync(path).isDirectory() && !lstatSync(path).isSymbolicLink())
  assert.equal(realpathSync(path), resolve(path), 'No symlinked parent paths')
  chmodSync(path, 0o700)
}
function regularTree(path) {
  for (const entry of readdirSync(path)) {
    const child = join(path, entry), stat = lstatSync(child)
    assert.ok(!stat.isSymbolicLink(), 'Private backup must not follow links')
    if (stat.isDirectory()) regularTree(child)
    else assert.ok(stat.isFile() && stat.nlink === 1, 'Only private regular files are accepted')
  }
}
export async function verifyBackup(directory) {
  try {
    safeDirectory(directory)
    const manifest = JSON.parse(readFileSync(join(directory, 'matched-set.json'), 'utf8'))
    assert.deepEqual(Object.keys(manifest.checksums).sort(), [...checksumFiles].sort())
    for (const name of checksumFiles) assert.equal(await hash(join(directory, name)), manifest.checksums[name], 'Backup checksum mismatch')
    return manifest
  } catch {
    throw new Error('Backup checksum verification failed; inspect privately')
  }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
const [action, ...args] = process.argv.slice(2)
if (action === 'backup') {
  const [project, backendId, dbId, privateDirectory, destination, sourceRevision, confirmation] = args
  assert.ok(project === 'meridian-prod' || /^cp4test-[a-z0-9-]+$/.test(project))
  assert.equal(confirmation, `BACKUP-${project}`, 'Explicit target confirmation required')
  assert.match(sourceRevision ?? '', /^[0-9a-f]{40}$/)
  assert.ok(!within(sourceRoot, privateDirectory) && !within(sourceRoot, destination), 'Private state/backup must stay outside source')
  assert.ok(!within(privateDirectory, destination) && !within(destination, privateDirectory), 'Backup/private directories must not overlap')
  safeDirectory(privateDirectory); regularTree(privateDirectory)
  assert.ok(existsSync(join(privateDirectory, 'runtime.env')) && existsSync(join(privateDirectory, 'tunnel-token')),
    'Complete private configuration and Tunnel credential required')
  safeDirectory(destination, true)
  const backend = inspect(backendId), db = inspect(dbId)
  const labels = c => c.Config.Labels ?? {}
  assert.equal(labels(backend)['com.docker.compose.project'], project)
  assert.equal(labels(backend)['com.docker.compose.service'], 'meridian-platform')
  assert.equal(labels(db)['com.docker.compose.project'], project)
  assert.equal(labels(db)['com.docker.compose.service'], 'postgres')
  assert.equal(db.State.Running, true, 'Backup requires the database running')
  const mount = backend.Mounts.find(m => m.Destination === '/var/lib/meridian/documents')
  assert.equal(mount?.Type, 'volume')
  assert.equal(mount.Name, `${project}_document_data`)
  const pgMount = db.Mounts.find(m => m.Destination === '/var/lib/postgresql/data')
  assert.equal(pgMount?.Name, `${project}_postgres_data`)
  const images = JSON.parse(docker(['image', 'inspect', backend.Image]))[0]
  const embedded = images.Config.Labels?.['org.opencontainers.image.revision']
  if (project === 'meridian-prod') assert.notEqual(embedded, 'development', 'A development image is not a production release')
  if (embedded && embedded !== 'development') assert.equal(embedded, sourceRevision)
  // Close public ingress first and stop every application writer before the consistency cut.
  const ids = docker(['ps', '-q', '--filter', `label=com.docker.compose.project=${project}`]).split(/\s+/).filter(Boolean)
  const ingress = ids.filter(id => labels(inspect(id))['com.docker.compose.service'] === 'cloudflared')
  if (ingress.length) docker(['stop', ...ingress])
  docker(['stop', backendId])
  const remainingWriters = docker(['ps', '-q', '--filter', `volume=${mount.Name}`])
  assert.equal(remainingWriters, '', 'Document writers must be stopped')
  const release = { sourceRevision, imageId: backend.Image, sourceAttribution: embedded && embedded !== 'development' ? 'image-label' : 'operator-confirmed-pre-CP4',
    schemaVersion: docker(['exec', dbId, 'sh', '-eu', '-c',
      'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAX -c "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"']) }
  writeFileSync(join(destination, 'release.json'), JSON.stringify(release, null, 2), { mode: 0o600, flag: 'wx' })
  docker(['exec', dbId, 'sh', '-eu', '-c', 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom'], join(destination, 'database.dump'))
  const archive = (source, target) => docker(['run', '--rm', '--network', 'none', '--read-only',
    '--mount', `${source},target=/input,readonly`, '--mount', `type=bind,source=${destination},target=/backup`,
    'postgres:16', 'tar', '-czpf', `/backup/${target}`, '-C', '/input', '.'])
  archive(`type=volume,source=${mount.Name}`, 'documents.tgz')
  archive(`type=bind,source=${privateDirectory}`, 'private.tgz')
  for (const file of checksumFiles) chmodSync(join(destination, file), 0o600)
  const checksums = {}
  for (const file of checksumFiles) checksums[file] = await hash(join(destination, file))
  writeFileSync(join(destination, 'matched-set.json'), JSON.stringify({ project, createdAt: new Date().toISOString(), checksums }, null, 2),
    { flag: 'wx', mode: 0o600 })
  await verifyBackup(destination)
  console.log('PASS: matched backup checksums; application/ingress remain stopped pending operator review')
} else if (action === 'verify') {
  await verifyBackup(args[0]); console.log('PASS: matched backup checksums')
} else if (action === 'restore') {
  const [directory, project] = args
  await verifyBackup(directory)
  // This helper can never restore into production or a preexisting project/volume.
  assert.match(project ?? '', /^cp4restore-[a-z0-9-]+$/)
  const restoreVolumes = [`${project}_postgres_data`, `${project}_document_data`]
  for (const volume of restoreVolumes) {
    assert.notEqual(spawnSync('docker', ['volume', 'inspect', volume]).status, 0, 'Restore refuses existing volumes')
  }
  const restoredPrivate = join(directory, `${project}-private`)
  assert.ok(!existsSync(restoredPrivate), 'Restore refuses existing private destination')
  for (const volume of restoreVolumes) {
    docker(['volume', 'create', '--label', `com.docker.compose.project=${project}`, volume])
  }
  safeDirectory(restoredPrivate, true)
  docker(['run', '--rm', '--network', 'none', '--read-only', '--mount', `type=bind,source=${directory},target=/backup,readonly`,
    '--mount', `type=bind,source=${restoredPrivate},target=/private`, 'postgres:16',
    'tar', '-xzpf', '/backup/private.tgz', '-C', '/private'])
  regularTree(restoredPrivate)
  const passwordLine = readFileSync(join(restoredPrivate, 'runtime.env'), 'utf8').split(/\r?\n/)
    .find(line => /^POSTGRES_PASSWORD=.+$/.test(line))
  assert.ok(passwordLine, 'Restored private configuration requires the database password')
  const databaseEnvironment = join(restoredPrivate, 'restore-database.env')
  writeFileSync(databaseEnvironment, passwordLine + '\n', { mode: 0o600, flag: 'wx' })
  docker(['run', '--rm', '--network', 'none', '--read-only', '--mount', `type=bind,source=${directory},target=/backup,readonly`,
    '--mount', `type=volume,source=${project}_document_data,target=/documents`, 'postgres:16',
    'tar', '-xzpf', '/backup/documents.tgz', '-C', '/documents'])
  // No ports, no connection to live networks, no provider/Tunnel, keys remain in private files.
  const dbName = `${project}-postgres`
  docker(['run', '-d', '--name', dbName, '--network', 'none', '--label', `com.docker.compose.project=${project}`,
    '--env-file', databaseEnvironment, '-e', 'POSTGRES_USER=meridian_user', '-e', 'POSTGRES_DB=meridian_db',
    '--mount', `type=volume,source=${project}_postgres_data,target=/var/lib/postgresql/data`,
    '--mount', `type=bind,source=${directory},target=/backup,readonly`, 'postgres:16'])
  docker(['exec', dbName, 'sh', '-eu', '-c',
    'i=0; until pg_isready -U meridian_user -d meridian_db >/dev/null; do i=$((i+1)); test "$i" -lt 60; sleep 1; done; pg_restore -U meridian_user -d meridian_db --exit-on-error /backup/database.dump'])
  const release = JSON.parse(readFileSync(join(directory, 'release.json'), 'utf8'))
  assert.equal(docker(['exec', dbName, 'psql', '-U', 'meridian_user', '-d', 'meridian_db', '-tAX', '-c',
    'SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1']), release.schemaVersion)
  console.log(`PASS: isolated DB/document/private restore into ${project}; application readback still required`)
} else {
  throw new Error('Usage: recovery.mjs backup PROJECT BACKEND_ID DB_ID PRIVATE_DIR NEW_ABSOLUTE_DIR SOURCE_SHA BACKUP-PROJECT | verify DIR | restore DIR NEW_cp4restore-PROJECT')
}
}
