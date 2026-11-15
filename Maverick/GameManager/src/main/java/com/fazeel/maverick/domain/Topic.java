package com.fazeel.maverick.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
public class Topic {

	 	private int topicId;
	    private String topicName;
	    private String topicImage;
	    private List<Questions> questions;
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
		
		
}
