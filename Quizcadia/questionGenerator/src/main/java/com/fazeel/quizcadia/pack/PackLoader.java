package com.fazeel.quizcadia.pack;

import java.util.List;
import java.util.function.IntPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.domain.Questions;

/**
 * Puts the bundled question packs into the bank. Safe to run on every start: questions are matched by id,
 * so nothing is duplicated, and questions players got retired stay retired.
 */
public class PackLoader {

	private static final Logger log = LoggerFactory.getLogger(PackLoader.class);
	public static final List<String> PACKS = List.of("packs/south-asia.json");

	private final QuestionBankService bank;
	private final IntPredicate retired;

	public PackLoader(QuestionBankService bank, IntPredicate retired) {
		this.bank = bank;
		this.retired = retired;
	}

	/** @return how many questions were new to the bank */
	public int load(List<String> resources) {
		int added = 0;
		for (String resource : resources) {
			QuestionPack pack = QuestionPack.load(resource);
			for (QuestionPack.PackTopic topic : pack.topics()) {
				List<Questions> questions = QuestionPack.questions(topic).stream()
						.filter(q -> !retired.test(q.getQuestionId())).toList();
				added += bank.importQuestions(pack.category(), topic.name(), questions);
			}
			log.info("Question pack {} loaded: {} topics", resource, pack.topics().size());
		}
		return added;
	}
}
