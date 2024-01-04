run:
	docker compose up --detach bie-solr
	./gradlew bootRun

run-docker:
	./gradlew build
	docker compose build --no-cache
	docker compose up --detach

release:
	../sbdi-install/utils/make-release.sh

fetch-backbone:
	mkdir -p /data/bie-index/import
	wget https://hosted-datasets.gbif.org/datasets/backbone/current/backbone.zip -O /data/bie-index/import/backbone.zip
	unzip -q /data/bie-index/import/backbone.zip -d /data/bie-index/import/backbone/

process-backbone:
	./sbdi/process-backbone.py /data/bie-index/import/backbone
