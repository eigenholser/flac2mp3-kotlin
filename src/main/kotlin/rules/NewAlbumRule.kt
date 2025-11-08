package com.eigenholser.flac2mp3.rules

import com.eigenholser.flac2mp3.DestType
import com.eigenholser.flac2mp3.ImageScaler
import com.eigenholser.flac2mp3.TrackData
import com.eigenholser.flac2mp3.deleteMp3CoverArt
import com.eigenholser.flac2mp3.states.AlbumState
import com.eigenholser.flac2mp3.states.AlbumState.state
import com.eigenholser.flac2mp3.states.AlbumStates
import com.eigenholser.flac2mp3.states.ExistingAlbumEvent
import com.eigenholser.flac2mp3.states.NewAlbumEvent
import com.eigenholser.flac2mp3.toError
import com.eigenholser.flac2mp3.toInfo
import org.jeasy.rules.api.Facts
import org.jeasy.rules.api.Rule
import org.jeasy.states.api.FiniteStateMachine
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.logging.Logger

class NewAlbumRule(private val albumStateMachine: FiniteStateMachine) : Rule {
    override fun getName() = AlbumRule.NEW_ALBUM.name

    override fun getPriority() = 1

    override fun getDescription() = "Determines whether current track represents a transition to a new album."

    override fun execute(facts: Facts) {
        facts.get<TrackData>(AlbumArtFacts.TRACK_DATA.name)
            .apply {
                listOf(
                    ImageScaler.scale(flacAlbum, mp3Album, DestType.COVER),
                    ImageScaler.scale(flacAlbum, mp3Album, DestType.THUMB)
                )
                    .any { !it }
                    .also { isError ->
                        if (isError) {
                            logger.warning("Error performing art scaling on album: $currentAlbum".toError())
                        } else {
                            logger.info("Completed art scaling on album: $currentAlbum".toInfo())
                        }
                    }

                state.prevMp3AlbumPath
                    .takeIf { it.isNotBlank() }
                    ?.also { deleteMp3CoverArt(state.prevMp3AlbumPath) }

                state.nextAlbum = currentAlbum
                state.prevMp3AlbumPath = mp3Album
                Paths.get(mp3Album).createDirectories()

                AlbumState.albumStateMachine.fire(ExistingAlbumEvent())
            }

        albumStateMachine.fire(NewAlbumEvent())
    }

    override fun compareTo(other: Rule) = 0

    override fun evaluate(facts: Facts): Boolean {
        val albumState = facts.get<FiniteStateMachine>(AlbumFact.ALBUM_STATE.name)
        val currentAlbum = facts.get<String>(AlbumFact.CURRENT_ALBUM.name)
        val nextAlbum = facts.get<String>(AlbumFact.NEXT_ALBUM.name)

        return AlbumStates.valueOf(albumState.currentState.name) == AlbumStates.EXISTING_ALBUM && currentAlbum != nextAlbum
    }

    companion object {
        private val logger = Logger.getLogger("NewAlbumRule")

        private fun Path.createDirectories(): Path =
            runCatching { Files.createDirectories(this) }
                .onFailure { logger.info("Unable to create directories: $this") }
                .getOrThrow()
    }
}
