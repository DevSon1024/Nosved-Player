package com.devson.nvplayer.data.parser

/**
 * Pure Kotlin parser utility for extracting structured media metadata from local filenames.
 *
 * Supports:
 * - Standard and complex TV Show formats (SxxExx, 1x05, Season X Episode Y) with scene tags.
 * - Absolute Episode Show formats (e.g. release group prefixes, hyphenated episode numbering).
 * - Movies with release year and scene tags, guarded by a minimum duration threshold of 45 mins.
 */
object MediaFilenameParser {

    /**
     * Common video file extensions to strip before regex processing.
     */
    private val EXTENSION_REGEX = Regex(
        """\.(mkv|mp4|avi|mov|wmv|flv|webm|m4v|ts|m2ts|3gp|vob|ogm|rmvb)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Leading release group or site prefixes (e.g., "[Vegamovies.To].", "AnimePahe_", "[Fansub]").
     */
    private val LEADING_GROUP_PREFIX_REGEX = Regex(
        """^(?:\[[^\]]+\]|\([^\)]+\)|AnimePahe_|Vegamovies[\.\w]*|HDToons[\.\w]*)[._\s-]*""",
        RegexOption.IGNORE_CASE
    )

    /**
     * TV Show SxxExx Regex:
     * Capture Group 1: Leading release group prefix (e.g. "[Vegamovies.To]")
     * Capture Group 2: Raw Title (e.g. "My Hero Academia", "Rick.and.Morty", "Vinland.Saga")
     * Capture Group 3: Optional 4-digit Year (e.g. "2021")
     * Capture Group 4: Season Number (e.g. "07", "1")
     * Capture Group 5: Episode Number (e.g. "04", "11")
     */
    private val TV_SXX_EXX_REGEX = Regex(
        """^(?:\[([^\]]+)\]|\(([^\)]+)\))?[._\s-]*(.+?)(?:[._\s-]+(?:(\d{4}))[._\s-]+)?[._\s-]*[Ss](\d{1,2})[._\s-]*[Ee](\d{1,4})(?:[Ee\-\d]+)?(?:\b|[._\s-]|$)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * TV 1x05 Cross Format Regex:
     * Capture Group 1: Raw Title
     * Capture Group 2: Optional 4-digit Year
     * Capture Group 3: Season Number
     * Capture Group 4: Episode Number
     */
    private val TV_CROSS_FORMAT_REGEX = Regex(
        """^(?:\[[^\]]+\]|\([^\)]+\))?[._\s-]*(.+?)(?:[._\s-]+(?:(\d{4}))[._\s-]+)?[._\s-]+(\d{1,2})[xX](\d{1,4})(?:\b|[._\s-]|$)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * TV "Season X Episode Y" Text Regex:
     * Capture Group 1: Raw Title
     * Capture Group 2: Optional 4-digit Year
     * Capture Group 3: Season Number
     * Capture Group 4: Episode Number
     */
    private val TV_SEASON_EPISODE_TEXT_REGEX = Regex(
        """^(?:\[[^\]]+\]|\([^\)]+\))?[._\s-]*(.+?)(?:[._\s-]+(?:(\d{4}))[._\s-]+)?[._\s-]+[Ss]eason\s*(\d{1,2})[._\s-]+[Ee]pisode\s*(\d{1,4})(?:\b|[._\s-]|$)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Show Format with Release Group & Absolute Episode:
     * Capture Group 1: Release group in brackets [Fansub]
     * Capture Group 2: Release group in parentheses (Fansub)
     * Capture Group 3: Show Title
     * Capture Group 4: Optional Season indicator
     * Capture Group 5: Absolute Episode Number (e.g. "04", "102")
     */
    private val SHOW_GROUP_EPISODE_REGEX = Regex(
        """^(?:\[([^\]]+)\]|\(([^\)]+)\))\s*(.+?)(?:[._\s-]+[Ss](\d{1,2})|[._\s-]+Season\s*(\d{1,2}))?\s*-\s*(\d{1,4}(?:\.\d+)?)(?:v\d+)?(?:\s*\[|\s*\(|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Show Absolute Episode with Delimited Dash (e.g. AnimePahe_Show_Title_-_04_720p or Show Title - 04 [1080p]):
     * Capture Group 1: Show Title
     * Capture Group 2: Absolute Episode Number
     */
    private val SHOW_ABSOLUTE_DASH_REGEX = Regex(
        """^(?:\[[^\]]*\]|\([^\)]*\)|AnimePahe_)?[._\s-]*(.+?)[._\s]+(?:-|–|—)[._\s]+(\d{1,4}(?:\.\d+)?)(?:v\d+)?[._\s]*(?:.*)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Show Absolute Episode with Trailing Quality Tag (e.g. Title-2-720p-h1x):
     * Capture Group 1: Show Title
     * Capture Group 2: Absolute Episode Number
     */
    private val SHOW_ABSOLUTE_QUALITY_REGEX = Regex(
        """^(?:\[[^\]]*\]|\([^\)]*\)|AnimePahe_)?[._\s-]*(.+?)[._\s-]+(\d{1,4})[._\s-]+(?:(?:1080|720|480|2160)p|4k|8k|hd|web|bdrip|brrip|dvdrip|bluray|x264|x265|hevc|h1x|subs|dual).*$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Movie with Year in Parentheses/Brackets: e.g. "Oppenheimer (2023) [1080p]"
     * Capture Group 1: Movie Title
     * Capture Group 2: 4-digit Year (19xx or 20xx)
     */
    private val MOVIE_YEAR_PAREN_REGEX = Regex(
        """^(?:\[[^\]]*\]|\([^\)]*\))?[._\s-]*(.*?)[._\s-]*[\(\[]((?:19|20)\d{2})[\)\]](?:.*)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Movie with Year in Scene Format: e.g. "Interstellar.2014.1080p.BluRay.x264"
     * Capture Group 1: Movie Title
     * Capture Group 2: 4-digit Year (19xx or 20xx)
     */
    private val MOVIE_YEAR_SCENE_REGEX = Regex(
        """^(?:\[[^\]]*\]|\([^\)]*\))?[._\s-]*(.*?)[._\s-]+((?:19|20)\d{2})(?:[._\s-]+(?:1080p|720p|480p|2160p|4k|8k|uhd|bluray|bdrip|brrip|web-?dl|webrip|hdrip|dvdrip|hdtv|cam|ts|tc|dvdscr|x264|x265|h264|h265|hevc|av1|xvid|divx|aac|flac|dts|truehd|atmos|proper|repack|remux|unrated|extended|complete|multi|dual|dubbed|subbed).*|[._\s-]*)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Noise tokens and scene tags to remove during title cleanup.
     */
    private val NOISE_TAGS_REGEX = Regex(
        """\b(1080p|720p|480p|2160p|4k|8k|uhd|bluray|bdrip|brrip|dvdrip|web-?dl|webrip|hdrip|hdtv|cam|ts|tc|dvdscr|x264|x265|h264|h265|hevc|av1|xvid|divx|aac|flac|dts|dts-hd|truehd|atmos|ac3|eac3|ddp?5\.1|mp3|opus|proper|repack|remux|unrated|extended|directors\.cut|theatrical|imax|complete|multi(\s*audio)?|dual(\s*audio)?|dubbed|subbed|engsub|esubs?|hin-eng-jap|hindi|english|10bit|8bit|hdr|hdr10\+?|dolby\.?vision|sdr|hdtoonsplay|vegamovies|subsplease|h1x|animepahe)\b""",
        RegexOption.IGNORE_CASE
    )

    private val DOMAIN_SUFFIX_REGEX = Regex(
        """\b(?:vegamovies\.(?:is|to|in|org|com|net)|hdtoonsplay|subsplease|animepahe)\b""",
        RegexOption.IGNORE_CASE
    )

    private val BRACKETED_NOISE_REGEX = Regex("""\[[^\]]*\]|\([^\)]*\)""")
    private val YEAR_REGEX = Regex("""\b(?:19|20)\d{2}\b""")
    private val SEPARATORS_REGEX = Regex("""[\._\-]+""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")

    /**
     * Minimum duration (45 minutes in milliseconds) to classify a video as a Movie.
     * Prevents short clips and tutorials containing years from being classified as movies.
     */
    private const val MIN_MOVIE_DURATION_MILLIS = 45 * 60 * 1000L // 2,700,000 ms

    data class ShowTitleAndSeason(
        val title: String,
        val seasonNumber: Int?
    )

    private val SHOW_SEASON_EXPLICIT_REGEX = Regex(
        """^(.*?)[._\s-]+(?:[Ss]eason|S)[._\s-]*(\d{1,2})$""",
        RegexOption.IGNORE_CASE
    )

    private val SHOW_SEASON_ORDINAL_REGEX = Regex(
        """^(.*?)[._\s-]+(\d{1,2})(?:st|nd|rd|th)[._\s-]*(?:[Ss]eason)?$""",
        RegexOption.IGNORE_CASE
    )

    private val SHOW_SEASON_ROMAN_REGEX = Regex(
        """^(.*?)[._\s-]+(I|II|III|IV|V|VI|VII|VIII|IX|X|XI|XII|XIII|XIV|XV)$""",
        RegexOption.IGNORE_CASE
    )

    private val SHOW_SEASON_NUMBER_REGEX = Regex(
        """^(.*?)[._\s-]+(\d{1,2})$""",
        RegexOption.IGNORE_CASE
    )

    fun romanToInt(roman: String): Int? {
        val map = mapOf(
            "I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5,
            "VI" to 6, "VII" to 7, "VIII" to 8, "IX" to 9, "X" to 10,
            "XI" to 11, "XII" to 12, "XIII" to 13, "XIV" to 14, "XV" to 15
        )
        return map[roman.uppercase()]
    }

    /**
     * Extracts a show season number from the tail of the title string
     * (e.g. "Title_2", "Title_II", "Title_Season_2", "Title_2nd_Season").
     */
    fun extractShowSeason(rawTitle: String): ShowTitleAndSeason {
        val trimmed = rawTitle.trim()

        // 1. Explicit Season: e.g. "Title_Season_2" or "Title_S2"
        val explicitMatch = SHOW_SEASON_EXPLICIT_REGEX.find(trimmed)
        if (explicitMatch != null && explicitMatch.groupValues[1].isNotBlank()) {
            val base = explicitMatch.groupValues[1]
            val season = explicitMatch.groupValues[2].toIntOrNull()
            if (season != null) return ShowTitleAndSeason(base, season)
        }

        // 2. Ordinal Season: e.g. "Title_2nd_Season" or "Title_2nd"
        val ordinalMatch = SHOW_SEASON_ORDINAL_REGEX.find(trimmed)
        if (ordinalMatch != null && ordinalMatch.groupValues[1].isNotBlank()) {
            val base = ordinalMatch.groupValues[1]
            val season = ordinalMatch.groupValues[2].toIntOrNull()
            if (season != null) return ShowTitleAndSeason(base, season)
        }

        // 3. Roman Numeral Season: e.g. "Title_II", "Title_IV"
        val romanMatch = SHOW_SEASON_ROMAN_REGEX.find(trimmed)
        if (romanMatch != null && romanMatch.groupValues[1].isNotBlank()) {
            val base = romanMatch.groupValues[1]
            val roman = romanMatch.groupValues[2]
            val season = romanToInt(roman)
            if (season != null) return ShowTitleAndSeason(base, season)
        }

        // 4. Trailing number season: e.g. "Title_2" (not a year)
        val numberMatch = SHOW_SEASON_NUMBER_REGEX.find(trimmed)
        if (numberMatch != null && numberMatch.groupValues[1].isNotBlank()) {
            val base = numberMatch.groupValues[1]
            val num = numberMatch.groupValues[2].toIntOrNull()
            if (num != null && num in 1..20) return ShowTitleAndSeason(base, num)
        }

        return ShowTitleAndSeason(trimmed, null)
    }

    /**
     * Parses a raw media filename into a [ParsedMediaInfo] data model.
     *
     * @param rawName The file name (with or without directory path and extension).
     * @param durationMillis Optional media duration in milliseconds for movie validation.
     * @return [ParsedMediaInfo.TvShow], [ParsedMediaInfo.Movie], or [ParsedMediaInfo.Unclassified].
     */
    fun parse(rawName: String, durationMillis: Long = 0L): ParsedMediaInfo {
        val baseName = rawName.substringAfterLast('/').substringAfterLast('\\').trim()
        val nameWithoutExt = EXTENSION_REGEX.replace(baseName, "").trim()

        if (nameWithoutExt.isBlank()) {
            return ParsedMediaInfo.Unclassified(rawTitle = baseName, cleanedTitle = cleanTitle(baseName))
        }

        // 1. Check for standard / complex TV Show SxxExx format (e.g. My Hero Academia-S07E04-720p-[HIN-ENG-JAP]-Vegamovies.is)
        val tvSxxMatch = TV_SXX_EXX_REGEX.find(nameWithoutExt)
        if (tvSxxMatch != null && tvSxxMatch.groupValues[3].isNotBlank()) {
            val rawTitle = tvSxxMatch.groupValues[3]
            val year = tvSxxMatch.groupValues[4].toIntOrNull()
            val seasonNumber = tvSxxMatch.groupValues[5].toIntOrNull() ?: 1
            val episodeNumber = tvSxxMatch.groupValues[6].toIntOrNull() ?: 1

            return ParsedMediaInfo.TvShow(
                rawTitle = rawTitle.trim(),
                cleanedTitle = cleanTitle(rawTitle),
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                year = year
            )
        }

        // 2. Check for TV Show 1x05 Cross format
        val tvCrossMatch = TV_CROSS_FORMAT_REGEX.find(nameWithoutExt)
        if (tvCrossMatch != null && tvCrossMatch.groupValues[1].isNotBlank()) {
            val rawTitle = tvCrossMatch.groupValues[1]
            val year = tvCrossMatch.groupValues[2].toIntOrNull()
            val seasonNumber = tvCrossMatch.groupValues[3].toIntOrNull() ?: 1
            val episodeNumber = tvCrossMatch.groupValues[4].toIntOrNull() ?: 1

            return ParsedMediaInfo.TvShow(
                rawTitle = rawTitle.trim(),
                cleanedTitle = cleanTitle(rawTitle),
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                year = year
            )
        }

        // 3. Check for TV Show "Season X Episode Y" Text format
        val tvTextMatch = TV_SEASON_EPISODE_TEXT_REGEX.find(nameWithoutExt)
        if (tvTextMatch != null && tvTextMatch.groupValues[1].isNotBlank()) {
            val rawTitle = tvTextMatch.groupValues[1]
            val year = tvTextMatch.groupValues[2].toIntOrNull()
            val seasonNumber = tvTextMatch.groupValues[3].toIntOrNull() ?: 1
            val episodeNumber = tvTextMatch.groupValues[4].toIntOrNull() ?: 1

            return ParsedMediaInfo.TvShow(
                rawTitle = rawTitle.trim(),
                cleanedTitle = cleanTitle(rawTitle),
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                year = year
            )
        }

        // 4. Check for Show format with release group [Fansub] Title - 04 [1080p]
        val showGroupMatch = SHOW_GROUP_EPISODE_REGEX.find(nameWithoutExt)
        if (showGroupMatch != null) {
            val rawTitle = showGroupMatch.groupValues[3]
            val seasonStr = showGroupMatch.groupValues[4].ifEmpty { showGroupMatch.groupValues[5] }
            val epStr = showGroupMatch.groupValues[6]
            val parsedSeason = seasonStr.toIntOrNull()
            val epNumber = epStr.toDoubleOrNull()?.toInt() ?: 1

            val seasonInfo = extractShowSeason(rawTitle)
            val finalSeason = parsedSeason ?: seasonInfo.seasonNumber ?: 1

            return ParsedMediaInfo.TvShow(
                rawTitle = rawTitle.trim(),
                cleanedTitle = cleanTitle(seasonInfo.title),
                seasonNumber = finalSeason,
                episodeNumber = epNumber
            )
        }

        // 5. Check for Show absolute episode with dash (e.g. Title - 04 [720p])
        val showDashMatch = SHOW_ABSOLUTE_DASH_REGEX.find(nameWithoutExt)
        if (showDashMatch != null && showDashMatch.groupValues[1].isNotBlank()) {
            val rawTitle = showDashMatch.groupValues[1]
            val epNumber = showDashMatch.groupValues[2].toDoubleOrNull()?.toInt() ?: 1

            if (epNumber !in 1900..2099 && !rawTitle.matches(Regex("""^(19|20)\d{2}$"""))) {
                val seasonInfo = extractShowSeason(rawTitle)
                val finalSeason = seasonInfo.seasonNumber ?: 1

                return ParsedMediaInfo.TvShow(
                    rawTitle = rawTitle.trim(),
                    cleanedTitle = cleanTitle(seasonInfo.title),
                    seasonNumber = finalSeason,
                    episodeNumber = epNumber
                )
            }
        }

        // 6. Check for Movie with (Year) or [Year] (e.g. Oppenheimer (2023))
        val movieParenMatch = MOVIE_YEAR_PAREN_REGEX.find(nameWithoutExt)
        if (movieParenMatch != null && movieParenMatch.groupValues[1].isNotBlank()) {
            val rawTitle = movieParenMatch.groupValues[1]
            val year = movieParenMatch.groupValues[2].toIntOrNull()

            // Guard against short tutorial clips
            if (durationMillis == 0L || durationMillis >= MIN_MOVIE_DURATION_MILLIS) {
                return ParsedMediaInfo.Movie(
                    rawTitle = rawTitle.trim(),
                    cleanedTitle = cleanTitle(rawTitle),
                    year = year
                )
            } else {
                return ParsedMediaInfo.Unclassified(
                    rawTitle = nameWithoutExt,
                    cleanedTitle = cleanTitle(nameWithoutExt)
                )
            }
        }

        // 7. Check for Movie with Year and scene/quality tags (e.g. Interstellar.2014.1080p.BluRay.x264)
        val movieSceneMatch = MOVIE_YEAR_SCENE_REGEX.find(nameWithoutExt)
        if (movieSceneMatch != null && movieSceneMatch.groupValues[1].isNotBlank()) {
            val rawTitle = movieSceneMatch.groupValues[1]
            val year = movieSceneMatch.groupValues[2].toIntOrNull()

            if (durationMillis == 0L || durationMillis >= MIN_MOVIE_DURATION_MILLIS) {
                return ParsedMediaInfo.Movie(
                    rawTitle = rawTitle.trim(),
                    cleanedTitle = cleanTitle(rawTitle),
                    year = year
                )
            } else {
                return ParsedMediaInfo.Unclassified(
                    rawTitle = nameWithoutExt,
                    cleanedTitle = cleanTitle(nameWithoutExt)
                )
            }
        }

        // 8. Check for Show absolute episode with quality suffix (e.g. Title-2-720p-h1x)
        val showQualityMatch = SHOW_ABSOLUTE_QUALITY_REGEX.find(nameWithoutExt)
        if (showQualityMatch != null && showQualityMatch.groupValues[1].isNotBlank()) {
            val rawTitle = showQualityMatch.groupValues[1]
            val epNumber = showQualityMatch.groupValues[2].toIntOrNull() ?: 1

            if (epNumber !in 1900..2099 && !rawTitle.matches(Regex("""^(19|20)\d{2}$"""))) {
                val seasonInfo = extractShowSeason(rawTitle)
                val finalSeason = seasonInfo.seasonNumber ?: 1

                return ParsedMediaInfo.TvShow(
                    rawTitle = rawTitle.trim(),
                    cleanedTitle = cleanTitle(seasonInfo.title),
                    seasonNumber = finalSeason,
                    episodeNumber = epNumber
                )
            }
        }

        // Fallback: Unclassified
        return ParsedMediaInfo.Unclassified(
            rawTitle = nameWithoutExt,
            cleanedTitle = cleanTitle(nameWithoutExt)
        )
    }

    /**
     * Cleans up title strings by stripping release prefixes, domain suffixes, noise tags,
     * resolutions, brackets, and normalizing punctuation to spaces.
     */
    fun cleanTitle(title: String): String {
        var cleaned = EXTENSION_REGEX.replace(title, "")
        cleaned = LEADING_GROUP_PREFIX_REGEX.replace(cleaned, "")
        cleaned = DOMAIN_SUFFIX_REGEX.replace(cleaned, " ")
        cleaned = BRACKETED_NOISE_REGEX.replace(cleaned, " ")
        cleaned = NOISE_TAGS_REGEX.replace(cleaned, " ")
        cleaned = YEAR_REGEX.replace(cleaned, " ")
        cleaned = SEPARATORS_REGEX.replace(cleaned, " ")
        cleaned = MULTI_SPACE_REGEX.replace(cleaned, " ").trim()
        return cleaned
    }
}
