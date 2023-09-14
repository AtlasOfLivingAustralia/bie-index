# Bie-index

## Setup

Create data directory at `/data/bie-index` and populate as below (it is easiest to symlink the config files to the ones in this repo):
```
mats@xps-13:/data/bie-index$ tree
.
└── config
    ├── bie-index-config.yml -> /home/mats/src/biodiversitydata-se/bie-index/sbdi/data/config/bie-index-config.yml
    ├── conservation-lists.json -> /home/mats/src/biodiversitydata-se/bie-index/sbdi/data/config/conservation-lists.json
    ├── image-lists.json -> /home/mats/src/biodiversitydata-se/bie-index/sbdi/data/config/image-lists.json
    ├── locality-keywords.json -> /home/mats/src/biodiversitydata-se/bie-index/sbdi/data/config/locality-keywords.json
    └── vernacular-lists.json -> /home/mats/src/biodiversitydata-se/bie-index/sbdi/data/config/vernacular-lists.json
```

## Usage

Run locally:
```
make run
```

Build and run in Docker (using Tomcat):
```
make run-docker
```

Make a release. This will create a new tag and push it. A new Docker container will be built on Github.
```
mats@xps-13:~/src/biodiversitydata-se/bie-index (master *)$ make release

Current version: 1.0.1. Enter the new version (or press Enter for 1.0.2): 
Updating to version 1.0.2
Tag 1.0.2 created and pushed.
```
