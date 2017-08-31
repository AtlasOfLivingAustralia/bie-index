#!/bin/sh
#
# Hammer the bie-index.
#
# This fires a number of web service calls at the bie-index to see if it breaks
# If you want to do it several times, just spawn multiple instances
#
ws=${1:-http://bie.ala.org.au/ws}
dir=`dir $0`
source=scientific.csv
log=/tmp/hammer$$.log

fail() {
  echo $1 >> $log
  echo $1
  exit 1
}

doget() {
    url="$ws/$1"
    curl -s -f "$url" >> $log || fail "GET $url failed"
}

dopost() {
    url="$ws/$1"
    curl -X POST -H "Content-Type: application/json" -s -f -d "$2" "$url" >> $log || fail "POST $url failed"
}

test_species() {
    species=`awk -F "|" 'NR > 1 {print $1}' $source`
    for s in $species
    do
        doget "species/$s"
    done
}


test_taxon() {
    species=`awk -F "|" 'NR > 1 {print $1}' $source`
    for s in $species
    do
        doget "taxon/$s"
    done
}


test_short() {
    species=`awk -F "|" 'NR > 1 {print $1}' $source`
    for s in $species
    do
        doget "species/shortProfile/$s"
    done
}

test_search() {
    species=`awk -F "|" 'NR > 1 {print $2}' $source | sed -e 's/ /\+/g'`
    for s in $species
    do
        doget "search?q=$s"
    done
}


test_auto() {
    species=`awk -F "|" 'NR > 1 {print $2}' $source | sed -e 's/ /\+/g'`
    for s in $species
    do
        doget "auto?q=$s"
    done
}


test_classification() {
    species=`awk -F "|" 'NR > 1 {print $1}' $source`
    for s in $species
    do
        doget "classification/$s"
    done
}

test_childconcepts() {
    species=`awk -F "|" 'NR > 1 {print $1}' $source`
    for s in $species
    do
        doget "childConcepts/$s"
    done
}

test_imagesearch() {
    species=`awk -F "|" 'NR > 1 {print $1}' $source`
    for s in $species
    do
        doget "imageSearch/$s"
    done
}

test_bulklookup() {
    species=`awk -F "|" 'BEGIN { print "[" } NR == 2 {printf "\"%s\"", $1} NR > 2 { printf ", \"%s\"", $1 } END { print "]" }' $source`
    dopost "species/guids/bulklookup" "$species"
}

test_lookupbulk() {
    species=`awk -F "|" 'BEGIN { print "{ \"vernacular\": true, \"names\": [" } NR == 2 {printf "\"%s\"", $2} NR > 2 { printf ", \"%s\"", $2 } END { print "] }" }' $source`
    dopost "species/lookup/bulk" "$species"
}

test_habitats() {
    doget "habitats"
    doget "habitats/tree"
}


test_ranks() {
    doget "ranks"
}

test_bulklookup
test_lookupbulk
test_habitats
test_ranks
test_species
test_taxon
test_short
test_search
test_auto
test_classification
test_childconcepts
test_imagesearch
