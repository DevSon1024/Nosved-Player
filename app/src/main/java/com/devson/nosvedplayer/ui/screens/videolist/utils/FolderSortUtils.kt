package com.devson.nosvedplayer.ui.screens.videolist.utils

import com.devson.nosvedplayer.model.SortDirection
import com.devson.nosvedplayer.model.SortField
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.VideoFolder

fun List<VideoFolder>.applyFolderSort(
    folderMap: Map<VideoFolder, List<Video>>,
    field: SortField,
    direction: SortDirection
): List<VideoFolder> {
    val sorted = when (field) {
        SortField.TITLE -> sortedBy { it.name.lowercase() }
        SortField.DATE -> sortedBy { folder -> folderMap[folder]?.maxOfOrNull { it.dateAdded } ?: 0L }
        SortField.PLAYED_TIME -> sortedBy { folder -> folderMap[folder]?.maxOfOrNull { it.playedTime ?: 0L } ?: 0L }
        SortField.STATUS -> sortedBy { it.name.lowercase() }
        SortField.LENGTH -> sortedBy { folder -> folderMap[folder]?.sumOf { it.duration } ?: 0L }
        SortField.SIZE -> sortedBy { folder -> folderMap[folder]?.sumOf { it.size } ?: 0L }
        SortField.RESOLUTION -> sortedBy { it.name.lowercase() }
        SortField.PATH -> sortedBy { it.id.lowercase() }
        SortField.FRAME_RATE -> sortedBy { it.name.lowercase() }
        SortField.TYPE -> sortedBy { it.name.lowercase() }
    }
    return if (direction == SortDirection.DESCENDING) sorted.reversed() else sorted
}
