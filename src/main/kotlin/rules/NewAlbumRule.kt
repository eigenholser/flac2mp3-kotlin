package com.eigenholser.flac2mp3.rules

import com.eigenholser.flac2mp3.deleteMp3CoverArt
import com.eigenholser.flac2mp3.states.AlbumState.state
import com.eigenholser.flac2mp3.states.AlbumStates
import com.eigenholser.flac2mp3.states.ExistingAlbumEvent
import com.eigenholser.flac2mp3.toInfo
import org.jeasy.rules.api.Facts
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.logging.Logger
import kotlin.io.path.exists

class NewAlbumRule() : TrackRule() {
    override val rulePriority = 1

    override fun getName() = AlbumRule.NEW_ALBUM.name

    override fun getDescription() = "Determines whether current track represents a transition to a new album."

    override fun evaluate(facts: Facts) =
        facts.albumState() == AlbumStates.NEW_ALBUM

    override fun execute(facts: Facts) {
        facts.albumStateMachine().fire(ExistingAlbumEvent())
        facts.trackData()
            .apply {
                state.previousAlbum
                    .takeIf { it.isNotBlank() }
                    ?.also { deleteMp3CoverArt(it) }

                state.previousAlbum = state.currentAlbum
                state.currentAlbum = currentAlbum

                Paths.get(mp3Album)
                    .takeIf { !it.exists() }
                    ?.also { it.createDirectories() }
            }
    }

    companion object {
        private val logger = Logger.getLogger("NewAlbumRule")

        private fun Path.createDirectories(): Path =
            runCatching { Files.createDirectories(this) }
                .onFailure { logger.info("Unable to create directories: $this".toInfo()) }
                .getOrThrow()
    }
}
