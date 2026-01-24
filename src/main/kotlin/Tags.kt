package com.eigenholser.flac2mp3

import com.eigenholser.flac2mp3.ImageScaler.scale
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldDataInvalidException
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.StandardArtwork
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.util.logging.Logger

data class AudioTags(
    val artist: String = "",
    val album: String = "",
    val title: String = "",
    val year: String = "",
    val genre: String = "",
    val track: String = "",
    val cddb: String = ""
)

object Tag {
    private val logger = Logger.getLogger("Tag")
    private val md = MessageDigest.getInstance("MD5")

    fun md5sum(input: String) =
        BigInteger(1, md.digest(input.toByteArray()))
            .toString(16)
            .padStart(32, '0')

    fun readAudioFile(file: String): AudioFile? =
        runCatching { AudioFileIO.read(File(file)) }
            .onFailure { logger.info("Unable to read audio file: $file. Caused by: ${it.message}".toInfo()) }
            .getOrNull()

    fun writeMp3Tags(
        mp3File: String,
        mp3AlbumPath: String,
        flacAudioFile: AudioFile,
        updateAlbumArt: Boolean = true
    ) {
        assert(flacAudioFile.tag is FlacTag)
        readAudioFile(mp3File)
            ?.also { it.apply { tag = tag ?: ID3v24Tag() } }
            ?.apply {
                val flacTags = flacAudioFile.toAudioTags()

                mapOf(
                    DestType.COVER to File("${mp3AlbumPath}/${Config.coverArtFile}"),
                    DestType.THUMB to File("${mp3AlbumPath}/${Config.thumbArtFile}")
                )
                    .map { (k, v) ->
                        v.exists()
                            .takeIf { !it }
                            ?.takeIf { updateAlbumArt }
                            ?.let { k to scale(flacAudioFile.file.parent, mp3AlbumPath, k) }
                            ?: (k to Result.success(null))
                    }
                    .forEach { (k, v) ->
                        if (v.isSuccess) {
                            logger.info("Scaled [$k] art for album: ${flacAudioFile.file.absolutePath}".toInfo())
                        } else {
                            logger.info("Error scaling [$k] art for album: ${flacAudioFile.file.absolutePath}. Caused by: ${v.exceptionOrNull()?.message}".toDebug())
                        }
                    }

                tag
                    .takeIf { updateAlbumArt }
                    ?.ifArtworkExists { it.apply { deleteAlbumArtField(mp3File) } }
                    ?.addAlbumArtField(mp3AlbumPath)

                flacTags
                    .toMap()
                    .forEach { (k, v) -> tag.setField(k, v) }
            }
            ?.apply {
                runCatching { commit() }
                    .onSuccess { logger.info("Committed ID3v24 tags: $mp3File".toInfo()) }
                    .onFailure { e -> logger.warning("Unable to commit ID3v24 tags. Caused by: {$e.message}".toWarn()) }
            }
            ?: logger.warning("Something bad happened: AudioFile is null.".toWarn())
    }

    fun AudioTags.toMap() =
        mapOf(
            FieldKey.ARTIST to artist,
            FieldKey.ALBUM to album,
            FieldKey.TITLE to title,
            FieldKey.YEAR to year,
            FieldKey.GENRE to genre,
            FieldKey.TRACK to track,
            FieldKey.CATALOG_NO to cddb,
        )

    fun AudioFile.toAudioTags() =
        tag.run {
            AudioTags(
                artist = getFirst(FieldKey.ARTIST),
                album = getFirst(FieldKey.ALBUM),
                title = getFirst(FieldKey.TITLE),
                year = getFirst(FieldKey.YEAR).ifEmpty { "0000" },
                genre = getFirst(FieldKey.GENRE).ifEmpty { "None" },
                track = getFirst(FieldKey.TRACK),
                cddb =
                    getFirst("CDDB")
                        .ifEmpty { getFirst("MD5 SIGNATURE") }
                        .ifEmpty { md5sum(getFirst(FieldKey.TITLE)) }
            )
        }

    fun AudioFile.artworkExists() = tag?.firstArtwork != null

    fun Tag.addAlbumArtField(mp3AlbumPath: String) {
        assert(this is ID3v24Tag)
        runCatching {
            StandardArtwork
                .createArtworkFromFile(File("$mp3AlbumPath/${Config.coverArtFile}"))
                .also { it.description = "Cover Art" }
                .also { addField(it) }
        }
            .onSuccess { logger.info("Added album art to track: $mp3AlbumPath/${Config.coverArtFile}".toInfo()) }
            .onFailure {
                when (it) {
                    is FieldDataInvalidException ->
                        logger.warning("Unable to tag file with album art: $mp3AlbumPath/${Config.coverArtFile}".toWarn())

                    is IOException ->
                        logger.warning("Unable to find album art: $mp3AlbumPath/${Config.coverArtFile}".toWarn())
                }
            }
    }

    private fun Tag.deleteAlbumArtField(mp3File: String) =
        runCatching { deleteArtworkField() }
            .onSuccess { logger.info("Deleted existing ID3v24 artwork on track: $mp3File".toInfo()) }
            .onFailure { logger.severe("ID3v24 artwork tag not present on track: $mp3File".toError()) }
            .map { true }
            .getOrElse { false }

    fun Tag.ifArtworkExists(action: (tag: Tag) -> Tag) =
        if (firstArtwork != null) {
            action(this)
        } else {
            this
        }

//    fun updateAlbumArtField(mp3File: String, mp3Album: String) {
//        readAudioFile(mp3File)
//            ?.apply {
//                tag
//                    ?.ifArtworkExists { tag.apply { deleteAlbumArtField(mp3File) } }
//                    ?.apply { tag.addAlbumArtField(mp3Album) }
//                    ?.apply { commit() }
//            }
//    }
}
