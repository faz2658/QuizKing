package com.fazeel.quizcadia.kafka;

import com.fazeel.quizcadia.domain.GameRequest;
import com.fazeel.quizcadia.domain.SinglePlayerResult;

/** Outgoing events of the game engine. */
public interface GameEvents {

	void requestGame(GameRequest request);

	void publishResult(SinglePlayerResult result);
}
