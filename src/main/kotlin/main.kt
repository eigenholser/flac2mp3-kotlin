package com.eigenholser.flac2mp3

import com.eigenholser.flac2mp3.Tag.albumArtTagExists
import com.eigenholser.flac2mp3.Tag.readAudioFile
import com.eigenholser.flac2mp3.rules.*
import com.eigenholser.flac2mp3.states.AlbumState.albumStateMachine
import com.eigenholser.flac2mp3.states.AlbumState.state
import com.eigenholser.flac2mp3.states.AlbumStates
import com.eigenholser.flac2mp3.states.ExistingAlbumEvent
import org.jeasy.rules.api.Fact
import org.jeasy.rules.api.Facts
import org.jeasy.rules.api.Rules
import org.jeasy.rules.api.RulesEngineParameters
import org.jeasy.rules.core.DefaultRulesEngine
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.util.logging.Logger
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists

val logger: Logger = Logger.getLogger("main")

@ExperimentalPathApi
fun main(args: Array<String>) {
    /* Leaving the DB stuff for now. May return to it later. */
    // val db = DbSettings.db
    // FlacDatabase.createDatabase()

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
                }
                .also { rulesEngine.fire(Rules(NewAlbum(albumStateMachine)), it) }

            if (AlbumStates.valueOf(albumStateMachine.currentState.name) == AlbumStates.NEW_ALBUM) {
                deleteMp3CoverArt(state.prevMp3AlbumPath)

                state.nextAlbum = track.currentAlbum
                state.prevMp3AlbumPath = track.mp3Album
                Files.createDirectories(Paths.get(track.mp3Album))

                fireAlbumArtRules(track)
                    .also { albumStateMachine.fire(ExistingAlbumEvent()) }
            } else {
                fireAlbumArtRules(track)
            }

            LameFlac2Mp3.flac2mp3(flacSrc = track.flacFile, mp3Dest = track.mp3File)
            Tag.writeMp3Tags(
                mp3File = track.mp3File,
                mp3AlbumPath = track.mp3Album,
                flacAudioFile = readAudioFile(track.flacFile)
            )
        }

    // Delete the last album art
    deleteMp3CoverArt(state.prevMp3AlbumPath)
}

fun fireAlbumArtRules(track: TrackData) {
    val parameters =
        RulesEngineParameters().skipOnFirstAppliedRule(true)
    val albumArtFacts =
        Facts()
            .apply {
                add(Fact(AlbumArtFacts.TRACK_DATA.name, track))
                add(Fact(AlbumArtFacts.ALBUM_STATE.name, albumStateMachine))
            }

    DefaultRulesEngine(parameters)
        .fire(Rules(ArtNewMp3(), ArtUpdateIDv3()), albumArtFacts)
}

data class TrackData(
    val flacFile: String,
    val flacAlbum: String,
    val currentAlbum: String,
    val mp3Album: String,
    val mp3File: String,
    val fsize: Long,
    val mtime: Long
)

fun File.toTrackData(): TrackData {
    val mp3Extension = "mp3"
    val extensionSeparator = "."
    val dirSeparator = "/"
    val fsize = Files.getAttribute(toPath(), "size") as Long
    val mtime = Files.getAttribute(toPath(), "lastModifiedTime") as FileTime
    val flacTrackname = name
    val trackName = nameWithoutExtension
    val mp3TrackName = trackName + extensionSeparator + mp3Extension
    val flacAlbum = absolutePath.removeSuffix(dirSeparator + flacTrackname)
    val currentAlbum = flacAlbum
        .removePrefix(Config.flacRoot + dirSeparator)
        .removeSuffix(dirSeparator + flacTrackname)
    val mp3Album = Config.mp3Root + dirSeparator + currentAlbum
    val mp3File = mp3Album + dirSeparator + mp3TrackName

    return TrackData(
        flacFile = absolutePath,
        flacAlbum = flacAlbum,
        currentAlbum = currentAlbum,
        mp3Album = mp3Album,
        mp3File = mp3File,
        fsize = fsize,
        mtime = mtime.toMillis()
    )
}

fun TrackData.isCurrent() =
    if (mp3FileExists()) {
        val flacMtime = Files.getAttribute(Paths.get(flacFile), "lastModifiedTime") as FileTime
        val mp3Mtime = Files.getAttribute(Paths.get(mp3File), "lastModifiedTime") as FileTime
        flacMtime.toMillis() < mp3Mtime.toMillis()
    } else {
        false
    }

fun TrackData.mp3FileExists() = File(mp3File).exists()

fun TrackData.albumArtPNGExists() = Paths.get(flacAlbum).resolve(Config.albumArtFile).exists()

fun TrackData.isAlbumArtUpdated() =
    if (mp3FileExists() && albumArtPNGExists()) {
        val albumArtMtime = Files.getAttribute(
            Paths.get(flacAlbum).resolve(Config.albumArtFile), "lastModifiedTime"
        ) as FileTime
        val mp3Mtime = Files.getAttribute(Paths.get(mp3File), "lastModifiedTime") as FileTime

        readAudioFile(mp3File)
            .run { (albumArtTagExists() && albumArtMtime.toMillis() > mp3Mtime.toMillis()) || !albumArtTagExists() }
    } else {
        false
    }

fun TrackData.isStale() = !isCurrent() || isAlbumArtUpdated()

fun deleteMp3CoverArt(mp3AlbumPathAbsolute: String) =
    File("${mp3AlbumPathAbsolute}/${Config.coverArtFile}").delete()
