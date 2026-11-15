package com.fazeel.maverick.bank;

import com.fazeel.maverick.domain.Category;

public interface BankEvents {

	/** Publishes (part of) a category so the game manager can merge it into its bank. */
	void categoryChanged(Category category);
}
