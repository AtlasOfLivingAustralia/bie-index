package au.org.ala.bie.indexing

import javax.script.*
import java.util.regex.Pattern

/**
 * Builds a weight for a specific document, based on a semi-interpreted language.
 * <p>
 * The input model is, essentially a multi-layer dictionary.
 * <p>
 * The first map is for types of weights and the rules that apply to them.
 * The second map is for fields and the lists of conditions that apply to them.
 * The list of conditions contain a list of matches conditions and adjustments to the base
 * score for an element.
 *
 * @author Doug Palmer &lt;Doug.Palmer@csiro.au&gt;
 * @copyright Copyright &copy; 2019 Atlas of Living Australia
 */
class WeightBuilder {
    Compilable compiler
    Weight global
    List<Weight> weights

    /**
     * Build for a weight document
     *
     * @param model The weights and rules to compute
     */
    WeightBuilder(model) {
        def manager = new ScriptEngineManager()
        this.compiler = (Compilable) manager.getEngineByName(model.script ?: 'nashorn')
        this.global = new Weight(model.global)
        this.weights = model.weights.collect { new Weight(it) }
    }

    Map<String, Double> apply(double base, Map document) {
        Bindings bindings = new SimpleBindings(document)
        def results = [:]
        this.weights.each {
            def w = global.applyRules(base, bindings)
            it.apply(w, bindings, results)
        }
        return results
    }

    class Weight {
        String field
        List<Rule> rules

        Weight(weight) {
            this.field = weight.field
            this.rules = weight.rules.collect { new Rule(it, null) }
        }

        def apply(double weight, Bindings bindings, Map results) {
            weight = applyRules(weight, bindings)
            results[field] = weight
        }

        double applyRules(double weight, Bindings bindings) {
            return rules.inject(weight, { w, r -> r.apply(w, bindings) })
        }
    }

    class Rule {
        String term
        Boolean exists
        Object value
        Pattern match
        CompiledScript condition
        double weight
        CompiledScript weightExpession
        List<Rule> rules

        Rule(rule, parent) {
            this.term = rule.term ?: parent?.term
            this.exists = rule.exists
            this.value = rule.value
            this.match = rule.match ? Pattern.compile(rule.match) : null
            this.condition = rule.condition ? WeightBuilder.this.compiler.compile(rule.condition) : null
            this.weight = rule.weight ?: 1.0f
            this.weightExpession = rule.weightExpression ? WeightBuilder.this.compiler.compile(rule.weightExpression) : null
            this.rules = rule.rules ? rule.rules.collect { new Rule(it, this) } : null
        }

        double apply(double weight, Bindings bindings) {
            Object val = null

            if (this.term)
                val = bindings.get(this.term)
            if (!test(val, bindings))
                return weight
            weight = weight * this.weight
            if (this.weightExpession) {
                if (this.term)
                    bindings.put("_value", val)
                bindings.put("_weight", weight)
                def result = this.weightExpession.eval(bindings)
                bindings.remove('_value')
                bindings.remove('_weight')
                weight = ((Number) result).doubleValue()
            }
            if (this.rules) {
                weight = this.rules.inject(weight, { w, r -> r.apply(w, bindings) })
            }
            return weight
        }

        boolean test(Object val, Bindings bindings) {
            if (val != null && val in Collection) {
                if (this.exists != null && (this.exists && val.isEmpty()  || !this.exists && !val.isEmpty()))
                    return false
                return val.any { test(it, bindings) }
            }
            if (this.exists != null && (this.exists && val == null  || !this.exists && val != null))
                return false
            if (this.value && this.value != val)
                return false
            if (this.match && (!(val in String) || !this.match.matcher(val).matches()))
                return false
            if (this.condition) {
                if (this.term)
                    bindings.put("_value", val)
                def result = this.condition.eval(bindings)
                bindings.remove('_value')
                if (!(result in Boolean) || !result)
                    return false
            }
            return true
        }
    }
}
