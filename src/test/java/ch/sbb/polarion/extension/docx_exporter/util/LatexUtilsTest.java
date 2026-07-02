package ch.sbb.polarion.extension.docx_exporter.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure string-level tests for the LaTeX normalization in {@link LatexUtils}. These run in a plain build (no
 * pandoc-service) and pin the balanced-brace scanning logic of the {@code \over}, {@code \atop}, {@code \root} and
 * matrix transforms. The end-to-end "does texmath actually accept the result" check lives in
 * {@code ch.sbb.polarion.extension.docx_exporter.pandoc.FormulaCoverageTest}.
 */
@SuppressWarnings("SpellCheckingInspection") // remove visual distraction because here we have a lot of false-positives ('infty', 'cdot' etc.)
class LatexUtilsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // \require{...} is a MathJax-only directive and must be stripped, leaving the rest intact.
            "\\require{cancel}\\cancel{x} | \\cancel{x}",
            "\\require{color}x^2 | x^2",
            // \cfrac -> \frac, including nested.
            "\\cfrac{1}{1+\\cfrac{1}{x}} | \\frac{1}{1+\\frac{1}{x}}",
    })
    void stripsAndRewritesSimpleMacros(String input, String expected) {
        assertEquals(expected, LatexUtils.sanitizeFormulaSource(input));
    }

    @Test
    void rewritesInfixOverInBraceGroup() {
        assertEquals("{\\frac{a }{ b}}", LatexUtils.sanitizeFormulaSource("{a \\over b}"));
    }

    @Test
    void rewritesInfixOverAtTopLevel() {
        assertEquals("\\frac{a }{ b}", LatexUtils.sanitizeFormulaSource("a \\over b"));
    }

    @Test
    void infixOverDoesNotMatchLongerCommands() {
        // \overline, \overset and \overbrace share the "\over" prefix but must be left untouched (the delimited
        // \overwithdelims is its own transform, covered separately).
        assertEquals("\\overline{x}", LatexUtils.sanitizeFormulaSource("\\overline{x}"));
        assertEquals("\\overset{!}{=}", LatexUtils.sanitizeFormulaSource("\\overset{!}{=}"));
        assertEquals("\\overbrace{a+b}", LatexUtils.sanitizeFormulaSource("\\overbrace{a+b}"));
    }

    @Test
    void infixOverInsideMatrixCellDoesNotSwallowNeighbouringCells() {
        // \over scopes to its alignment cell: the first cell becomes a fraction, the second cell ("c") is untouched,
        // and the \pmatrix macro is rewritten to the amsmath environment with \cr turned into \\.
        assertEquals(
                "\\begin{pmatrix}\\frac{a }{ b }& c\\end{pmatrix}",
                LatexUtils.sanitizeFormulaSource("\\pmatrix{a \\over b & c}"));
    }

    @Test
    void strayClosingBraceDoesNotDerailInfixConversion() {
        // A stray unmatched '}' must clamp the brace depth at 0 (never negative), so a following top-level
        // \over is still recognized at depth 0 and converted, instead of leaking as raw text.
        assertEquals("\\frac{a} }{ b}", LatexUtils.sanitizeFormulaSource("a} \\over b"));
    }

    @Test
    void rewritesInfixAtopInBraceGroup() {
        // \atop is the ruleless sibling of \over; texmath has no ruleless fraction, so it maps to \substack.
        assertEquals("{\\substack{n \\\\ k}}", LatexUtils.sanitizeFormulaSource("{n \\atop k}"));
    }

    @Test
    void rewritesInfixAtopAtTopLevel() {
        assertEquals("\\substack{a \\\\ b}", LatexUtils.sanitizeFormulaSource("a \\atop b"));
    }

    @Test
    void rewritesAtopInSubscriptStack() {
        // The canonical real-world \atop: a two-line condition under a sum. After the shim the subscript holds a
        // \substack, which texmath renders as a stacked subscript.
        assertEquals(
                "\\sum_{\\substack{0 \\le i \\le m \\\\ 0 < j < n}} P(i,j)",
                LatexUtils.sanitizeFormulaSource("\\sum_{0 \\le i \\le m \\atop 0 < j < n} P(i,j)"));
    }

    @Test
    void infixAtopInsideMatrixCellDoesNotSwallowNeighbouringCells() {
        // \atop scopes to its alignment cell, exactly like \over: the first cell becomes a \substack, "c" is untouched.
        assertEquals(
                "\\begin{pmatrix}\\substack{a \\\\ b }& c\\end{pmatrix}",
                LatexUtils.sanitizeFormulaSource("\\pmatrix{a \\atop b & c}"));
    }

    @Test
    void stripsTrailingCrFromAlignmentMacro() {
        // The idiomatic trailing \cr would become an empty final row in the aligned env (a stray dotted
        // box in Word); it must be dropped, while the interior \cr becomes the \\ row separator.
        assertEquals(
                "\\begin{aligned} a &= b \\\\ c &= d\\end{aligned}",
                LatexUtils.sanitizeFormulaSource("\\eqalign{ a &= b \\cr c &= d \\cr }"));
    }

    @Test
    void stripsTrailingCrFromMatrixMacro() {
        assertEquals(
                "\\begin{pmatrix} a & b \\\\ c & d\\end{pmatrix}",
                LatexUtils.sanitizeFormulaSource("\\pmatrix{ a & b \\cr c & d \\cr }"));
    }

    @Test
    void stripsTrailingDoubleBackslashFromMatrixMacro() {
        assertEquals(
                "\\begin{matrix} a \\\\ b\\end{matrix}",
                LatexUtils.sanitizeFormulaSource("\\matrix{ a \\\\ b \\\\ }"));
    }

    @Test
    void keepsRowsWhenNoTrailingSeparator() {
        // No trailing separator: nothing is stripped, the interior \cr still becomes \\.
        assertEquals(
                "\\begin{pmatrix} a \\\\ b \\end{pmatrix}",
                LatexUtils.sanitizeFormulaSource("\\pmatrix{ a \\cr b }"));
    }

    @Test
    void convertsCrFollowedByDigitOrUnderscore() {
        // A TeX control word ends at the first non-letter, so \cr0 / \cr_ are \cr followed by 0/_ and must
        // convert. (A "\cr\b" regex would miss these: RE2J's \b treats digits and _ as word characters.)
        assertEquals("\\begin{matrix} a \\\\0 \\end{matrix}", LatexUtils.sanitizeFormulaSource("\\matrix{ a \\cr0 }"));
        assertEquals("\\begin{matrix} a \\\\_1 \\end{matrix}", LatexUtils.sanitizeFormulaSource("\\matrix{ a \\cr_1 }"));
    }

    @Test
    void stripsTrailingCrEvenWhenPrecededByRowBreak() {
        // "\\\cr" is a "\\" row break plus a trailing "\cr": the "\cr" is a genuine control word (odd
        // backslash run) and must still be stripped, not mistaken for the tail of an escaped "\\".
        assertEquals(
                "\\begin{matrix}a \\\\\\end{matrix}",
                LatexUtils.sanitizeFormulaSource("\\matrix{a \\\\\\cr}"));
    }

    @Test
    void rewritesOverwithdelims() {
        // \overwithdelims is a ruled fraction wrapped in two delimiter tokens -> \left D1 \frac{A}{B} \right D2.
        assertEquals("{\\left[\\frac{a }{ b}\\right]}", LatexUtils.sanitizeFormulaSource("{a \\overwithdelims [ ] b}"));
    }

    @Test
    void rewritesAtopwithdelims() {
        // \atopwithdelims is the ruleless variant -> \substack inside the delimiters. {n \atopwithdelims () k} is the
        // classic binomial coefficient.
        assertEquals("{\\left(\\substack{n \\\\ k}\\right)}", LatexUtils.sanitizeFormulaSource("{n \\atopwithdelims () k}"));
    }

    @Test
    void rewritesWithdelimsControlWordAndNullDelimiters() {
        // Delimiters may be control sequences (\langle, \rangle) or the null delimiter ".", all valid for \left/\right.
        assertEquals("{\\left\\langle\\frac{a }{ b}\\right\\rangle}",
                LatexUtils.sanitizeFormulaSource("{a \\overwithdelims \\langle \\rangle b}"));
        assertEquals("{\\left.\\frac{a }{ b}\\right.}",
                LatexUtils.sanitizeFormulaSource("{a \\overwithdelims . . b}"));
    }

    @Test
    void leavesWithdelimsLongerCommandUntouched() {
        // Neither \over nor \atop must swallow the delimited variants; \overwithdelims/\atopwithdelims are matched whole.
        assertEquals("\\overwithdelimsX", LatexUtils.sanitizeFormulaSource("\\overwithdelimsX"));
    }

    @Test
    void rewritesBuildrel() {
        // \buildrel A \over B -> \overset{A}{B}; the \rm in the top is then turned into \mathrm by the font-switch pass.
        assertEquals("\\overset{\\mathrm{def}}{=}", LatexUtils.sanitizeFormulaSource("\\buildrel \\rm def \\over ="));
    }

    @Test
    void rewritesBuildrelWithGroupedBottom() {
        assertEquals("\\overset{x}{a+b}", LatexUtils.sanitizeFormulaSource("\\buildrel x \\over {a+b}"));
    }

    @Test
    void rewritesOldFontSwitchInGroup() {
        // \rm declares a font for the rest of its group; texmath needs the argument form \mathrm{...}.
        assertEquals("{\\mathrm{abc}}", LatexUtils.sanitizeFormulaSource("{\\rm abc}"));
    }

    @Test
    void rewritesBareFontSwitch() {
        assertEquals("\\mathbf{x}", LatexUtils.sanitizeFormulaSource("\\bf x"));
    }

    @Test
    void nestedFontSwitchOverridesOuterWithinItsGroup() {
        // The inner \bf overrides \rm only within its own group; "a" and "c" stay roman, "b" becomes bold.
        assertEquals("{\\mathrm{a {\\mathbf{b}} c}}", LatexUtils.sanitizeFormulaSource("{\\rm a {\\bf b} c}"));
    }

    @Test
    void fontSwitchIsScopedToItsMatrixCell() {
        // \rm applies only to its own cell "a", not the neighboring cells; the matrix macro and \cr convert as usual.
        assertEquals(
                "\\begin{matrix}\\mathrm{a}& b \\\\ c & d\\end{matrix}",
                LatexUtils.sanitizeFormulaSource("\\matrix{\\rm a & b \\cr c & d}"));
    }

    @Test
    void rewritesRootWithSingleTokenRadicand() {
        assertEquals("\\sqrt[3]{x}", LatexUtils.sanitizeFormulaSource("\\root 3 \\of x"));
    }

    @Test
    void rewritesRootWithGroupedIndexAndRadicand() {
        assertEquals("\\sqrt[n+1]{x+y}", LatexUtils.sanitizeFormulaSource("\\root n+1 \\of {x+y}"));
    }

    @Test
    void rewritesRootWithControlWordRadicand() {
        // Radicand is a control sequence (no braces): read as a single token.
        assertEquals("\\sqrt[3]{\\alpha}", LatexUtils.sanitizeFormulaSource("\\root 3 \\of \\alpha"));
    }

    @Test
    void rootWithoutOfIsLeftUntouched() {
        // No \of keyword -> not a \root..\of construct, left as-is.
        assertEquals("\\root 3 x", LatexUtils.sanitizeFormulaSource("\\root 3 x"));
    }

    @Test
    void rootWithUnbalancedRadicandBraceIsLeftUntouched() {
        // The radicand "{x+y" never closes -> the transform bails and leaves the source untouched.
        assertEquals("\\root 3 \\of {x+y", LatexUtils.sanitizeFormulaSource("\\root 3 \\of {x+y"));
    }

    @Test
    void buildrelWithoutOverIsLeftUntouched() {
        assertEquals("\\buildrel x", LatexUtils.sanitizeFormulaSource("\\buildrel x"));
    }

    @Test
    void overwithdelimsWithoutDelimitersIsLeftUntouched() {
        // No delimiter tokens follow the operator -> convertDelimitedInfix bails, operator left in place.
        assertEquals("{a \\overwithdelims}", LatexUtils.sanitizeFormulaSource("{a \\overwithdelims}"));
    }

    @Test
    void overwithdelimsWithBraceDelimiterIsLeftUntouched() {
        // A delimiter token may not be a brace group -> the operator is left untouched.
        assertEquals("{a \\overwithdelims { } b}", LatexUtils.sanitizeFormulaSource("{a \\overwithdelims { } b}"));
    }

    @Test
    void overwithdelimsWithControlSymbolDelimiters() {
        // Delimiters given as escaped braces "\{" / "\}" (control symbols) -> \left\{ ... \right\}.
        assertEquals("{\\left\\{\\frac{a }{ b}\\right\\}}", LatexUtils.sanitizeFormulaSource("{a \\overwithdelims \\{ \\} b}"));
    }

    @Test
    void unbalancedOpeningBraceInCellIsCopiedVerbatim() {
        // The denominator "b{c" has an unmatched "{": it is copied verbatim (no group to recurse into).
        assertEquals("\\frac{a }{ b{c}", LatexUtils.sanitizeFormulaSource("a \\over b{c"));
    }

    @Test
    void buildrelWithBracedTopBeforeOver() {
        // Braces (and a control word) between \buildrel and \over exercise the depth-tracking scan that locates \over.
        assertEquals("\\overset{{x}}{y}", LatexUtils.sanitizeFormulaSource("\\buildrel {x} \\over y"));
    }

    @Test
    void emptyMatrixMacroBodyConverts() {
        assertEquals("\\begin{matrix}\\end{matrix}", LatexUtils.sanitizeFormulaSource("\\matrix{}"));
    }

    @Test
    void literalCrAfterRowBreakIsNotStripped() {
        // "\\cr" is a "\\" row break followed by the literal letters "cr" (even backslash run), not a \cr control
        // word, so the trailing-separator strip must leave it; only the interior separator handling applies.
        assertEquals("\\begin{matrix}a \\\\cr\\end{matrix}", LatexUtils.sanitizeFormulaSource("\\matrix{a \\\\cr}"));
    }

    @Test
    void leavesPlainFormulaUntouched() {
        String plain = "\\frac{-b\\pm\\sqrt{b^2-4ac}}{2a}";
        assertEquals(plain, LatexUtils.sanitizeFormulaSource(plain));
    }
}
