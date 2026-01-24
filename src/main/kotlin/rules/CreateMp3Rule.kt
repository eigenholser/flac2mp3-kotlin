package com.eigenholser.flac2mp3.rules

import com.eigenholser.flac2mp3.Config
import com.eigenholser.flac2mp3.LameFlac2Mp3.flac2mp3
import com.eigenholser.flac2mp3.Tag.readAudioFile
import com.eigenholser.flac2mp3.Tag.writeMp3Tags
import com.eigenholser.flac2mp3.TrackData
import com.eigenholser.flac2mp3.albumArtPNGExists
import com.eigenholser.flac2mp3.mp3FileExists
import com.eigenholser.flac2mp3.toError
import com.eigenholser.flac2mp3.toInfo
import com.eigenholser.flac2mp3.toWarn
import org.jeasy.rules.api.Facts
import java.util.logging.Logger

class CreateMp3Rule : TrackRule() {
    override val rulePriority = 3

    override fun getName() = AlbumArtRules.NEW_MP3_ART_EXISTS.name

    override fun getDescription() = "New MP3 file and album art PNG exists in FLAC album."

    override fun evaluate(facts: Facts) =
        facts.get<TrackData>(AlbumArtFacts.TRACK_DATA.name)
            ?.run {
                when (Config.albumArtRequired) {
                    true -> {
                        runCatching { albumArtPNGExists() }
                            .onFailure { logger.severe("Error reading FLAC path: $flacFile".toError()) }
                            .onSuccess {
                                if (it) {
                                    logger.info("Found album art $flacAlbum/${Config.albumArtFile}".toInfo())
                                } else {
                                    logger.warning("Album art not found. MP3 encoding disabled! $flacAlbum/${Config.albumArtFile}".toWarn())
                                }
                            }
                            .map { !mp3FileExists() && it /* it == albumArtPNGExists() */ }
                            .getOrDefault(false)
                    }

                    false -> !mp3FileExists()
                }
            }
            ?: false

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

    companion object {
        private val logger = Logger.getLogger("CreateMp3Rule")
    }
}
