package com.fazeel.quizcadia.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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

import com.fazeel.quizcadia.ai.AiUnavailableException;
import com.fazeel.quizcadia.ai.DraftQuestion;
import com.fazeel.quizcadia.ai.DraftService;
import com.fazeel.quizcadia.ai.DraftService.GenerationResult;
import com.fazeel.quizcadia.bank.InvalidQuestionException;
import com.fazeel.quizcadia.bank.NotFoundException;
import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.bank.TopicCounts;
import com.fazeel.quizcadia.domain.Category;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.domain.Topic;
import com.fazeel.quizcadia.opentdb.ImportStatus;
import com.fazeel.quizcadia.opentdb.OpenTriviaImporter;

@RestController
@RequestMapping("/api/v1/question-generator")
public class QuestionBankController {

	private final QuestionBankService bank;
	private final OpenTriviaImporter importer;
	private final DraftService drafts;

	public QuestionBankController(QuestionBankService bank, OpenTriviaImporter importer, DraftService drafts) {
		this.bank = bank;
		this.importer = importer;
		this.drafts = drafts;
	}

	public record GenerateRequest(int categoryId, String topicName, int level, boolean trueFalse, int count) {
	}

	/** Whether a question model is configured. */
	@GetMapping("/ai/status")
	public Map<String, Boolean> aiStatus() {
		return Map.of("available", drafts.available());
	}

	/** Asks the question model for new questions; they are queued as drafts for review. */
	@PostMapping("/ai/drafts")
	public GenerationResult generateDrafts(@RequestBody GenerateRequest r) {
		return drafts.generate(r.categoryId(), r.topicName(), r.level(), r.trueFalse(), r.count());
	}

	@GetMapping("/ai/drafts")
	public List<DraftQuestion> pendingDrafts() {
		return drafts.pending();
	}

	/** Approves a draft, optionally with corrections in the body; it then joins its topic's games. */
	@PostMapping("/ai/drafts/{draftId}/approve")
	public Questions approveDraft(@PathVariable String draftId, @RequestBody(required = false) Questions edited) {
		return drafts.approve(draftId, edited);
	}

	@DeleteMapping("/ai/drafts/{draftId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void rejectDraft(@PathVariable String draftId) {
		drafts.reject(draftId);
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

	/** How long the topic counts are kept; also how stale a browser or CDN may show them. */
	static final Duration COUNTS_FOR = Duration.ofSeconds(10);

	private record Counts(List<TopicCounts.TopicCount> items, long at) {
	}

	private volatile Counts counts;

	/**
	 * Question counts per category and topic. Public (the welcome page shows them to every visitor), so they are
	 * counted at most every few seconds and may be cached briefly by browsers and a CDN. The admin page's import
	 * progress still moves along, a few seconds behind.
	 */
	@GetMapping("/stats")
	public ResponseEntity<List<TopicCounts.TopicCount>> stats() {
		long now = System.currentTimeMillis();
		Counts c = counts;
		if (c == null || now - c.at() >= COUNTS_FOR.toMillis()) {
			c = new Counts(List.copyOf(bank.topicCounts()), now);
			counts = c;
		}
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(COUNTS_FOR).cachePublic()
						.staleWhileRevalidate(Duration.ofMinutes(1)))
				.body(c.items());
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

	@ExceptionHandler(AiUnavailableException.class)
	ProblemDetail aiUnavailable(AiUnavailableException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
	}

	@ExceptionHandler(RestClientException.class)
	ProblemDetail upstream(RestClientException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Wikidata is not reachable right now");
	}
}
