package com.eigenholser.flac2mp3.rules

import com.eigenholser.flac2mp3.DestType
import com.eigenholser.flac2mp3.ImageScaler
import com.eigenholser.flac2mp3.Tag
import com.eigenholser.flac2mp3.Tag.readAudioFile
import com.eigenholser.flac2mp3.TrackData
import com.eigenholser.flac2mp3.isAlbumArtUpdated
import com.eigenholser.flac2mp3.states.AlbumStates
import org.jeasy.rules.api.Facts

class ArtUpdateIDv3: AlbumArtRule() {
    override val rulePriority = 2

    override fun getName() = AlbumArtRules.MP3_TAGGED_ART_UPDATED.name

    override fun getDescription() =
        "Existing MP3 file has album art tag and album art PNG updated in FLAC album."

    override fun execute(facts: Facts) {
        if (getAlbumState(facts) == AlbumStates.NEW_ALBUM) {
            facts.get<TrackData>(AlbumArtFacts.TRACK_DATA.name)
                .apply {
                    ImageScaler.scaleImage(
                        flacAlbum,
                        mp3Album,
                        DestType.COVER
                    )

                    // TODO: Check for MP3 exists first?
                    Tag.updateAlbumArtField(
                        mp3File,
                        mp3Album
                    )

                    Tag.writeMp3Tags(
                        mp3File = mp3File,
                        mp3AlbumPath = mp3Album,
                        flacAudioFile = readAudioFile(flacFile)
                    )
                }
        }
    }

    override fun evaluate(facts: Facts) =
        facts.get<TrackData>(AlbumArtFacts.TRACK_DATA.name)
            .run { isAlbumArtUpdated() }
}