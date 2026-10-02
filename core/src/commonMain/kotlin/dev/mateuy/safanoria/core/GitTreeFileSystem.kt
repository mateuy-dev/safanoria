package dev.mateuy.safanoria.core

import okio.Buffer
import okio.FileHandle
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Sink
import okio.Source

/**
 * Blob contents by object id, read once. Shared by the [GitTreeFileSystem]s of several branches,
 * where most tickets are the same blob.
 */
public class BlobCache(private val git: Git) {
    private val texts = mutableMapOf<String, String>()

    /** How many blobs have been read from git (for tests of the cost). */
    public val reads: Int get() = texts.size

    public fun text(id: String): String = texts.getOrPut(id) { git.blob(id) }
}

/**
 * A read-only view of commit [ref], as if it were checked out at [root]: `Repository(root, it)`
 * reads that branch's config and tickets without touching the working tree. Directories are
 * listed when first used; contents come from [blobs]. Paths outside [root] don't exist.
 */
public class GitTreeFileSystem(
    private val git: Git,
    public val ref: String,
    private val root: Path,
    private val blobs: BlobCache = BlobCache(git),
) : FileSystem() {
    private val trees = mutableMapOf<String, Map<String, TreeEntry>?>()

    /** The path relative to [root] with `/`, `""` for the root; null outside it. */
    private fun relative(path: Path): String? {
        val p = (if (path.isAbsolute) path else root / path).normalized()
        val inside = root.segments
        if (p.root != root.root || p.segments.size < inside.size || p.segments.subList(0, inside.size) != inside) return null
        return p.segments.drop(inside.size).joinToString("/")
    }

    private fun tree(dir: String): Map<String, TreeEntry>? =
        trees.getOrPut(dir) { git.tree(ref, dir)?.associateBy { it.name } }

    private fun entry(relative: String): TreeEntry? {
        val slash = relative.lastIndexOf('/')
        val dir = if (slash < 0) "" else relative.substring(0, slash)
        return tree(dir)?.get(relative.substring(slash + 1))
    }

    override fun canonicalize(path: Path): Path {
        val p = if (path.isAbsolute) path else root / path
        if (metadataOrNull(p) == null) throw FileNotFoundException("no such file in $ref: $path")
        return p.normalized()
    }

    override fun metadataOrNull(path: Path): FileMetadata? {
        val rel = relative(path) ?: return null
        if (rel == "") return FileMetadata(isDirectory = true)
        val e = entry(rel) ?: return null
        return when {
            e.type == "tree" -> FileMetadata(isDirectory = true)
            e.type == "blob" && e.mode != "120000" -> FileMetadata(isRegularFile = true, size = e.size)
            else -> FileMetadata() // symlinks and submodules: neither files nor directories here
        }
    }

    override fun listOrNull(dir: Path): List<Path>? {
        val rel = relative(dir) ?: return null
        val base = if (dir.isAbsolute) dir else root / dir
        return tree(rel)?.keys?.sorted()?.map { base / it }
    }

    override fun list(dir: Path): List<Path> = listOrNull(dir) ?: throw FileNotFoundException("no such directory in $ref: $dir")

    override fun source(file: Path): Source {
        val e = relative(file)?.let(::entry)?.takeIf { it.type == "blob" } ?: throw FileNotFoundException("no such file in $ref: $file")
        return Buffer().writeUtf8(blobs.text(e.id))
    }

    private fun readOnly(): Nothing = throw IOException("$ref is read-only here: it isn't checked out")

    override fun openReadOnly(file: Path): FileHandle = readOnly()
    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle = readOnly()
    override fun sink(file: Path, mustCreate: Boolean): Sink = readOnly()
    override fun appendingSink(file: Path, mustExist: Boolean): Sink = readOnly()
    override fun createDirectory(dir: Path, mustCreate: Boolean): Unit = readOnly()
    override fun atomicMove(source: Path, target: Path): Unit = readOnly()
    override fun delete(path: Path, mustExist: Boolean): Unit = readOnly()
    override fun createSymlink(source: Path, target: Path): Unit = readOnly()
}
