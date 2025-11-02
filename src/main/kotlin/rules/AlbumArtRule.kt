package com.eigenholser.flac2mp3.rules

import com.eigenholser.flac2mp3.TrackData
import com.eigenholser.flac2mp3.states.AlbumStates
import org.jeasy.rules.api.Facts
import org.jeasy.rules.api.Rule
import org.jeasy.states.api.FiniteStateMachine

abstract class AlbumArtRule : Rule {
    abstract val rulePriority: Int

    fun getAlbumState(facts: Facts) =
        AlbumStates.valueOf(facts.get<FiniteStateMachine>(AlbumArtFacts.ALBUM_STATE.name).currentState.name)

    fun getTrackData(facts: Facts): TrackData = facts.get(AlbumArtFacts.TRACK_DATA.name)

    override fun getPriority() = rulePriority

    override fun compareTo(other: Rule) =
        when {
            priority > other.priority -> 1
            priority < other.priority -> -1
            else -> name.compareTo(other.name)
        }
}