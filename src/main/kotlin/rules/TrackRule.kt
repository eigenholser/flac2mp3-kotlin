package com.eigenholser.flac2mp3.rules

import com.eigenholser.flac2mp3.TrackData
import com.eigenholser.flac2mp3.states.AlbumStates
import org.jeasy.rules.api.Facts
import org.jeasy.rules.api.Rule
import org.jeasy.states.api.FiniteStateMachine

abstract class TrackRule : Rule {
    abstract val rulePriority: Int

    fun Facts.albumStateMachine(): FiniteStateMachine = get(AlbumArtFacts.ALBUM_STATE.name)

    fun Facts.albumState() =
        AlbumStates.valueOf(albumStateMachine().currentState.name)

    fun Facts.trackData(): TrackData = get(AlbumArtFacts.TRACK_DATA.name)

    override fun getPriority() = rulePriority

    override fun compareTo(other: Rule) =
        when {
            priority > other.priority -> 1
            priority < other.priority -> -1
            else -> 0
        }
}