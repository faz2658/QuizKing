#!/bin/bash
# One-time move from the project's old name ("maverick") to Quiz King, safe to run on every deploy:
# each step only acts if something still carries the old name. Delete this file once every machine
# (the live server and each developer's laptop) has been migrated.
#
#   deploy/rename-migration.sh before   # stop the old stack, copy its data volumes, rename .env keys
#   deploy/rename-migration.sh after    # rewrite old Java class names stored in Mongo documents
#
# Run from the project folder (where .env and docker-compose.yml are). DOCKER="sudo docker" on the server.
# The old volumes are copied, never deleted: they stay as a backup until you remove them yourself.
set -euo pipefail
DOCKER=${DOCKER:-docker}
OLD_PROJECT=maverick
NEW_PROJECT=quiz-king
VOLUMES=(mongo-data mysql-data neo4j-data caddy-data caddy-config)

volume_exists() { $DOCKER volume inspect "$1" >/dev/null 2>&1; }

before() {
	local old_data=false
	for v in "${VOLUMES[@]}"; do
		volume_exists "${OLD_PROJECT}_$v" && old_data=true
	done

	if [ -f .env ]; then
		# Settings keep their values; only the names change
		perl -pi -e 's/^MAVERICK_/QUIZKING_/' .env
		if $old_data; then
			# The old databases were created with these, and they live inside the data, so keep using them
			grep -q '^MYSQL_USER=' .env || echo "MYSQL_USER=${OLD_PROJECT}" >>.env
			grep -q '^MYSQL_PASSWORD=' .env || echo "MYSQL_PASSWORD=${OLD_PROJECT}-dev-password" >>.env
			grep -q '^NEO4J_PASSWORD=' .env || echo "NEO4J_PASSWORD=${OLD_PROJECT}-dev-password" >>.env
		fi
	fi

	# Stop the old containers (they hold the ports, and the databases must not change while being copied)
	if [ -n "$($DOCKER ps -aq --filter "label=com.docker.compose.project=${OLD_PROJECT}")" ]; then
		echo "==> Stopping the old '${OLD_PROJECT}' containers"
		$DOCKER compose -p "$OLD_PROJECT" down --remove-orphans
	fi

	local pending=()
	for v in "${VOLUMES[@]}"; do
		if volume_exists "${OLD_PROJECT}_$v" && ! volume_exists "${NEW_PROJECT}_$v"; then
			pending+=("$v")
		fi
	done
	[ ${#pending[@]} -eq 0 ] && return

	echo "==> Copying data from the '${OLD_PROJECT}' stack to '${NEW_PROJECT}': ${pending[*]}"
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
	[ -n "$mongo" ] || return 0
	# Spring Data saved each document's Java class (e.g. com.fazeel.<old>.domain.Game); point them at the new package
	$DOCKER exec "$mongo" mongosh --quiet --eval '
		// also the package of the original codebase, still on a few very old documents
		const old = ["com.fazeel.'"$OLD_PROJECT"'.", "com.stackroute.'"$OLD_PROJECT"'."], to = "com.fazeel.quizking.";
		let n = 0;
		db.getMongo().getDBNames().filter(d => !["admin", "config", "local"].includes(d)).forEach(d => {
			const database = db.getSiblingDB(d);
			database.getCollectionNames().forEach(c => old.forEach(from => {
				n += database[c].updateMany({ _class: { $regex: "^" + from.replaceAll(".", "\\.") } },
					[{ $set: { _class: { $replaceOne: { input: "$_class", find: from, replacement: to } } } }]).modifiedCount;
			}));
		});
		if (n) print("==> Updated the stored class name on " + n + " Mongo documents");'
}

case "${1:-}" in
before) before ;;
after) after ;;
*) echo "usage: $0 before|after" >&2; exit 2 ;;
esac
