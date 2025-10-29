package com.eigenholser.flac2mp3.rules

import com.eigenholser.flac2mp3.*
import org.jeasy.rules.api.Facts
import kotlin.io.path.ExperimentalPathApi

class ArtNewMp3 : AlbumArtRule() {
    override val rulePriority = 1

    override fun getName() = AlbumArtRules.NEW_MP3_ART_EXISTS.name

    override fun getDescription() = "New MP3 file and album art PNG exists in FLAC album."

    @OptIn(ExperimentalPathApi::class)
    override fun execute(facts: Facts) {
        getTrackData(facts)
            .apply { LameFlac2Mp3.flac2mp3(flacSrc = flacFile, mp3Dest = mp3File) }
    }

    @OptIn(ExperimentalPathApi::class)
    override fun evaluate(facts: Facts) =
        facts.get<TrackData>(AlbumArtFacts.TRACK_DATA.name)
            .run { !mp3FileExists() && albumArtPNGExists() }

}