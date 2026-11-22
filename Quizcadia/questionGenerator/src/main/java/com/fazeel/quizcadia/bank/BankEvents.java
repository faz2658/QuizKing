package com.fazeel.quizcadia.bank;

import com.fazeel.quizcadia.domain.Category;

/** Tells other services that the bank changed. */
public interface BankEvents {

	/** Publishes (part of) a category so the game manager can merge it into its bank. */
	void categoryChanged(Category category);
}
