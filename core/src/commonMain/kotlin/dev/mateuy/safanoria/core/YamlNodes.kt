package dev.mateuy.safanoria.core

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlException
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.YamlTaggedNode
import okio.Path

private val yaml = Yaml.default

/**
 * A parsed YAML block and where it starts in its file. kaml lines are 1-based within the block;
 * [lineOf] turns them into file lines.
 */
internal class YamlBlock(val file: Path?, val root: YamlNode, private val firstLine: Int) {
    fun lineOf(node: YamlNode): Int = node.location.line + firstLine - 1
    fun columnOf(node: YamlNode): Int = node.location.column

    fun diagnostic(node: YamlNode, code: String, message: String): Diagnostic =
        Diagnostic(file, lineOf(node), columnOf(node), code, message)
}

/** Parses [text], whose first line is line [firstLine] of [file]. */
internal fun parseYamlBlock(file: Path?, text: String, firstLine: Int): Pair<YamlBlock?, Diagnostic?> = try {
    YamlBlock(file, yaml.parseToYamlNode(text), firstLine) to null
} catch (e: YamlException) {
    null to Diagnostic(file, e.line + firstLine - 1, e.column, "yaml-syntax", e.message ?: "invalid YAML")
}

internal fun YamlNode.unwrap(): YamlNode = if (this is YamlTaggedNode) innerNode.unwrap() else this

/** The entry for [key] in a map, as (key node, value node). */
internal fun YamlNode.entry(key: String): Pair<YamlScalar, YamlNode>? =
    (unwrap() as? YamlMap)?.entries?.entries?.firstOrNull { it.key.content == key }?.let { it.key to it.value }

internal fun YamlNode.get(key: String): YamlNode? = entry(key)?.second

/** The scalar text, or null for null, maps and lists. */
internal fun YamlNode?.text(): String? = (this?.unwrap() as? YamlScalar)?.content

internal fun YamlNode?.isNull(): Boolean = this == null || unwrap() is YamlNull

internal fun YamlNode?.textList(): List<String>? = (this?.unwrap() as? YamlList)?.items?.mapNotNull { it.text() }

internal fun YamlNode?.mapEntries(): List<Pair<YamlScalar, YamlNode>> =
    (this?.unwrap() as? YamlMap)?.entries?.entries?.map { it.key to it.value } ?: emptyList()
