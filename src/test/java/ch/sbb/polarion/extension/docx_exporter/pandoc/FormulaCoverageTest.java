package ch.sbb.polarion.extension.docx_exporter.pandoc;

import ch.sbb.polarion.extension.docx_exporter.pandoc.service.model.PandocParams;
import ch.sbb.polarion.extension.docx_exporter.util.LatexUtils;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.stream.Stream;

import static ch.sbb.polarion.extension.docx_exporter.pandoc.FormulaCoverageTest.Outcome.LEAK;
import static ch.sbb.polarion.extension.docx_exporter.pandoc.FormulaCoverageTest.Outcome.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Differential characterization test for LaTeX-formula conversion.
 *
 * <h2>What this pins</h2>
 * Polarion authors formulas with <b>MathJax 2.7.9</b> (TeX input), which accepts a far wider plain-TeX/LaTeX surface than
 * Pandoc's <b>texmath</b> reader (the converter that turns the LaTeX into native Word equations / OMML). When texmath
 * cannot parse an expression it silently gives up and Pandoc emits the raw LaTeX as literal {@code $$...$$} text, so the
 * formula shows up as source code instead of a rendered equation. {@link LatexUtils#sanitizeFormulaSource(String)} is a
 * targeted shim that rewrites several MathJax-isms (matrix/alignment macros, {@code \cr}, {@code \over}, {@code \cfrac},
 * {@code \root..\of}, {@code \require}) into texmath-compatible forms.
 *
 * <h2>How it works</h2>
 * Every corpus formula - drawn from the <a href="https://docs.mathjax.org/en/v2.7/tex.html">MathJax 2.7 TeX surface</a>
 * and the <a href="https://www.onemathematicalcat.org/MathJaxDocumentation/TeXSyntax.htm">"TeX Commands available in
 * MathJax"</a> list - declares two explicit expectations: how the <b>raw</b> formula converts, and how it converts
 * <b>after</b> sanitization. Each is sent to the real pandoc-service and the actual outcome (OK = produced an
 * {@code <m:oMath>}; LEAK = no OMML, raw source leaked) is asserted against the declared expectation, so any drift -
 * whether a regression in our shim or a behavior change in a bumped pandoc/texmath - fails the test with the exact
 * formula that diverged. The raw expectations document texmath's native behavior (and therefore why each shim exists);
 * the sanitized expectations are the contract we actually ship.
 *
 * <h2>Running</h2>
 * Like every {@link BasePandocTest}, this is skipped unless a reachable pandoc-service is configured:
 * {@code mvn verify -P tests-with-pandoc-docker -Dpandoc.service.url=<url>}.
 */
@SkipTestWhenParamNotSet
@SuppressWarnings("SpellCheckingInspection") // remove visual distraction because here we have a lot of false-positives ('infty', 'cdot' etc.)
class FormulaCoverageTest extends BasePandocTest {

    /** Outcome of converting one formula through the pandoc-service. */
    enum Outcome {
        OK,    // produced an <m:oMath> element - Word will render a real equation
        LEAK   // no OMML; texmath could not parse it and the raw source leaked as text
    }

    private record FormulaCase(@NotNull String id, @NotNull String category, @NotNull String latex,
                               @NotNull Outcome expectedRaw, @NotNull Outcome expectedSanitized) {
        @Override
        public @NonNull String toString() {
            return id + " (" + category + ")";
        }
    }

    // The corpus, grouped by the language construct it exercises. Each row pins both the raw texmath behavior and the
    // behavior after LatexUtils.sanitizeFormulaSource. LEAK->OK rows are exactly what the shim buys us; OK->OK rows are
    // constructs texmath already handles; LEAK->LEAK rows are the residual, not-yet-shimmed gap (kept here so a future
    // texmath bump that starts handling them is noticed immediately).
    private static final List<FormulaCase> CORPUS = List.of(
            // --- Baseline: core texmath features that must always work (also proves the harness + container are healthy) ---
            new FormulaCase("frac", "baseline", "\\frac{a}{b+c}", OK, OK),
            new FormulaCase("sup-sub", "baseline", "x_{i}^{2} + y^{n+1}", OK, OK),
            new FormulaCase("sqrt", "baseline", "\\sqrt{x^2 + y^2}", OK, OK),
            new FormulaCase("sqrt-index", "baseline", "\\sqrt[3]{x}", OK, OK),
            new FormulaCase("greek", "baseline", "\\alpha + \\beta = \\gamma", OK, OK),
            new FormulaCase("sum-limits", "baseline", "\\sum_{i=1}^{n} i = \\frac{n(n+1)}{2}", OK, OK),
            new FormulaCase("integral", "baseline", "\\int_{0}^{\\infty} e^{-x}\\,dx = 1", OK, OK),
            new FormulaCase("left-right", "baseline", "\\left(\\frac{a}{b}\\right)^{2}", OK, OK),
            new FormulaCase("binom", "baseline", "\\binom{n}{k}", OK, OK),
            new FormulaCase("accents", "baseline", "\\vec{v} \\cdot \\hat{n} = \\overline{z}", OK, OK),
            new FormulaCase("text", "baseline", "x \\text{ if } y", OK, OK),

            // --- Environment-form matrices: texmath's required form; pass raw and after sanitize ---
            new FormulaCase("env-pmatrix", "matrix-env", "\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}", OK, OK),
            new FormulaCase("env-cases", "matrix-env", "f(x) = \\begin{cases} 1 & x>0 \\\\ 0 & x\\le 0 \\end{cases}", OK, OK),

            // --- Plain-TeX matrix/alignment MACROS: LatexUtils rewrites these to amsmath environments. LEAK -> OK ---
            new FormulaCase("pmatrix-cr", "matrix-macro", "\\pmatrix{ a & b \\cr c & d }", LEAK, OK),
            new FormulaCase("matrix-cr", "matrix-macro", "\\matrix{ a & b \\cr c & d }", LEAK, OK),
            new FormulaCase("bmatrix-cr", "matrix-macro", "\\bmatrix{ a & b \\cr c & d }", LEAK, OK),
            new FormulaCase("vmatrix-cr", "matrix-macro", "\\vmatrix{ a & b \\cr c & d }", LEAK, OK),
            new FormulaCase("cases-macro", "matrix-macro", "\\cases{ 1 & x>0 \\cr 0 & x\\le 0 }", LEAK, OK),
            new FormulaCase("eqalign", "matrix-macro", "\\eqalign{ a &= b \\cr c &= d }", LEAK, OK),
            new FormulaCase("displaylines", "matrix-macro", "\\displaylines{ a = b \\cr c = d }", LEAK, OK),
            new FormulaCase("nested-pmatrix", "matrix-macro", "\\pmatrix{ \\pmatrix{ a \\cr b } & c \\cr d & e }", LEAK, OK),
            // The exact real-world Polarion formula that motivated the original fix.
            new FormulaCase("polarion-matrix", "matrix-macro",
                    "A = \\pmatrix{a_{11} & a_{12} & \\ldots & a_{1n} \\cr a_{21} & a_{22} & \\ldots & a_{2n} \\cr \\vdots & \\vdots & \\ddots & \\vdots \\cr a_{m1} & a_{m2} & \\ldots & a_{mn} \\cr}",
                    LEAK, OK),

            // --- Infix fraction-like operators: \over, \atop and the delimited *withdelims variants are shimmed (LEAK -> OK); \choose is native ---
            new FormulaCase("over", "infix-operator", "{a \\over b}", LEAK, OK),
            new FormulaCase("atop", "infix-operator", "{n \\atop k}", LEAK, OK),
            // The canonical real-world \atop: a two-line condition under a sum (\substack after the shim).
            new FormulaCase("atop-sum-subscript", "infix-operator", "\\sum_{0 \\le i \\le m \\atop 0 < j < n} P(i,j)", LEAK, OK),
            new FormulaCase("choose", "infix-operator", "{n \\choose k}", OK, OK),
            // \overwithdelims / \atopwithdelims: ruled / ruleless fraction wrapped in delimiters -> \left D1 ... \right D2.
            new FormulaCase("overwithdelims", "infix-operator", "{a \\overwithdelims [ ] b}", LEAK, OK),
            new FormulaCase("atopwithdelims", "infix-operator", "{n \\atopwithdelims ( ) k}", LEAK, OK),

            // --- Roots / stacking legacy macros: \root..\of and \buildrel are both shimmed (LEAK -> OK) ---
            new FormulaCase("root-of", "legacy-macro", "\\root 3 \\of x", LEAK, OK),
            new FormulaCase("root-of-grouped", "legacy-macro", "\\root n+1 \\of {x+y}", LEAK, OK),
            // \buildrel A \over B -> \overset{A}{B}; the \rm in the canonical "defined as" symbol is handled by the font-switch shim.
            new FormulaCase("buildrel", "legacy-macro", "\\buildrel \\rm def \\over =", LEAK, OK),

            // --- Old declaration-style font switches: texmath leaks the bare switches; the shim wraps them as \mathXX{...} ---
            new FormulaCase("font-rm", "font-switch", "{\\rm def}", LEAK, OK),
            new FormulaCase("font-bf", "font-switch", "\\bf x", LEAK, OK),
            new FormulaCase("font-nested", "font-switch", "{\\rm a {\\bf b} c}", LEAK, OK),
            // The less-common alphabets map to \mathXX that historically need extra LaTeX packages (\mathscr -> mathrsfs,
            // \mathfrak/\mathbb -> amssymb); pin that texmath accepts them so a future pandoc/texmath bump can't silently leak them.
            new FormulaCase("font-cal", "font-switch", "{\\cal L}", LEAK, OK),
            new FormulaCase("font-frak", "font-switch", "{\\frak g}", LEAK, OK),
            new FormulaCase("font-bbb", "font-switch", "{\\Bbb R}", LEAK, OK),
            new FormulaCase("font-scr", "font-switch", "{\\scr F}", LEAK, OK),

            // --- Color: the pandoc-service math-color shim now renders real OMML color; \textcolor no longer leaks.
            //     (This asserts only that an equation is produced; that the color is actually applied is verified in
            //     pandoc-service's test_math_color_integration.py. These rows require a pandoc-service with that shim.) ---
            new FormulaCase("color", "color", "\\color{red}{x^2}", OK, OK),
            new FormulaCase("textcolor", "color", "\\textcolor{red}{x^2}", OK, OK),
            new FormulaCase("color-quadratic", "color", "x=\\frac{-b\\pm\\sqrt{\\color{Red}{b^2-4ac}}}{2a}", OK, OK),

            // --- Decorations / arrows: native, except \cfrac which is shimmed to \frac (LEAK -> OK) ---
            new FormulaCase("underbrace", "decoration", "\\underbrace{a+b+c}_{n}", OK, OK),
            new FormulaCase("xrightarrow", "decoration", "A \\xrightarrow{f} B", OK, OK),
            new FormulaCase("overset", "decoration", "\\overset{!}{=}", OK, OK),
            new FormulaCase("cfrac", "decoration", "\\cfrac{1}{1+\\cfrac{1}{x}}", LEAK, OK),

            // --- MathJax extensions: \require is stripped so \cancel (native) survives; \ce and \def are not handled ---
            new FormulaCase("mhchem", "extension", "\\ce{H2O}", LEAK, LEAK),
            new FormulaCase("cancel", "extension", "\\cancel{x}", OK, OK),
            new FormulaCase("require-cancel", "extension", "\\require{cancel}\\cancel{x}", LEAK, OK),
            new FormulaCase("def-macro", "extension", "\\def\\half{\\frac{1}{2}}\\half", LEAK, LEAK),

            // --- Special characters that must survive raw to Pandoc ---
            new FormulaCase("comparison", "special-chars", "a < b > c", OK, OK),
            new FormulaCase("ampersand-align", "special-chars", "\\begin{aligned} a &= b \\\\ c &= d \\end{aligned}", OK, OK)
    );

    private static Stream<FormulaCase> corpus() {
        return CORPUS.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void formulaConvertsAsExpected(FormulaCase formulaCase) throws Exception {
        assertEquals(formulaCase.expectedRaw(), convert(formulaCase.latex()),
                () -> "Raw conversion of '" + formulaCase.id() + "' did not match expectation. LaTeX: " + formulaCase.latex());

        String sanitized = LatexUtils.sanitizeFormulaSource(formulaCase.latex());
        assertEquals(formulaCase.expectedSanitized(), convert(sanitized),
                () -> "Sanitized conversion of '" + formulaCase.id() + "' did not match expectation. Sanitized LaTeX: " + sanitized);
    }

    /**
     * Sends a single formula to the pandoc-service wrapped as a display math script and reports whether the resulting
     * DOCX contains a real Word equation. {@code <m:oMath>} is the reliable success signal: when texmath cannot parse the
     * LaTeX, Pandoc emits no OMML at all and leaks the source as a literal text run instead.
     */
    private Outcome convert(@NotNull String latex) throws Exception {
        String html = "<html><body><p><script type=\"math/tex; mode=display\">" + latex + "</script></p></body></html>";
        byte[] docxBytes = exportToDOCX(html, readTemplate("reference_template"), PandocParams.builder().build());
        WordprocessingMLPackage pkg = WordprocessingMLPackage.load(new ByteArrayInputStream(docxBytes));
        String documentXml = XmlUtils.marshaltoString(pkg.getMainDocumentPart().getJaxbElement(), true, true);
        return documentXml.contains("<m:oMath") ? OK : LEAK;
    }
}
