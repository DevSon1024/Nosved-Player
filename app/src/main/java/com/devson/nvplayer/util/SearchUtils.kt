package com.devson.nvplayer.util

object SearchUtils {

    private val DELIMITER_REGEX = Regex("[._\\-\\[\\]()]")
    private val WHITESPACE_REGEX = Regex("\\s+")

    /**
     * Normalizes text by replacing delimiter characters (., _, -, [, ], (, )) with spaces,
     * collapsing consecutive whitespace into a single space, trimming, and lowercasing.
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""
        return text.replace(DELIMITER_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .lowercase()
    }

    /**
     * Splits query into distinct non-empty lowercased tokens.
     */
    fun tokenize(query: String): List<String> {
        val normalized = normalize(query)
        if (normalized.isBlank()) return emptyList()
        return normalized.split(" ").filter { it.isNotBlank() }.distinct()
    }

    data class MatchScore(
        val exactPhraseMatch: Boolean,
        val titleLength: Int
    ) : Comparable<MatchScore> {
        override fun compareTo(other: MatchScore): Int {
            // 1. Exact phrase match ranked higher
            if (this.exactPhraseMatch != other.exactPhraseMatch) {
                return if (this.exactPhraseMatch) -1 else 1
            }
            // 2. Shorter title length ranked higher (closer match)
            return this.titleLength.compareTo(other.titleLength)
        }
    }

    fun calculateScore(normalizedTitle: String, queryTokens: List<String>, normalizedQuery: String): MatchScore? {
        if (queryTokens.isEmpty()) return null

        // All entered words must be in the title (placing does not matter)
        for (token in queryTokens) {
            if (!normalizedTitle.contains(token)) {
                return null
            }
        }

        val exactPhrase = normalizedQuery.isNotBlank() && normalizedTitle.contains(normalizedQuery)

        return MatchScore(
            exactPhraseMatch = exactPhrase,
            titleLength = normalizedTitle.length
        )
    }

    /**
     * Filters and ranks elements by multi-word query tokens delimiter-agnostically.
     * All entered words must be in the title, regardless of word placing.
     */
    fun <T> filterAndRank(
        items: List<T>,
        query: String,
        titleSelector: (T) -> String
    ): List<T> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return items

        val normalizedQuery = normalize(query)

        return items
            .mapNotNull { item ->
                val title = titleSelector(item)
                val normalizedTitle = normalize(title)
                val score = calculateScore(normalizedTitle, tokens, normalizedQuery)
                if (score != null) {
                    Pair(item, score)
                } else {
                    null
                }
            }
            .sortedWith(compareBy { it.second })
            .map { it.first }
    }

    /**
     * Generates top matching suggestions for the live search suggestion panel.
     * All entered words must be in the title, regardless of word placing.
     */
    fun getSuggestions(
        titles: List<String>,
        query: String,
        limit: Int = 6
    ): List<String> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()

        val normalizedQuery = normalize(query)

        return titles
            .distinct()
            .mapNotNull { title ->
                val normalizedTitle = normalize(title)
                val score = calculateScore(normalizedTitle, tokens, normalizedQuery)
                if (score != null) {
                    Pair(title, score)
                } else {
                    null
                }
            }
            .sortedWith(compareBy { it.second })
            .map { it.first }
            .take(limit)
    }

    /**
     * Checks if a title matches a query delimiter-agnostically.
     * All entered words must be present in the title, regardless of word placing.
     */
    fun matches(title: String, query: String): Boolean {
        if (query.isBlank()) return true
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return true
        val normalizedTitle = normalize(title)
        return tokens.all { normalizedTitle.contains(it) }
    }
}
