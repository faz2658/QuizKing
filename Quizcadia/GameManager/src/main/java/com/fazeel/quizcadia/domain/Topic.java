package com.fazeel.quizcadia.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
public class Topic {

	 	private int topicId;
	    private String topicName;
	    private String topicImage;
	    private List<Questions> questions;
	    /** Questions deleted from the bank, so copies elsewhere (the game manager's pools) drop them too. */
	    private List<Integer> removedQuestionIds;
	    /**
	     * Set when the question service sends the topic as counts only (its questions stay in the question service,
	     * which the engines load from when a game starts): playable questions in all, and per level 1-3.
	     */
	    private Integer questionCount;
	    private List<Integer> levelCounts;

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
		@JsonCreator
		public Topic() {
			super();
			// TODO Auto-generated constructor stub
		}
		
		
		
		public Topic(int topicId, String topicName, String topicImage, List<Questions> questions) {
			super();
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
		@Override
		public String toString() {
			return "Topic [topicId=" + topicId + ", topicName=" + topicName + ", topicImage=" + topicImage
					+ ", questions=" + questions + "]";
		}
		
		

	public List<Integer> getRemovedQuestionIds() {
		return removedQuestionIds;
	}

	public void setRemovedQuestionIds(List<Integer> removedQuestionIds) {
		this.removedQuestionIds = removedQuestionIds;
	}
}
