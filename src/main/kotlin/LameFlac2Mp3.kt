package com.eigenholser.flac2mp3

import java.util.logging.Logger

object LameFlac2Mp3 {
    private val logger = Logger.getLogger("LameFlac2Mp3")

    fun flac2mp3(flacSrc: String, mp3Dest: String): Int {
        logger.info("Converting track to MP3: $flacSrc --> $mp3Dest".toInfo())
        return ProcessBuilder(Config.lamePath, "-b", "${Config.bitRate}", "-q", "${Config.quality}", flacSrc, mp3Dest)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor()
    }
}