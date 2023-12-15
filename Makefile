run:
	docker compose up --detach bie-solr
	./gradlew bootRun

run-docker:
	./gradlew build
	docker compose build --no-cache
	docker compose up --detach

release:
	../sbdi-install/utils/make-release.sh
