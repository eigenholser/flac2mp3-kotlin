package com.eigenholser.flac2mp3.rules

import com.eigenholser.flac2mp3.states.AlbumState.state
import com.eigenholser.flac2mp3.states.AlbumStates
import com.eigenholser.flac2mp3.states.NewAlbumEvent
import org.jeasy.rules.api.Facts
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger

class ExistingAlbumRule() : TrackRule() {
    override val rulePriority = 2

    override fun getName() = AlbumRule.NEW_ALBUM.name

    override fun getDescription() = "Determines whether current track represents a transition to a new album."

    override fun evaluate(facts: Facts) =
        facts.albumState() == AlbumStates.EXISTING_ALBUM

    override fun execute(facts: Facts) {
        facts.trackData()
            .apply {
                state.previousAlbum = state.currentAlbum
                state.currentAlbum = currentAlbum
            }

        facts.albumStateMachine()
            .takeIf { state.run { currentAlbum != previousAlbum } }
            ?.also { it.fire(NewAlbumEvent()) }
    }

    companion object {
        private val logger = Logger.getLogger("NewAlbumRule")

        private fun Path.createDirectories(): Path =
            runCatching { Files.createDirectories(this) }
                .onFailure { logger.info("Unable to create directories: $this") }
                .getOrThrow()
    }
}