package com.devson.nvplayer.data.parser

sealed class ParsedMediaInfo {
    abstract val rawTitle: String
    abstract val cleanedTitle: String

    data class TvShow(
        override val rawTitle: String,
        override val cleanedTitle: String,
        val seasonNumber: Int,
        val episodeNumber: Int,
        val year: Int? = null
    ) : ParsedMediaInfo()


    data class Movie(
        override val rawTitle: String,
        override val cleanedTitle: String,
        val year: Int? = null
    ) : ParsedMediaInfo()

    data class Unclassified(
        override val rawTitle: String,
        override val cleanedTitle: String
    ) : ParsedMediaInfo()
}
