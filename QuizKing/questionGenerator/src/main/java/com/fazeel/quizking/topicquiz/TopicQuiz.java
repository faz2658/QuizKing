package com.fazeel.quizking.topicquiz;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** A quiz the AI model writes about any topic a player types, shared as a link. */
@Document(collection = "topic_quizzes")
public class TopicQuiz {

	public enum Status {
		GENERATING, READY, FAILED
	}

	/** One question; {@code answer} is the index of the right option. */
	public record Question(String text, List<String> options, int answer) {
	}

	@Id
	private String id;
	private String topic;
	@Indexed
	private int creatorId;
	private String creatorName;
	@Indexed
	private Status status = Status.GENERATING;
	private int target;
	private List<Question> questions = new ArrayList<>();
	private String problem;
	@Indexed
	private Instant createdAt;

	protected TopicQuiz() {
	}

	public TopicQuiz(String id, String topic, int creatorId, String creatorName, int target, Instant createdAt) {
		this.id = id;
		this.topic = topic;
		this.creatorId = creatorId;
		this.creatorName = creatorName;
		this.target = target;
		this.createdAt = createdAt;
	}

	void add(Question q) {
		questions.add(q);
	}

	void ready() {
		status = Status.READY;
	}

	void failed(String why) {
		status = Status.FAILED;
		problem = why;
	}

	public String getId() {
		return id;
	}

	public String getTopic() {
		return topic;
	}

	public int getCreatorId() {
		return creatorId;
	}

	public String getCreatorName() {
		return creatorName;
	}

	public Status getStatus() {
		return status;
	}

	public int getTarget() {
		return target;
	}

	public List<Question> getQuestions() {
		return questions;
	}

	public String getProblem() {
		return problem;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
