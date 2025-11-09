package com.eigenholser.flac2mp3

import com.eigenholser.flac2mp3.Tag.artworkExists
import com.eigenholser.flac2mp3.Tag.readAudioFile
import com.eigenholser.flac2mp3.rules.*
import com.eigenholser.flac2mp3.states.AlbumState.albumStateMachine
import com.eigenholser.flac2mp3.states.AlbumState.state
import com.eigenholser.flac2mp3.states.NewAlbumEvent
import org.jeasy.rules.api.Fact
import org.jeasy.rules.api.Facts
import org.jeasy.rules.api.Rules
import org.jeasy.rules.api.RulesEngineParameters
import org.jeasy.rules.core.DefaultRulesEngine
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.util.logging.LogManager
import java.util.logging.Logger
import kotlin.io.path.exists

data class TrackData(
    val flacFile: String,
    val flacAlbum: String,
    val currentAlbum: String,
    val mp3Album: String,
    val mp3File: String,
    val fsize: Long,
    val mtime: Long
)

fun main(args: Array<String>) {
    LogManager.getLogManager()
        .apply {
            readConfiguration(FileInputStream("src/main/resources/logging.properties"))
        }
    val logger = Logger.getLogger("com.eigenholser.flac2mp3.MainKt")
    logger.info("Scanning FLAC sources".toInfo())
    albumStateMachine.fire(NewAlbumEvent())

    File(Config.flacRoot)
        .walk()
        .filter { it.extension == "flac" }
        .map { it.toTrackData() }
        .filter { it.isStale() }
        .forEach { track ->
            logger.info("Processing track: $track".toInfo())
            track.fireAlbumArtRules()
        }

    deleteMp3CoverArt(state.previousAlbum)
}

fun TrackData.fireAlbumArtRules() {
    val parameters =
        RulesEngineParameters().skipOnFirstAppliedRule(false)
    val albumArtFacts =
        Facts()
            .also {
                it.add(Fact(AlbumArtFacts.TRACK_DATA.name, this))
                it.add(Fact(AlbumArtFacts.ALBUM_STATE.name, albumStateMachine))
            }

    DefaultRulesEngine(parameters)
        .fire(
            Rules(NewAlbumRule(), ExistingAlbumRule(), CreateMp3Rule(), UpdateAlbumArtRule(), SyncTagsRule()),
            albumArtFacts
        )
}

fun File.toTrackData(): TrackData {
    val mp3Extension = "mp3"
    val extensionSeparator = "."
    val dirSeparator = "/"
    val currentAlbum =
        absolutePath
            .removePrefix(Config.flacRoot + dirSeparator)
            .removeSuffix(dirSeparator + name)
    val mp3Album = Config.mp3Root + dirSeparator + currentAlbum

    return TrackData(
        flacFile = absolutePath,
        flacAlbum = absolutePath.removeSuffix(dirSeparator + name),
        currentAlbum = currentAlbum,
        mp3Album = mp3Album,
        mp3File = mp3Album + dirSeparator + nameWithoutExtension + extensionSeparator + mp3Extension,
        fsize = Files.getAttribute(toPath(), "size") as Long,
        mtime = toPath().mtime()
    )
}

fun TrackData.isCurrent() =
    if (mp3FileExists()) {
        Paths.get(flacFile).mtime() < Paths.get(mp3File).mtime()
    } else {
        false
    }

fun TrackData.mp3FileExists() = File(mp3File).exists()

fun TrackData.albumArtPNGExists() = Paths.get(flacAlbum).resolve(Config.albumArtFile).exists()

fun TrackData.isAlbumArtStale() =
    if (mp3FileExists() && albumArtPNGExists()) {
        val albumArtMtime = Paths.get(flacAlbum).resolve(Config.albumArtFile).mtime()
        val mp3Mtime = Paths.get(mp3File).mtime()

        readAudioFile(mp3File)
            ?.run { (artworkExists() && albumArtMtime > mp3Mtime) || !artworkExists() }
            ?: false
    } else {
        false
    }

fun TrackData.isStale() = !isCurrent() || isAlbumArtStale()

fun Path.mtime() =
    (Files.getAttribute(this, "lastModifiedTime") as FileTime).toMillis()

fun deleteMp3CoverArt(mp3Album: String) =
    listOf(
        File("${Config.mp3Root}/${mp3Album}/${Config.coverArtFile}").delete(),
        File("${Config.mp3Root}/${mp3Album}/${Config.thumbArtFile}").delete(),
    )
        .any { !it }

fun String.toInfo() = "\u001B[32m" + this + "\u001B[0m"
fun String.toDebug() = "\u001B[34m" + this + "\u001B[0m"
fun String.toError() = "\u001B[31m" + this + "\u001B[0m"
fun String.toWarn() = "\u001B[33m" + this + "\u001B[0m"

