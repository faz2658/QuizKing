#!/bin/bash
# One-time move from the old name (Quiz King: folder ~/quiz-king, compose project "quiz-king", Java package
# com.fazeel.quizking, settings QUIZKING_*) to QuizCadia. Safe to run on every deploy: each step only acts if
# something still carries the old name. Delete this file once every machine has been migrated.
#
#   deploy/rename-migration.sh before   # stop the old stack, move the folder, copy its data volumes, rename .env keys
#   deploy/rename-migration.sh after    # rewrite old names stored in Mongo documents, reinstall the budget guard
#
# DOCKER="sudo docker" on the server. The old volumes are copied, never deleted: they stay as a backup until you
# remove them yourself (docker volume rm quiz-king_mongo-data ...).
set -euo pipefail
DOCKER=${DOCKER:-docker}
OLD_PROJECT=${OLD_PROJECT:-quiz-king}
NEW_PROJECT=${NEW_PROJECT:-quizcadia}
OLD_DIR="${OLD_DIR:-$HOME/quiz-king}"
NEW_DIR="${NEW_DIR:-$HOME/quizcadia}"
VOLUMES=(mongo-data mysql-data neo4j-data caddy-data caddy-config)

volume_exists() { $DOCKER volume inspect "$1" >/dev/null 2>&1; }

before() {
	# Stop the old containers first: they hold the ports, and the databases must not change while being copied
	if [ -n "$($DOCKER ps -aq --filter "label=com.docker.compose.project=${OLD_PROJECT}")" ]; then
		echo "==> Stopping the old '${OLD_PROJECT}' containers"
		$DOCKER compose -p "$OLD_PROJECT" down --remove-orphans || true
		# compose can miss containers it doesn't fully recognise: remove whatever still carries the old project
		local left
		left=$($DOCKER ps -aq --filter "label=com.docker.compose.project=${OLD_PROJECT}")
		if [ -n "$left" ]; then
			$DOCKER stop $left >/dev/null
			$DOCKER rm $left >/dev/null
		fi
		if [ -n "$($DOCKER ps -q --filter "label=com.docker.compose.project=${OLD_PROJECT}")" ]; then
			echo "!! The old containers are still running; not copying live databases" >&2
			exit 1
		fi
	fi

	# The folder holds .env, secrets/ and the AI model: move it as a whole
	if [ -d "$OLD_DIR" ] && [ ! -d "$NEW_DIR" ]; then
		echo "==> Moving $OLD_DIR to $NEW_DIR"
		mv "$OLD_DIR" "$NEW_DIR"
	fi

	if [ -f "$NEW_DIR/.env" ]; then
		# Settings keep their values; only the names change
		perl -pi -e 's/^QUIZKING_/QUIZCADIA_/' "$NEW_DIR/.env"
		# The databases were created with the old development passwords where .env didn't set its own; those live
		# inside the data, so keep using them
		grep -q '^MYSQL_USER=' "$NEW_DIR/.env" || echo "MYSQL_USER=quizking" >>"$NEW_DIR/.env"
		grep -q '^MYSQL_PASSWORD=' "$NEW_DIR/.env" || echo "MYSQL_PASSWORD=quizking-dev-password" >>"$NEW_DIR/.env"
		grep -q '^NEO4J_PASSWORD=' "$NEW_DIR/.env" || echo "NEO4J_PASSWORD=quizking-dev-password" >>"$NEW_DIR/.env"
	fi

	local model="$NEW_DIR/ai/question-model/models"
	if [ -f "$model/quiz-king-q4_k_m.gguf" ] && [ ! -f "$model/quizcadia-q4_k_m.gguf" ]; then
		mv "$model/quiz-king-q4_k_m.gguf" "$model/quizcadia-q4_k_m.gguf"
		echo "==> Renamed the AI model file"
	fi

	local pending=()
	for v in "${VOLUMES[@]}"; do
		if volume_exists "${OLD_PROJECT}_$v" && ! volume_exists "${NEW_PROJECT}_$v"; then
			pending+=("$v")
		fi
	done
	[ ${#pending[@]} -eq 0 ] && return 0

	echo "==> Copying data from '${OLD_PROJECT}' to '${NEW_PROJECT}': ${pending[*]}"
	for v in "${pending[@]}"; do
		$DOCKER volume create --label "com.docker.compose.project=${NEW_PROJECT}" \
			--label "com.docker.compose.volume=$v" "${NEW_PROJECT}_$v" >/dev/null
		$DOCKER run --rm -v "${OLD_PROJECT}_$v:/from:ro" -v "${NEW_PROJECT}_$v:/to" alpine:3.20 \
			sh -c 'cp -a /from/. /to/'
		echo "    copied ${OLD_PROJECT}_$v -> ${NEW_PROJECT}_$v"
	done
}

after() {
	local mongo
	mongo=$($DOCKER ps -q --filter "label=com.docker.compose.project=${NEW_PROJECT}" \
		--filter "label=com.docker.compose.service=mongo" | head -1)
	if [ -n "$mongo" ]; then
		# Spring Data saved each document's Java class (com.fazeel.quizking.domain.Game ...); point them at the new
		# package. Built-in games were tagged with the creator "Quiz King".
		$DOCKER exec "$mongo" mongosh --quiet --eval '
			const from = "com.fazeel.quizking.", to = "com.fazeel.quizcadia.";
			let classes = 0, creators = 0;
			db.getMongo().getDBNames().filter(d => !["admin", "config", "local"].includes(d)).forEach(d => {
				const database = db.getSiblingDB(d);
				database.getCollectionNames().forEach(c => {
					classes += database[c].updateMany({ _class: { $regex: "^com\\.fazeel\\.quizking\\." } },
						[{ $set: { _class: { $replaceOne: { input: "$_class", find: from, replacement: to } } } }]).modifiedCount;
					creators += database[c].updateMany({ createdBy: "Quiz King" }, { $set: { createdBy: "QuizCadia" } }).modifiedCount;
				});
			});
			if (classes || creators) print("==> Mongo: updated " + classes + " stored class names, " + creators + " built-in game creators");'
	fi

	# The spending guard was installed by hand under the old name and pointed at the old folder
	if [ -f /usr/local/bin/quizking-budget-guard ] && [ -f "$NEW_DIR/deploy/budget-guard/quizcadia-budget-guard" ]; then
		echo "==> Reinstalling the budget guard as quizcadia-budget-guard"
		sudo systemctl disable --now quizking-budget-guard.timer >/dev/null 2>&1 || true
		sudo rm -f /etc/systemd/system/quizking-budget-guard.service /etc/systemd/system/quizking-budget-guard.timer \
			/usr/local/bin/quizking-budget-guard
		sudo install -m 755 "$NEW_DIR/deploy/budget-guard/quizcadia-budget-guard" /usr/local/bin/quizcadia-budget-guard
		sudo install -m 644 "$NEW_DIR/deploy/budget-guard/quizcadia-budget-guard.service" \
			"$NEW_DIR/deploy/budget-guard/quizcadia-budget-guard.timer" /etc/systemd/system/
		sudo systemctl daemon-reload
		sudo systemctl enable --now quizcadia-budget-guard.timer >/dev/null
	fi
}

case "${1:-}" in
before) before ;;
after) after ;;
*) echo "usage: $0 before|after" >&2; exit 2 ;;
esac
