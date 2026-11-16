package com.fazeel.quizcadia.catalog;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Counter document used to hand out unique, increasing game ids. */
@Document(collection = "sequences")
public class Sequence {

	@Id
	private String id;
	private long value;

	public String getId() {
		return id;
	}

	public long getValue() {
		return value;
	}
}
