package au.org.ala.bie.util

import spock.lang.Specification

/**
 * File description.
 * <p>
 * More description.
 * </p>
 *
 * @author Doug Palmer &lt;Doug.Palmer@csiro.au&gt;
 * @copyright Copyright &copy; 2017 Atlas of Living Australia
 */
class TitleCapitaliserSpec extends Specification {
    TitleCapitaliser capitaliser
    def setup() {
        capitaliser = TitleCapitaliser.create("en")
    }

    def cleanup() {
    }

    def 'test create 1'() {
        when:
        def c1 = TitleCapitaliser.create('en')
        def c2 = TitleCapitaliser.create('de')
        def c3 = TitleCapitaliser.create('en')
        def c4 = TitleCapitaliser.create('de')
        then:
        c1 != c2
        c1 == c3
        c1 != c4
        c2 != c3
        c2 == c4
        c3 != c4
    }

    def "test simple capitalisation 1"() {
        expect:
        capitaliser.capitalise('the consolations of philosophy') == 'The Consolations of Philosophy'
        capitaliser.capitalise('the mill on the floss') == 'The Mill on the Floss'
        capitaliser.capitalise('Cugel\'s Saga') == 'Cugel\'s Saga'
        capitaliser.capitalise('nac mac feegle') == 'Nac Mac Feegle'
    }

    def "test connective 1"() {
        expect:
        capitaliser.capitalise('hearth and heart') == 'Hearth and Heart'
        capitaliser.capitalise('softly but slowly') == 'Softly but Slowly'
        capitaliser.capitalise('but where do i lie?') == 'But Where Do I Lie?'
        capitaliser.capitalise('the last but') == 'The Last But'
    }

    def "test article 1"() {
        expect:
        capitaliser.capitalise('nift the lean') == 'Nift the Lean'
        capitaliser.capitalise('a mathematician reads the newspapers') == 'A Mathematician Reads the Newspapers'
        capitaliser.capitalise('shooting an elephant') == 'Shooting an Elephant'
        capitaliser.capitalise('Pick a peck of Pickled Pepper') == 'Pick a Peck of Pickled Pepper'
    }

    def "test preposition 1"() {
        expect:
        capitaliser.capitalise('Ten minutes to midnight') == 'Ten Minutes to Midnight'
        capitaliser.capitalise('the boys from the blackstuff') == 'The Boys from the Blackstuff'
        capitaliser.capitalise('abide by me') == 'Abide by Me'
        capitaliser.capitalise('the uses of enchantment') == 'The Uses of Enchantment'
    }

    def "test abbreviation letters 1"() {
        expect:
        capitaliser.capitalise('Writings of a.j.p. taylor') == 'Writings of A.J.P. Taylor'
        capitaliser.capitalise('a guide to e. nesbit') == 'A Guide to E. Nesbit'
        capitaliser.capitalise('c.s. lewis') == 'C.S. Lewis'
        capitaliser.capitalise('c. s. lewis') == 'C. S. Lewis'
    }

    def "test initial apostrophies 1"() {
        expect:
        capitaliser.capitalise('Tess of the d\'urbervilles') == 'Tess of the d\'Urbervilles'
        capitaliser.capitalise('sean o\'brien') == 'Sean O\'Brien'
        capitaliser.capitalise('a strange case of d\'Eath in the night') == 'A Strange Case of d\'Eath in the Night'
    }

    def "test capitalisation 1"() {
        expect:
        capitaliser.capitalise('something') == 'Something'
        capitaliser.capitalise('AParcelOfRogues') == 'Aparcelofrogues'
        capitaliser.capitalise('f.f.') == 'F.F.'
        capitaliser.capitalise('Burleigh&Stronginthearm') == 'Burleigh&Stronginthearm'
        capitaliser.capitalise('indo-pacific') == 'Indo-Pacific'
    }

}
