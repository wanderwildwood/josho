package com.wanderwildwood.josho.engine

/** Why a name typed for a score cannot be used. */
enum class NameProblem { Empty, Taken, Unusable }

private const val LY = ".ly"

/** A name as typed, made a score's file name: trimmed, and ending in .ly whether or not it was typed. */
fun scoreName(typed: String): String {
    val name = typed.trim()
    return if (name.endsWith(LY, ignoreCase = true)) name.dropLast(LY.length) + LY else name + LY
}

/**
 * What is wrong with [typed] as the name of a new score among files named [taken], or null when
 * it can be used. Names are compared without regard to case, as a memory card compares them.
 */
fun nameProblem(typed: String, taken: Collection<String>): NameProblem? {
    val name = scoreName(typed)
    return when {
        name.dropLast(LY.length).isBlank() -> NameProblem.Empty
        name.any { it == '/' || it == '\\' || it.code < 32 } -> NameProblem.Unusable
        taken.any { it.equals(name, ignoreCase = true) } -> NameProblem.Taken
        else -> null
    }
}

private val numbered = Regex("""^(.*\S) (\d{1,6})$""")

/** The next name after [name] that is not in [taken]: "song.ly" gives "song 2.ly", "song 2.ly" gives "song 3.ly". */
fun nextFreeName(name: String, taken: Collection<String>): String {
    val dot = name.lastIndexOf('.')
    val stem = if (dot > 0) name.substring(0, dot) else name
    val ending = if (dot > 0) name.substring(dot) else LY
    val match = numbered.matchEntire(stem)
    val base = match?.groupValues?.get(1) ?: stem
    var number = (match?.groupValues?.get(2)?.toInt() ?: 1) + 1
    while (taken.any { it.equals("$base $number$ending", ignoreCase = true) }) number++
    return "$base $number$ending"
}

/** What a compile writes beside a score named [score]: its PDF and, if it asks for one, its MIDI. */
fun byProductNames(score: String): List<String> {
    val base = score.substringBeforeLast('.')
    return listOf("$base.pdf", "$base.midi")
}

/** The engraved PDF beside [score], if a compile has left one. */
fun pdfBeside(folder: Folder, score: Doc): Doc? {
    val name = byProductNames(score.name).first()
    return folder.beside(score).firstOrNull { it.name == name }
}

/**
 * Writes [text] (by default what [score] holds as last saved) to a new score [name] in the same
 * folder, and returns it. Refuses a name that [nameProblem] would, so nothing is ever replaced.
 */
fun copyScore(folder: Folder, score: Doc, name: String, text: String = folder.read(score)): Doc? {
    val problem = nameProblem(name, folder.beside(score).map { it.name })
    require(problem == null) { "The name cannot be used: $problem" }
    return folder.createBeside(score, scoreName(name), text)
}

/**
 * Deletes [score] and what compiling it left beside it (the PDF and the MIDI of the same name).
 * False when the score itself could not be deleted; then nothing else is touched.
 */
fun deleteScore(folder: Folder, score: Doc): Boolean {
    val names = byProductNames(score.name)
    val leftovers = folder.beside(score).filter { it.name in names }
    if (!folder.delete(score)) return false
    leftovers.forEach { runCatching { folder.delete(it) } }
    return true
}
