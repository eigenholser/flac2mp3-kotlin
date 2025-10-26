package com.eigenholser.flac2mp3.rules

import com.eigenholser.flac2mp3.TrackData
import com.eigenholser.flac2mp3.isAlbumArtUpdated
import org.jeasy.rules.api.Facts

class ArtUpdateIDv3: AlbumArtRule {
    override val rulePriority = 2

    override fun getName() = AlbumArtRules.MP3_TAGGED_ART_UPDATED.name

    override fun getDescription() =
        "Existing MP3 file has album art tag and album art PNG updated in FLAC album."

    override fun evaluate(facts: Facts) =
        facts.get<TrackData>(AlbumArtFacts.TRACK_DATA.name)
            .run { isAlbumArtUpdated() }
}