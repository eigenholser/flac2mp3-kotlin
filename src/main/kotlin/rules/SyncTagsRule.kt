package com.eigenholser.flac2mp3.rules

import com.eigenholser.flac2mp3.AudioTags
import com.eigenholser.flac2mp3.Tag.readAudioFile
import com.eigenholser.flac2mp3.Tag.toAudioTags
import com.eigenholser.flac2mp3.Tag.toMap
import com.eigenholser.flac2mp3.Tag.writeMp3Tags
import com.eigenholser.flac2mp3.toInfo
import org.jeasy.rules.api.Facts
import java.io.File
import java.util.logging.Logger

class SyncTagsRule : TrackRule() {
    override val rulePriority = 5

    override fun getName() = "SYNC TAGS FLAC TO MP3"

    override fun getDescription() = "Synchronize tags from FLAC to MP3 tracks. One way only."

    override fun evaluate(facts: Facts) =
        facts.trackData()
            .run {
                val flacTags =
                    readAudioFile(flacFile)
                        ?.toAudioTags()
                        ?: AudioTags()

                val mp3Tags =
                    readAudioFile(mp3File)
                        ?.toAudioTags()
                        ?: AudioTags()

                flacTags.toMap() != mp3Tags.toMap()
                    .also { logger.info("MP3 tags out of sync for track $flacFile".toInfo()) }
            }

    override fun execute(facts: Facts) {
        facts.trackData()
            .apply {
                val flacFilename = File(flacFile).name
                val mp3Filename = File(mp3File).name

                logger.info("Synchronizing tags: $flacFilename --> $mp3Filename".toInfo())
                writeMp3Tags(
                    mp3File = mp3File,
                    mp3AlbumPath = mp3Album,
                    flacAudioFile =
                        readAudioFile(flacFile)
                            ?: throw IllegalStateException("FLAC AudioFile is null. This should never ever occur."),
                    updateAlbumArt = false
                )
            }
    }

    companion object {
        private val logger = Logger.getLogger("SyncTagsRule")
    }
}