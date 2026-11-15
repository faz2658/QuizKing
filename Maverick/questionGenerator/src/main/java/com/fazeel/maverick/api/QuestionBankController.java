package com.fazeel.maverick.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import com.fazeel.maverick.bank.InvalidQuestionException;
import com.fazeel.maverick.bank.NotFoundException;
import com.fazeel.maverick.bank.QuestionBankService;
import com.fazeel.maverick.domain.Category;
import com.fazeel.maverick.domain.Questions;
import com.fazeel.maverick.domain.Topic;
import com.fazeel.maverick.opentdb.ImportStatus;
import com.fazeel.maverick.opentdb.OpenTriviaImporter;

@RestController
@RequestMapping("/api/v1/question-generator")
public class QuestionBankController {

	private final QuestionBankService bank;
	private final OpenTriviaImporter importer;

	public QuestionBankController(QuestionBankService bank, OpenTriviaImporter importer) {
		this.bank = bank;
		this.importer = importer;
	}

	/** Starts importing Open Trivia DB (about 10 minutes); poll the GET endpoint for progress. */
	@PostMapping("/import/opentdb")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ImportStatus startImport() {
		return importer.start();
	}

	@GetMapping("/import/opentdb")
	public ImportStatus importStatus() {
		return importer.status();
	}

	/** Question counts per category and topic. */
	@GetMapping("/stats")
	public List<java.util.Map<String, Object>> stats() {
		return bank.categories().stream().flatMap(c -> (c.getTopic() == null ? List.<Topic>of() : c.getTopic()).stream()
				.map(t -> java.util.Map.<String, Object>of("category", c.getCategoryName(), "topic", t.getTopicName(),
						"questions", t.getQuestions() == null ? 0 : t.getQuestions().size())))
				.toList();
	}

	@GetMapping("/category")
	public List<Category> categories() {
		return bank.categories();
	}

	@PostMapping("/category")
	@ResponseStatus(HttpStatus.CREATED)
	public Category createCategory(@RequestBody Category category) {
		return bank.createCategory(category.getCategoryName(), category.getCategoryImage());
	}

	@GetMapping("/categoryId/{categoryId}")
	public Category category(@PathVariable int categoryId) {
		return bank.category(categoryId);
	}

	@PostMapping("/category/{categoryId}/topics")
	@ResponseStatus(HttpStatus.CREATED)
	public Category addTopic(@PathVariable int categoryId, @RequestBody Topic topic) {
		return bank.addTopic(categoryId, topic.getTopicName(), topic.getTopicImage());
	}

	@GetMapping("/category/{categoryId}/{topicName}/questions")
	public List<Questions> questions(@PathVariable int categoryId, @PathVariable String topicName) {
		return bank.questions(categoryId, topicName);
	}

	@PostMapping("/category/{categoryId}/{topicName}/questions")
	@ResponseStatus(HttpStatus.CREATED)
	public Questions addQuestion(@PathVariable int categoryId, @PathVariable String topicName,
			@RequestBody Questions question) {
		return bank.addQuestion(categoryId, topicName, question);
	}

	@PutMapping("/category/{categoryId}/{topicName}/{questionId}")
	public Questions updateQuestion(@PathVariable int categoryId, @PathVariable String topicName,
			@PathVariable int questionId, @RequestBody Questions question) {
		return bank.updateQuestion(categoryId, topicName, questionId, question);
	}

	@DeleteMapping("/category/{categoryId}/{topicName}/{questionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteQuestion(@PathVariable int categoryId, @PathVariable String topicName,
			@PathVariable int questionId) {
		bank.deleteQuestion(categoryId, topicName, questionId);
	}

	/** Generates questions from Wikidata, e.g. {@code ?template=capitals&count=30}. */
	@PostMapping("/category/{categoryId}/{topicName}/auto-questions")
	public List<Questions> generate(@PathVariable int categoryId, @PathVariable String topicName,
			@RequestParam String template, @RequestParam(defaultValue = "20") int count) {
		return bank.generate(categoryId, topicName, template, count);
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail notFound(NotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(InvalidQuestionException.class)
	ProblemDetail invalid(InvalidQuestionException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
	}

	@ExceptionHandler(RestClientException.class)
	ProblemDetail upstream(RestClientException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Wikidata is not reachable right now");
	}
}
