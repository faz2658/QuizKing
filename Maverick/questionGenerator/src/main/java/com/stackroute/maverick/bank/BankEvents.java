package com.stackroute.maverick.bank;

import com.stackroute.maverick.domain.Category;

public interface BankEvents {

	/** Publishes (part of) a category so the game manager can merge it into its bank. */
	void categoryChanged(Category category);
}
