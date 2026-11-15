package com.fazeel.quizking.kafka;

import com.fazeel.quizking.domain.GameRequest;
import com.fazeel.quizking.domain.SinglePlayerResult;

/** Outgoing events of the game engine. */
public interface GameEvents {

	void requestGame(GameRequest request);

	void publishResult(SinglePlayerResult result);
}
