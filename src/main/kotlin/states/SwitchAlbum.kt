package com.eigenholser.flac2mp3.states

import com.eigenholser.flac2mp3.toInfo
import org.jeasy.states.api.AbstractEvent
import org.jeasy.states.api.EventHandler
import java.util.logging.Logger

class SwitchAlbum : EventHandler<AbstractEvent> {
    override fun handleEvent(event: AbstractEvent) {
        logger.info("SwitchAlbum: Notified of event: ${event.name}".toInfo())
    }

    companion object {
        private val logger = Logger.getLogger("SwitchAlbum")
    }
}

