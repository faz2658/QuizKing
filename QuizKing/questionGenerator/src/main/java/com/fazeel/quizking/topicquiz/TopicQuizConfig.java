package com.fazeel.quizking.topicquiz;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TopicQuizConfig {

	/** One quiz at a time: the model serves one request at a time anyway, so a queue is fairer than parallel calls. */
	@Bean(destroyMethod = "shutdownNow")
	ExecutorService topicQuizExecutor() {
		return Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "topic-quiz");
			t.setDaemon(true);
			return t;
		});
	}
}
