package com.eigenholser.flac2mp3.rules

import com.eigenholser.flac2mp3.DestType
import com.eigenholser.flac2mp3.ImageScaler
import com.eigenholser.flac2mp3.TrackData
import com.eigenholser.flac2mp3.createDirectories
import com.eigenholser.flac2mp3.deleteMp3CoverArt
import com.eigenholser.flac2mp3.states.AlbumState
import com.eigenholser.flac2mp3.states.AlbumState.state
import com.eigenholser.flac2mp3.states.AlbumStates
import com.eigenholser.flac2mp3.states.ExistingAlbumEvent
import com.eigenholser.flac2mp3.states.NewAlbumEvent
import org.jeasy.rules.api.Facts
import org.jeasy.rules.api.Rule
import org.jeasy.states.api.FiniteStateMachine
import java.nio.file.Paths

class NewAlbum(private val albumStateMachine: FiniteStateMachine): Rule {
    override fun getName() = AlbumRule.NEW_ALBUM.name

    override fun getDescription() = "Determines whether current track represents a transition to a new album."

    override fun execute(facts: Facts) {
        facts.get<TrackData>(AlbumArtFacts.TRACK_DATA.name)
            .apply {
                ImageScaler.scaleImage(
                    flacAlbum,
                    mp3Album,
                    DestType.COVER
                )

                deleteMp3CoverArt(state.prevMp3AlbumPath)

                state.nextAlbum = currentAlbum
                state.prevMp3AlbumPath = mp3Album
                Paths.get(mp3Album).createDirectories()

//                fireAlbumArtRules()
//                    .also { AlbumState.albumStateMachine.fire(ExistingAlbumEvent()) }
                AlbumState.albumStateMachine.fire(ExistingAlbumEvent())
            }

        albumStateMachine.fire(NewAlbumEvent())
    }

    override fun compareTo(other: Rule): Int = 0

    override fun evaluate(facts: Facts): Boolean {
        val albumState = facts.get<FiniteStateMachine>(AlbumFact.ALBUM_STATE.name)
        val currentAlbum = facts.get<String>(AlbumFact.CURRENT_ALBUM.name)
        val nextAlbum = facts.get<String>(AlbumFact.NEXT_ALBUM.name)

        return AlbumStates.valueOf(albumState.currentState.name) == AlbumStates.EXISTING_ALBUM && currentAlbum != nextAlbum
    }
}