package com.fazeel.quizking.bank;

import com.fazeel.quizking.domain.Category;

public interface BankEvents {

	/** Publishes (part of) a category so the game manager can merge it into its bank. */
	void categoryChanged(Category category);
}
