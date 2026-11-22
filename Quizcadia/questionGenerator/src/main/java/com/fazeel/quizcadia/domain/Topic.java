package com.fazeel.quizcadia.domain;

import java.util.List;

import org.springframework.data.annotation.Transient;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A topic inside a category (e.g. Film in Entertainment), holding its questions. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Topic {

	private int topicId;
	private String topicName;
	private String topicImage;
	private List<Questions> questions;
	/** Questions deleted from the bank, so copies elsewhere (the game manager's pools) drop them too. */
	private List<Integer> removedQuestionIds;
	/** In messages only (not stored): how many playable questions the topic has, and per level 1-3. */
	@Transient
	private Integer questionCount;
	@Transient
	private List<Integer> levelCounts;

	@JsonCreator
	public Topic() {
	}

	public Topic(int topicId, String topicName, String topicImage, List<Questions> questions) {
		this.topicId = topicId;
		this.topicName = topicName;
		this.topicImage = topicImage;
		this.questions = questions;
	}

	public int getTopicId() {
		return topicId;
	}

	public void setTopicId(int topicId) {
		this.topicId = topicId;
	}

	public String getTopicName() {
		return topicName;
	}

	public void setTopicName(String topicName) {
		this.topicName = topicName;
	}

	public String getTopicImage() {
		return topicImage;
	}

	public void setTopicImage(String topicImage) {
		this.topicImage = topicImage;
	}

	public List<Questions> getQuestions() {
		return questions;
	}

	public void setQuestions(List<Questions> questions) {
		this.questions = questions;
	}

	public List<Integer> getRemovedQuestionIds() {
		return removedQuestionIds;
	}

	public void setRemovedQuestionIds(List<Integer> removedQuestionIds) {
		this.removedQuestionIds = removedQuestionIds;
	}

	public Integer getQuestionCount() {
		return questionCount;
	}

	public void setQuestionCount(Integer questionCount) {
		this.questionCount = questionCount;
	}

	public List<Integer> getLevelCounts() {
		return levelCounts;
	}

	public void setLevelCounts(List<Integer> levelCounts) {
		this.levelCounts = levelCounts;
	}

	@Override
	public String toString() {
		return "Topic [topicId=" + topicId + ", topicName=" + topicName + ", topicImage=" + topicImage
				+ ", questions=" + questions + "]";
	}
}
