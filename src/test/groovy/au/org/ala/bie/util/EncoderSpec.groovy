package au.org.ala.bie.util

import spock.lang.Specification

class EncoderSpec extends Specification {
    def 'test build language list 1'() {
        expect:
        Encoder.buildLanguageList([Locale.ENGLISH]) == ["en", "eng"]
    }

    def 'test build language list 2'() {
        expect:
        Encoder.buildLanguageList([Locale.UK]) == ["en-GB", "en", "eng"]
    }

    def 'test build language list 3'() {
        expect:
        Encoder.buildLanguageList([Locale.UK, Locale.US]) == ["en-GB", "en", "eng", "en-US"]
    }

    def 'test build language list 4'() {
        expect:
        Encoder.buildLanguageList([Locale.UK, Locale.FRANCE]) == ["en-GB", "en", "eng", "fr-FR", "fr", "fra"]
    }


}
