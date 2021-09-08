package au.org.ala.bie.indexing

import groovy.json.JsonSlurper
import spock.lang.Specification
import static spock.util.matcher.HamcrestMatchers.closeTo


/**
 *
 *
 * @author Doug Palmer &lt;Doug.Palmer@csiro.au&gt;
 * @copyright Copyright &copy; 2019 Atlas of Living Australia
 */
class WeightBuilderSpec extends Specification {
    WeightBuilder builder

    def setup() {
        def slurper = new JsonSlurper()
        def model = slurper.parse(this.class.getResource('/default-weights.json'))
        builder = new WeightBuilder(model)
    }

    def 'test empty 1'() {
        when:
        def doc = [:]
        def weights = builder.apply(1.0, doc)
        then:
        1.0 closeTo(weights.searchWeight , 0.01)
        1.0 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test empty 2'() {
        when:
        def doc = [:]
        def weights = builder.apply(2.0, doc)
        then:
        2.0 closeTo(weights.searchWeight, 0.01)
        2.0 closeTo(weights.suggestWeight, 0.01)
    }


    def 'test value 1'() {
        when:
        def doc = [idxtype: 'TAXON']
        def weights = builder.apply(1.0, doc)
        then:
        2.0 closeTo(weights.searchWeight, 0.01)
        2.0 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test value 2'() {
        when:
        def doc = [idxtype: 'COMMON']
        def weights = builder.apply(1.0, doc)
        then:
        1.5 closeTo(weights.searchWeight, 0.01)
        1.5 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test value 3'() {
        when:
        def doc = [taxonomicStatus: 'accepted']
        def weights = builder.apply(1.0, doc)
        then:
        2.0 closeTo(weights.searchWeight, 0.01)
        2.0 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test value 4'() {
        when:
        def doc = [taxonomicStatus: 'excluded']
        def weights = builder.apply(1.0, doc)
        then:
        0.3 closeTo(weights.searchWeight, 0.01)
        0.3 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test value 5'() {
        when:
        def doc = [rankID: 7000]
        def weights = builder.apply(1.0, doc)
        then:
        1.8 closeTo(weights.searchWeight, 0.01)
        1.8 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test value 6'() {
        when:
        def doc = [rankID: 'hello']
        def weights = builder.apply(1.0, doc)
        then:
        1.0 closeTo(weights.searchWeight, 0.01)
        1.0 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test valueCondition 1'() {
        when:
        def doc = [rankID: 8000]
        def weights = builder.apply(1.0, doc)
        then:
        1.0 closeTo(weights.searchWeight, 0.01)
        1.0 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test valueFromTo 2'() {
        when:
        def doc = [rankID: 8100]
        def weights = builder.apply(1.0, doc)
        then:
        0.5 closeTo(weights.searchWeight, 0.01)
        0.5 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test hybrid 1'() {
        when:
        def doc = [scientificName: 'Acacia dealbata']
        def weights = builder.apply(1.0, doc)
        then:
        1.0 closeTo(weights.searchWeight, 0.01)
        0.8773762 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test hybrid 2'() {
        when:
        def doc = [nameType: 'hybrid', scientificName: 'Acacia x sussia']
        def weights = builder.apply(1.0, doc)
        then:
        0.2 closeTo(weights.searchWeight, 0.01)
        0.17547524 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test lengthDecay 1'() {
        when:
        def doc = [scientificName: '1234']
        def weights = builder.apply(1.0, doc)
        then:
        1.0 closeTo(weights.searchWeight, 0.01)
        1.0 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test lengthDecay 2'() {
        when:
        def doc = [scientificName: '12345']
        def weights = builder.apply(1.0, doc)
        then:
        1.0 closeTo(weights.searchWeight, 0.01)
        0.9534796 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test lengthDecay 3'() {
        when:
        def doc = [scientificName: '1234567890']
        def weights = builder.apply(1.0, doc)
        then:
        1.0 closeTo(weights.searchWeight, 0.01)
        0.91 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test lengthDecay 4'() {
        when:
        def doc = [scientificName: '12345678901234567890']
        def weights = builder.apply(1.0, doc)
        then:
        1.0 closeTo(weights.searchWeight, 0.01)
        0.85 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test lengthDecay 5'() {
        when:
        def doc = [scientificName: '123456789012345678901234567890']
        def weights = builder.apply(1.0, doc)
        then:
        1.0 closeTo(weights.searchWeight, 0.01)
        0.79 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test lengthDecay 6'() {
        when:
        def doc = [scientificName: '1234567890123456789012345678901234567890']
        def weights = builder.apply(1.0, doc)
        then:
        1.0 closeTo(weights.searchWeight, 0.01)
        0.75 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test common names 1'() {
        setup:
        def slurper = new JsonSlurper()
        def model = slurper.parse(this.class.getResource('common-weights.json'))
        builder = new WeightBuilder(model)
        when:
        def doc = [scientificName: 'Osphranter rufus', commonName: [ 'Red Kangaroo']]
        def weights = builder.apply(1.0, doc)
        then:
        100.0 closeTo(weights.searchWeight, 0.01)
        100.0 closeTo(weights.suggestWeight, 0.01)
    }

    def 'test common names 2'() {
        setup:
        def slurper = new JsonSlurper()
        def model = slurper.parse(this.class.getResource('common-weights.json'))
        builder = new WeightBuilder(model)
        when:
        def doc = [scientificName: 'Tachyglossus aculeatus', commonName: [ 'Short-Beaked Echidna', 'Echidna']]
        def weights = builder.apply(1.0, doc)
        then:
        100.0 closeTo(weights.searchWeight, 0.01)
        100.0 closeTo(weights.suggestWeight, 0.01)
    }

}
