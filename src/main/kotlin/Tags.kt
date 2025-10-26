package com.eigenholser.flac2mp3

import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldDataInvalidException
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.StandardArtwork
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.util.logging.Logger
import kotlin.text.ifEmpty

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
    val logger: Logger = Logger.getLogger("Tags")
    private val md = MessageDigest.getInstance("MD5")

    fun md5sum(input: String) =
        BigInteger(1, md.digest(input.toByteArray()))
            .toString(16)
            .padStart(32, '0')

    fun readAudioFile(file: String): AudioFile =
        runCatching { AudioFileIO.read(File(file)) }
            .onFailure { logger.info("Unable to read audio file: $file") }
            .getOrThrow()

    fun writeMp3Tags(mp3File: String, mp3AlbumPath: String, flacAudioFile: AudioFile) {
        readAudioFile(mp3File)
            .also { it.tag = ID3v24Tag() }
            .let {
                val flacTags = flacAudioFile.toAudioTags()
                it.apply {
                    tag.addAlbumArtField(mp3AlbumPath)
                    tag.setField(FieldKey.ARTIST, flacTags.artist)
                    tag.setField(FieldKey.ALBUM, flacTags.album)
                    tag.setField(FieldKey.TITLE, flacTags.title)
                    tag.setField(FieldKey.YEAR, flacTags.year)
                    tag.setField(FieldKey.GENRE, flacTags.genre)
                    tag.setField(FieldKey.TRACK, flacTags.track)
                    tag.setField(FieldKey.CATALOG_NO, flacTags.cddb)
                    logger.info("Final fields in mp3 audio file: $mp3AlbumPath: ${tag.fieldCount}")
                }
            }
            .apply {
                runCatching { commit() }
                    .onSuccess { logger.info("Committed tags: $mp3File") }
                    .onFailure { e -> logger.warning("Unable to commit tags. Caused by: {$e.message}") }
            }
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
        logger.info("Initial fields in mp3 $mp3AlbumPath: ${fieldCount}")
        runCatching {
            StandardArtwork.createArtworkFromFile(File("$mp3AlbumPath/${Config.coverArtFile}"))
                .also { addField(it) }
        }
            .onSuccess { logger.info("Final fields in mp3 $mp3AlbumPath: ${fieldCount}") }
            .onFailure {
                when (it) {
                    is FieldDataInvalidException ->
                        logger.warning("Unable to tag file with album art: $mp3AlbumPath/${Config.coverArtFile}")

                    is IOException ->
                        logger.warning("Unable to find album art for tagging: $mp3AlbumPath/${Config.coverArtFile}")
                }
            }
    }

    private fun Tag.deleteAlbumArtField() {
        logger.info("Artwork list: $artworkList")
        runCatching { deleteArtworkField() }
            .onFailure { logger.info("Album art tag not present.") }
    }

    fun updateAlbumArtField(mp3File: String, mp3Album: String) {
        readAudioFile(mp3File)
            .apply {
                if (albumArtTagExists()) {
                    tag.deleteAlbumArtField()
                }
                tag.addAlbumArtField(mp3Album)
                commit()
            }
    }
}
