package com.fazeel.quizcadia.pack;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.function.IntPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.domain.Questions;

/**
 * Puts the bundled question packs (resources/packs/*.json, one per country) into the bank. A pack is only sent
 * again when its file changed since the last load, so a normal restart costs nothing even with a pack for every
 * country. Questions are matched by id, so nothing is duplicated, and questions players got retired stay retired.
 */
public class PackLoader {

	private static final Logger log = LoggerFactory.getLogger(PackLoader.class);
	public static final String PATTERN = "classpath*:packs/*.json";

	/** Remembers the content hash of each pack that was loaded. */
	public interface LoadedPacks {
		String hash(String pack);

		void loaded(String pack, String hash);
	}

	public record Pack(String name, byte[] json) {
	}

	private final QuestionBankService bank;
	private final IntPredicate retired;
	private final LoadedPacks loaded;

	public PackLoader(QuestionBankService bank, IntPredicate retired, LoadedPacks loaded) {
		this.bank = bank;
		this.retired = retired;
		this.loaded = loaded;
	}

	/** Every pack bundled with the app. */
	public static List<Pack> bundled() {
		try {
			List<Pack> packs = new ArrayList<>();
			for (Resource r : new PathMatchingResourcePatternResolver().getResources(PATTERN)) {
				try (InputStream in = r.getInputStream()) {
					packs.add(new Pack(r.getFilename(), in.readAllBytes()));
				}
			}
			packs.sort(java.util.Comparator.comparing(Pack::name));
			return packs;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Imports the packs that changed since they were last loaded. Topics with a shared category (History,
	 * Sports...) are gathered from all changed packs and imported once per shared topic, each question tagged
	 * with its pack's country; topics without one become the pack's own country topic as before.
	 *
	 * @return how many questions were new to the bank
	 */
	public int load(List<Pack> packs) {
		int added = 0;
		Map<List<String>, List<Questions>> shared = new LinkedHashMap<>();
		Map<String, String> changed = new LinkedHashMap<>();
		for (Pack p : packs) {
			String hash = sha256(p.json());
			if (hash.equals(loaded.hash(p.name()))) {
				continue;
			}
			QuestionPack pack = QuestionPack.parse(p.json());
			for (QuestionPack.PackTopic topic : pack.topics()) {
				List<Questions> questions = QuestionPack.questions(topic, pack.country()).stream()
						.filter(q -> !retired.test(q.getQuestionId())).toList();
				if (topic.shared()) {
					shared.computeIfAbsent(List.of(topic.category().trim(), topic.name().trim()), k -> new ArrayList<>())
							.addAll(questions);
				} else {
					added += bank.importQuestions(pack.category(), pack.country(), topic.name(), questions);
				}
			}
			changed.put(p.name(), hash);
		}
		for (Map.Entry<List<String>, List<Questions>> e : shared.entrySet()) {
			added += bank.importQuestions(e.getKey().get(0), null, e.getKey().get(1), e.getValue());
			log.info("Shared topic {} / {}: {} pack questions", e.getKey().get(0), e.getKey().get(1), e.getValue().size());
		}
		// only once everything is in the bank, so a failed start loads the same packs again next time
		changed.forEach(loaded::loaded);
		log.info("Question packs: {} loaded, {} unchanged", changed.size(), packs.size() - changed.size());
		return added;
	}

	static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
