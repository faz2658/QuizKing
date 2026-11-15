package com.stackroute.maverick.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.stackroute.maverick.domain.Game;
import com.stackroute.maverick.session.QuizBuilder;
import com.stackroute.maverick.session.SessionQuestion;

import tools.jackson.databind.json.JsonMapper;

class DemoDataTest {

	@Test
	void everyDemoGameIsPlayableAndHonoursItsLevelMix() throws Exception {
		List<Game> games = DemoData.read(JsonMapper.builder().build());

		assertThat(games).hasSizeGreaterThanOrEqualTo(3);
		QuizBuilder builder = new QuizBuilder(new Random(3));
		for (Game g : games) {
			List<SessionQuestion> qs = builder.build(g);
			var mix = g.getQuestionLevels();
			assertThat(qs).as(g.getGameName())
					.hasSize(mix.getEasyLevel() + mix.getMediumLevel() + mix.getAdvancedLevel());
		}
	}
}
