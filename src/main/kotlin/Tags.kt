package com.eigenholser.flac2mp3

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

data class FlacTags(
    val artist: String,
    val album: String,
    val title: String,
    val year: String,
    val genre: String,
    val track: String,
    val cddb: String
)

private val md = MessageDigest.getInstance("MD5")

fun md5sum(input:String) =
    BigInteger(1, md.digest(input.toByteArray()))
        .toString(16)
        .padStart(32, '0')

object Tag {
    val logger: Logger = Logger.getLogger("Tags")

    fun readFlacTags(flacFile: String) =
        AudioFileIO.read(File(flacFile))
            .run {
                FlacTags(
                    artist = tag.getFirst(FieldKey.ARTIST),
                    album = tag.getFirst(FieldKey.ALBUM),
                    title = tag.getFirst(FieldKey.TITLE),
                    year = tag.getFirst(FieldKey.YEAR).ifEmpty { "0000" },
                    genre = tag.getFirst(FieldKey.GENRE).ifEmpty { "None" },
                    track = tag.getFirst(FieldKey.TRACK),
                    cddb =
                        tag.getFirst("CDDB")
                            .ifEmpty { tag.getFirst("MD5 SIGNATURE") }
                            .ifEmpty { md5sum(tag.getFirst(FieldKey.TITLE)) }
                )
            }

    fun writeMp3Tags(mp3File: String, mp3AlbumPath: String, flacTags: FlacTags) {
        AudioFileIO.read(File(mp3File))
            .also { it.tag = ID3v24Tag() }
            .let {
                it.apply {
                    addAlbumArtField(mp3AlbumPath, tag)
                    tag.setField(FieldKey.ARTIST, flacTags.artist)
                    tag.setField(FieldKey.ALBUM, flacTags.album)
                    tag.setField(FieldKey.TITLE, flacTags.title)
                    tag.setField(FieldKey.YEAR, flacTags.year)
                    tag.setField(FieldKey.GENRE, flacTags.genre)
                    tag.setField(FieldKey.TRACK, flacTags.track)
                    logger.info("Fields finally in mp3 $mp3AlbumPath: ${tag.fieldCount}")
                    // TODO: Something broken about this. How does it work?
//                    tag.createField(FieldKey.valueOf("CDDB"), flacTags.cddb)
                }
            }
            .also {
                runCatching { it.commit() }
                    .onSuccess { logger.info("Committed tags: $mp3File") }
                    .onFailure { logger.warning("Unable to commit. Caused by: {$it.message}") }
            }
    }

    fun albumArtTagExists(mp3File: String): Boolean {
        val f = AudioFileIO.read(File(mp3File))
        val artwork = f.tag?.firstArtwork
        return artwork != null
    }

    private fun addAlbumArtField(mp3AlbumPath: String, tag: Tag) {
        logger.info("Fields initially in mp3 $mp3AlbumPath: ${tag.fieldCount}")
        runCatching {
            val albumArt = StandardArtwork.createArtworkFromFile(File("$mp3AlbumPath/${Config.coverArtFile}"))
            tag.addField(albumArt)
            logger.info("Fields finally in mp3 $mp3AlbumPath: ${tag.fieldCount}")
        }
            .onFailure {
                when (it) {
                    is FieldDataInvalidException ->
                        logger.warning("Could not tag file with album art: $mp3AlbumPath/${Config.coverArtFile}")

                    is IOException ->
                        logger.warning("Could not find album art for tagging: $mp3AlbumPath/${Config.coverArtFile}")
                }
            }
    }

    private fun deleteAlbumArtField(tag: Tag) {
        logger.info("Artwork list: ${tag.artworkList}")
        runCatching { tag.deleteArtworkField() }
            .onFailure { logger.info("Album art tag not present.") }
    }

    fun updateAlbumArtField(mp3File: String, mp3Album: String) {
        AudioFileIO.read(File(mp3File))
            .let { f ->
                if (albumArtTagExists(File(mp3File))) {
                    deleteAlbumArtField(f.tag)
                }
                addAlbumArtField(mp3Album, f.tag)
                f.commit()
            }
    }
}