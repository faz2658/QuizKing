package com.fazeel.quizcadia.pack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.bank.QuestionRules;
import com.fazeel.quizcadia.domain.Questions;

class PackLoaderTest {

	/** ISO codes the app's country picker offers (frontend core/countries.ts). */
	static final Set<String> COUNTRIES = Set.of((
			"AF AL DZ AD AO AG AR AM AU AT AZ BS BH BD BB BY BE BZ BJ BT BO BA BW BR BN BG BF BI KH CM CA CV CF TD CL CN CO KM CG CD CR CI HR CU CY CZ "
					+ "DK DJ DM DO EC EG SV GQ ER EE SZ ET FJ FI FR GA GM GE DE GH GR GD GT GN GW GY HT HN HK HU IS IN ID IR IQ IE IL IT JM JP JO KZ KE KI KW KG "
					+ "LA LV LB LS LR LY LI LT LU MO MG MW MY MV ML MT MH MR MU MX FM MD MC MN ME MA MZ MM NA NR NP NL NZ NI NE NG KP MK NO OM PK PW PS PA PG PY PE "
					+ "PH PL PT PR QA RO RU RW KN LC VC WS SM ST SA SN RS SC SL SG SK SI SB SO ZA KR SS ES LK SD SR SE CH SY TW TJ TZ TH TL TG TO TT TN TR TM TV "
					+ "UG UA AE GB US UY UZ VU VA VE VN YE ZM ZW").split(" "));

	static class Memory implements PackLoader.LoadedPacks {
		final Map<String, String> hashes = new HashMap<>();

		@Override
		public String hash(String pack) {
			return hashes.get(pack);
		}

		@Override
		public void loaded(String pack, String hash) {
			hashes.put(pack, hash);
		}
	}

	/** The shared world topics on the live site that country questions join, as category / topic. */
	static final Set<String> SHARED_TOPICS = Set.of("History / History", "Geography / Geography", "Sports / Sports",
			"Entertainment / Music", "Entertainment / Film", "Entertainment / Books", "Politics / Politics", "Art / Art",
			"Animals / Animals", "General Knowledge / General Knowledge");

	@Test
	void everyPackQuestionIsPlayableAndGoesIntoASharedTopic() {
		Set<Integer> ids = new HashSet<>();
		Set<String> countries = new HashSet<>();
		Map<String, Integer> perSharedTopic = new HashMap<>();
		List<PackLoader.Pack> packs = PackLoader.bundled();
		assertThat(packs).hasSizeGreaterThanOrEqualTo(199);
		for (PackLoader.Pack p : packs) {
			QuestionPack pack = QuestionPack.parse(p.json());
			assertThat(pack.category()).as(p.name()).isNotBlank();
			assertThat(COUNTRIES).as(p.name() + " country").contains(pack.country());
			assertThat(countries.add(pack.country())).as("two packs for " + pack.country()).isTrue();
			assertThat(p.name()).as("file is named after its country").isEqualTo(pack.country().toLowerCase() + ".json");
			Set<String> own = new HashSet<>();
			for (QuestionPack.PackTopic topic : pack.topics()) {
				String key = topic.category() + " / " + topic.name();
				assertThat(topic.shared()).as(p.name() + " " + topic.name() + " has a shared category").isTrue();
				assertThat(SHARED_TOPICS).as(p.name()).contains(key);
				assertThat(own.add(key)).as(p.name() + " lists " + key + " twice").isTrue();
				List<Questions> qs = QuestionPack.questions(topic, pack.country());
				assertThat(qs).as(key).isNotEmpty().allMatch(q -> pack.country().equals(q.getCountry()));
				perSharedTopic.merge(key, qs.size(), Integer::sum);
				for (Questions q : qs) {
					assertThatCode(() -> QuestionRules.validate(q)).as(q.getQuestionStem()).doesNotThrowAnyException();
					assertThat(q.getOption4()).as(q.getQuestionStem()).isNotBlank();
					assertThat(q.getExplanation()).as(q.getQuestionStem()).isNotBlank();
					// ids are global: a clash would let one question overwrite another; reword one of them
					assertThat(ids.add(q.getQuestionId())).as("id clash, reword: " + q.getQuestionStem()).isTrue();
				}
			}
		}
		// every shared topic gets a real share of country questions
		assertThat(perSharedTopic.keySet()).containsExactlyInAnyOrderElementsOf(SHARED_TOPICS);
		perSharedTopic.forEach((k, n) -> assertThat(n).as(k).isGreaterThanOrEqualTo(100));
	}

	@Test
	void idsAndOptionOrderAreStableSoReloadingUpdatesInsteadOfDuplicating() {
		QuestionPack.PackTopic topic = QuestionPack.load("packs/in.json").topics().get(0);
		List<Questions> a = QuestionPack.questions(topic);
		List<Questions> b = QuestionPack.questions(topic);
		for (int i = 0; i < a.size(); i++) {
			assertThat(a.get(i).getQuestionId()).isEqualTo(b.get(i).getQuestionId())
					.isBetween(QuestionPack.PACK_ID_BASE, QuestionPack.PACK_ID_BASE + QuestionPack.PACK_ID_SPAN - 1);
			assertThat(a.get(i).getOption1()).isEqualTo(b.get(i).getOption1());
		}
		// the answer is not always in the same place
		assertThat(a.stream().map(q -> q.getOption4().equals(q.getCorrectAnswer())).distinct()).hasSize(2);
	}

	@Test
	@SuppressWarnings("unchecked")
	void putsEachTopicIntoItsSharedTopicTaggedWithTheCountryAndLeavesRetiredQuestionsOut() {
		QuestionBankService bank = mock(QuestionBankService.class);
		PackLoader.Pack india = PackLoader.bundled().stream().filter(p -> p.name().equals("in.json")).findFirst().orElseThrow();
		QuestionPack pack = QuestionPack.parse(india.json());
		QuestionPack.PackTopic first = pack.topics().get(0);
		int retiredId = QuestionPack.questions(first).get(0).getQuestionId();

		new PackLoader(bank, id -> id == retiredId, new Memory()).load(List.of(india));

		// shared topics are not country categories: the country travels on each question instead
		verify(bank, never()).importQuestions(anyString(), eq("IN"), anyString(), anyList());
		verify(bank, times(pack.topics().size())).importQuestions(anyString(), isNull(), anyString(), anyList());
		ArgumentCaptor<List<Questions>> captured = ArgumentCaptor.forClass(List.class);
		verify(bank).importQuestions(eq(first.category()), isNull(), eq(first.name()), captured.capture());
		assertThat(captured.getValue()).hasSize(first.questions().size() - 1)
				.noneMatch(q -> q.getQuestionId() == retiredId)
				.allMatch(q -> "IN".equals(q.getCountry())); // each question carries its country for the game mix
	}

	@Test
	@SuppressWarnings("unchecked")
	void aSharedTopicIsImportedOnceWithTheQuestionsOfEveryChangedPack() {
		QuestionBankService bank = mock(QuestionBankService.class);
		List<PackLoader.Pack> packs = PackLoader.bundled();

		new PackLoader(bank, id -> false, new Memory()).load(packs);

		ArgumentCaptor<List<Questions>> history = ArgumentCaptor.forClass(List.class);
		verify(bank, times(1)).importQuestions(eq("History"), isNull(), eq("History"), history.capture());
		assertThat(history.getValue().stream().map(Questions::getCountry).distinct().count()).isGreaterThan(150);
		verify(bank, times(SHARED_TOPICS.size())).importQuestions(anyString(), isNull(), anyString(), anyList());
	}

	@Test
	void onlyPacksThatChangedSinceTheLastStartAreSentAgain() {
		QuestionBankService bank = mock(QuestionBankService.class);
		Memory memory = new Memory();
		List<PackLoader.Pack> packs = PackLoader.bundled();
		PackLoader loader = new PackLoader(bank, id -> false, memory);

		loader.load(packs);
		clearInvocations(bank);
		loader.load(packs); // a normal restart
		verify(bank, never()).importQuestions(anyString(), any(), anyString(), anyList());

		PackLoader.Pack india = packs.stream().filter(p -> p.name().equals("in.json")).findFirst().orElseThrow();
		String edited = new String(india.json()).replace("The Wall", "The Great Wall");
		loader.load(List.of(new PackLoader.Pack("in.json", edited.getBytes())));
		verify(bank, times(QuestionPack.parse(india.json()).topics().size())).importQuestions(anyString(), isNull(),
				anyString(), anyList());
	}
}
