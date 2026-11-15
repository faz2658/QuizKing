package com.stackroute.maverick.domain;

public class MultiQuestion {
	private int qId;
	public String question;
	public String[] options;
	public String selectedAnswer;
	public String correctAnswer;
	public String getQuestion() {
		return question;
	}
	public void setQuestion(String question) {
		this.question = question;
	}
	public String[] getOptions() {
		return options;
	}
	public void setOptions(String[] options) {
		this.options = options;
	}
	public String getSelectedAnswer() {
		return selectedAnswer;
	}
	public void setSelectedAnswer(String selectedAnswer) {
		this.selectedAnswer = selectedAnswer;
	}
	public String getCorrectAnswer() {
		return correctAnswer;
	}
	public void setCorrectAnswer(String correctAnswer) {
		this.correctAnswer = correctAnswer;
	}
	public MultiQuestion(String question, String[] options, String selectedAnswer, String correctAnswer) {
		super();
		this.question = question;
		this.options = options;
		this.selectedAnswer = selectedAnswer;
		this.correctAnswer = correctAnswer;
	}
	
	public MultiQuestion()
	{}
	public int getqId() {
		return qId;
	}
	public void setqId(int qId) {
		this.qId = qId;
	}
}
