package com.eigenholser.flac2mp3

import ij.IJ
import ij.ImagePlus
import ij.process.ImageProcessor
import java.util.logging.Logger
import kotlin.math.nextUp

enum class DestType {
    COVER, THUMB
}

data class AlbumArtScale(
    val imagePlus: ImagePlus,
    val filename: String,
)

object ImageScaler {
    val logger: Logger = Logger.getLogger("ImageScaler")
    private const val FORMAT = "jpg"

    private fun resize(ip: ImageProcessor, resolution: Int) =
        (resolution / ip.width.toDouble())
            .let { ip.resize(resolution, ((it * ip.height).nextUp().toInt())) }

    fun scale(src: String, dest: String, destType: DestType) =
        runCatching { IJ.openImage("$src/${Config.albumArtFile}") }
            .onSuccess { logger.info("Converted album art type $destType") }
            .onFailure { logger.warning("Album art not found: $src/${Config.albumArtFile}") }
            .mapCatching { imagePlus ->
                when (destType) {
                    DestType.THUMB ->
                        AlbumArtScale(
                            imagePlus = imagePlus.apply { processor = resize(imagePlus.processor, Config.thumbnailResolution) },
                            filename = dest + "/" + Config.thumbArtFile,
                        )

                    DestType.COVER ->
                        AlbumArtScale(
                            imagePlus = imagePlus.apply { processor = resize(imagePlus.processor, Config.thumbnailResolution) },
                            filename = dest + "/" + Config.coverArtFile,
                        )
                }
                    .apply { IJ.saveAs(imagePlus, FORMAT, filename) }
                    .apply { imagePlus.close() }
            }
            .onSuccess { logger.info("Scaled image: $src/${Config.albumArtFile} --> ${it.filename}, resolution: ${it.imagePlus.width}") }
            .onFailure { logger.severe("Failed to scale image: Caused by: ${it.message}") }
            .map { true }
            .getOrElse { false }
}