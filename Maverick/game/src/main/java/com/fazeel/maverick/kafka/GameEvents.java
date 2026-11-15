package com.fazeel.maverick.kafka;

import com.fazeel.maverick.domain.GameRequest;
import com.fazeel.maverick.domain.SinglePlayerResult;

/** Outgoing events of the game engine. */
public interface GameEvents {

	void requestGame(GameRequest request);

	void publishResult(SinglePlayerResult result);
}
