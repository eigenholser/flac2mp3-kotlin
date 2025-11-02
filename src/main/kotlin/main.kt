package com.eigenholser.flac2mp3

import com.eigenholser.flac2mp3.Tag.albumArtTagExists
import com.eigenholser.flac2mp3.Tag.readAudioFile
import com.eigenholser.flac2mp3.rules.*
import com.eigenholser.flac2mp3.states.AlbumState.albumStateMachine
import com.eigenholser.flac2mp3.states.AlbumState.state
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

val logger: Logger = Logger.getLogger("MainKt")

fun main(args: Array<String>) {
    LogManager.getLogManager()
        .apply { readConfiguration(FileInputStream("src/main/resources/logging.xml")) }
    logger.info("Scanning FLAC sources")

    val rulesEngine = DefaultRulesEngine()

    File(Config.flacRoot)
        .walk()
        .filter { it.extension == "flac" }
        .map { it.toTrackData() }
        .filter { it.isStale() }
        .forEach { track ->
            logger.info("Processing track: $track")

            Facts()
                .apply {
                    add(Fact(AlbumFact.ALBUM_STATE.name, albumStateMachine))
                    add(Fact(AlbumFact.CURRENT_ALBUM.name, track.currentAlbum))
                    add(Fact(AlbumFact.NEXT_ALBUM.name, state.nextAlbum))
                    add(Fact(AlbumArtFacts.TRACK_DATA.name, track))
                }
                .also { rulesEngine.fire(Rules(NewAlbumRule(albumStateMachine)), it) }

            track.fireAlbumArtRules()
        }

    // Delete the previous album art
    deleteMp3CoverArt(state.prevMp3AlbumPath)
}

fun TrackData.fireAlbumArtRules() {
    val parameters =
        RulesEngineParameters().skipOnFirstAppliedRule(true)
    val albumArtFacts =
        Facts()
            .also {
                it.add(Fact(AlbumArtFacts.TRACK_DATA.name, this))
                it.add(Fact(AlbumArtFacts.ALBUM_STATE.name, albumStateMachine))
            }

    DefaultRulesEngine(parameters)
        .fire(Rules(CreateMp3Rule(), UpdateAlbumArtRule()), albumArtFacts)
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

fun TrackData.isAlbumArtUpdated() =
    if (mp3FileExists() && albumArtPNGExists()) {
        val albumArtMtime = Paths.get(flacAlbum).resolve(Config.albumArtFile).mtime()
        val mp3Mtime = Paths.get(mp3File).mtime()

        readAudioFile(mp3File)
            ?.run { (albumArtTagExists() && albumArtMtime > mp3Mtime) || !albumArtTagExists() }
            ?: false
    } else {
        false
    }

fun TrackData.isStale() = !isCurrent() || isAlbumArtUpdated()

fun Path.mtime() =
    (Files.getAttribute(this, "lastModifiedTime") as FileTime).toMillis()

fun Path.createDirectories(): Path =
    runCatching { Files.createDirectories(this) }
        .onFailure { logger.info("Unable to create directories: $this") }
        .getOrThrow()

fun deleteMp3CoverArt(mp3AlbumPathAbsolute: String) =
    listOf(
        File("${mp3AlbumPathAbsolute}/${Config.coverArtFile}").delete(),
        File("${mp3AlbumPathAbsolute}/${Config.thumbArtFile}").delete(),
    )
        .any { !it }
