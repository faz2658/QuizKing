package com.stackroute.maverick.kafka;

import com.stackroute.maverick.domain.GameRequest;
import com.stackroute.maverick.domain.SinglePlayerResult;

/** Outgoing events of the game engine. */
public interface GameEvents {

	void requestGame(GameRequest request);

	void publishResult(SinglePlayerResult result);
}
