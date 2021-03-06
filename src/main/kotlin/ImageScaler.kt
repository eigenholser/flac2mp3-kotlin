package com.eigenholser.flac2mp3

import ij.IJ
import ij.process.ImageProcessor
import java.util.logging.Logger
import kotlin.math.nextUp

enum class DestType {
    COVER, THUMB
}

object ImageScaler {
    val logger = Logger.getLogger("ImageScaler")

    val coverFilename = Config.coverArtFile
//    val thumbFilename = Config.thumbArtFile
    val destFormat = "jpg"

    private fun computeScaleFactor(xAxis: Int, srcSize: Int): Double {
        return (xAxis/srcSize.toDouble())
    }

    private fun makeThumb(ip: ImageProcessor): ImageProcessor {
        val scaleFactor = computeScaleFactor(Config.thumbnailResolution, ip.width)
        return ip.resize(Config.thumbnailResolution, (scaleFactor*ip.height).nextUp().toInt())
    }

    private fun makeCover(ip: ImageProcessor): ImageProcessor {
        val scaleFactor = computeScaleFactor(Config.coverResolution, ip.width)
        return ip.resize(Config.coverResolution, ((scaleFactor*ip.height).nextUp().toInt()))
    }

    fun scaleImage(src: String, dest: String) {
        try {
            val imp = IJ.openImage("$src/${Config.albumArtFile}")
            val ip = imp.processor

            // Disable thumbnail for now. Maybe remove it entirely.
//            imp.processor = makeThumb(ip)
//            IJ.saveAs(imp, destFormat, "$dest/$thumbFilename")

            imp.processor = makeCover(ip)
            IJ.saveAs(imp, destFormat, "$dest/$coverFilename")
        } catch (e: NullPointerException) {
            logger.warning("Album art not found: $src/${Config.albumArtFile}")
        }
    }

    fun scaleImage(src: String, dest: String, destType: DestType) {
        try {
            val imp = IJ.openImage("$src/${Config.albumArtFile}")
            val ip = imp.processor

            if (destType == DestType.THUMB) {
                imp.processor = makeThumb(ip)
                IJ.saveAs(imp, destFormat, "$dest/$coverFilename")
            } else if (destType == DestType.COVER) {
                imp.processor = makeCover(ip)
                IJ.saveAs(imp, destFormat, "$dest/$coverFilename")
            }
        } catch (e: NullPointerException) {
            logger.warning("Album art not found: $src/${Config.albumArtFile}")
        }
    }
}