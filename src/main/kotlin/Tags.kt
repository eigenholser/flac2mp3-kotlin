package com.eigenholser.flac2mp3

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
    val artist: String,
    val album: String,
    val title: String,
    val year: String,
    val genre: String,
    val track: String,
    val cddb: String
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
            .onFailure { logger.info("Unable to read audio file: $file".toInfo()) }
            .getOrNull()

    fun writeMp3Tags(mp3File: String, mp3AlbumPath: String, flacAudioFile: AudioFile) {
        assert(flacAudioFile.tag is FlacTag)
        readAudioFile(mp3File)
            ?.also { it.tag = ID3v24Tag() }
            ?.apply {
                val flacTags = flacAudioFile.toAudioTags()
                tag.addAlbumArtField(mp3AlbumPath)

                listOf(
                    FieldKey.ARTIST to flacTags.artist,
                    FieldKey.ALBUM to flacTags.album,
                    FieldKey.TITLE to flacTags.title,
                    FieldKey.YEAR to flacTags.year,
                    FieldKey.GENRE to flacTags.genre,
                    FieldKey.TRACK to flacTags.track,
                    FieldKey.CATALOG_NO to flacTags.cddb,
                )
                    .forEach { (k, v) -> tag.setField(k, v) }
            }
            ?.apply {
                runCatching { commit() }
                    .onSuccess { logger.info("Committed ID3v24 tags: $mp3File".toInfo()) }
                    .onFailure { e -> logger.warning("Unable to commit ID3v24 tags. Caused by: {$e.message}".toWarn()) }
            }
            ?: logger.warning("Something went wrong: AudioFile is null.".toWarn())
    }

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

    fun AudioFile.albumArtTagExists() = tag?.firstArtwork != null

    private fun Tag.addAlbumArtField(mp3AlbumPath: String) {
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

    fun Tag.ifArtworkExists(action: () -> Tag) =
        if (firstArtwork != null) {
            action()
        } else {
            this
        }

    fun updateAlbumArtField(mp3File: String, mp3Album: String) {
        readAudioFile(mp3File)
            ?.apply {
                tag
                    ?.ifArtworkExists { tag.apply { deleteAlbumArtField(mp3File) } }
                    ?.apply { tag.addAlbumArtField(mp3Album) }
                    ?.apply { commit() }
            }
    }
}
