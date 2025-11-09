package com.eigenholser.flac2mp3.rules

import com.eigenholser.flac2mp3.Tag.readAudioFile
import com.eigenholser.flac2mp3.Tag.writeMp3Tags
import com.eigenholser.flac2mp3.isAlbumArtStale
import org.jeasy.rules.api.Facts

class UpdateAlbumArtRule : TrackRule() {
    override val rulePriority = 4

    override fun getName() = AlbumArtRules.MP3_TAGGED_ART_UPDATED.name

    override fun getDescription() =
        "Existing MP3 file has album art tag and album art PNG updated in FLAC album."

    override fun evaluate(facts: Facts) =
        facts.trackData()
            .run { isAlbumArtStale() }

    override fun execute(facts: Facts) {
        facts.trackData()
            .apply {
                writeMp3Tags(
                    mp3File = mp3File,
                    mp3AlbumPath = mp3Album,
                    flacAudioFile =
                        readAudioFile(flacFile)
                            ?: throw IllegalStateException("FLAC AudioFile is null. This should never ever occur."),
                )
            }
    }
}