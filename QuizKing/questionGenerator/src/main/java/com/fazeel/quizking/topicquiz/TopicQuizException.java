package com.fazeel.quizking.topicquiz;

/** A topic-quiz rule was broken. {@code busy} means try again later (too many quizzes being written). */
public class TopicQuizException extends RuntimeException {

	private final boolean busy;
	private final boolean notFound;

	TopicQuizException(String message) {
		this(message, false, false);
	}

	private TopicQuizException(String message, boolean busy, boolean notFound) {
		super(message);
		this.busy = busy;
		this.notFound = notFound;
	}

	static TopicQuizException busy(String message) {
		return new TopicQuizException(message, true, false);
	}

	static TopicQuizException notFound(String message) {
		return new TopicQuizException(message, false, true);
	}

	public boolean isBusy() {
		return busy;
	}

	public boolean isNotFound() {
		return notFound;
	}
}
