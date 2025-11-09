package com.eigenholser.flac2mp3.rules

import com.eigenholser.flac2mp3.LameFlac2Mp3.flac2mp3
import com.eigenholser.flac2mp3.Tag.readAudioFile
import com.eigenholser.flac2mp3.Tag.writeMp3Tags
import com.eigenholser.flac2mp3.TrackData
import com.eigenholser.flac2mp3.albumArtPNGExists
import com.eigenholser.flac2mp3.mp3FileExists
import org.jeasy.rules.api.Facts

class CreateMp3Rule : TrackRule() {
    override val rulePriority = 3

    override fun getName() = AlbumArtRules.NEW_MP3_ART_EXISTS.name

    override fun getDescription() = "New MP3 file and album art PNG exists in FLAC album."

    override fun evaluate(facts: Facts) =
        facts.get<TrackData>(AlbumArtFacts.TRACK_DATA.name)
            .run { !mp3FileExists() && albumArtPNGExists() }

    override fun execute(facts: Facts) {
        facts.trackData()
            .apply {
                flac2mp3(flacSrc = flacFile, mp3Dest = mp3File)

                writeMp3Tags(
                    mp3File = mp3File,
                    mp3AlbumPath = mp3Album,
                    flacAudioFile =
                        readAudioFile(flacFile)
                            ?: throw IllegalStateException("FLAC AudioFile is null. This should never ever occur.")
                )
            }
    }
}
