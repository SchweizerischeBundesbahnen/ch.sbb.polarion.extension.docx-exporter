package ch.sbb.polarion.extension.docx_exporter.util;

import ch.sbb.polarion.extension.generic.regex.RegexMatcher;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Helpers that massage the LaTeX source of Polarion formulas into a form that Pandoc's math reader
 * (the {@code texmath} library) can convert into native Word equations (OMML).
 *
 * <h2>Why this class exists</h2>
 * In Polarion a formula is stored as {@code <img class="polarion-rte-formula" data-source="<LaTeX>">}
 * and rendered for the browser by <b>MathJax</b>. Our DOCX export does not embed the rendered image;
 * instead it hands the raw LaTeX to Pandoc as {@code <script type="math/tex">...</script>}, Pandoc
 * converts it to OMML, and <b>Word</b> draws it as a real, editable equation.
 * <p>
 * The catch is that MathJax accepts a much wider, plain-TeX-flavored syntax than Pandoc's
 * {@code texmath} does. When {@code texmath} cannot parse an expression it silently gives up and
 * Pandoc emits the raw LaTeX as literal {@code $$...$$} text into the document, so the formula shows
 * up as source code instead of a rendered equation. Two MathJax-isms that Polarion routinely
 * produces, but {@code texmath} rejects, are:
 * <ul>
 *   <li>the plain-TeX matrix/alignment <i>macros</i> ({@code \pmatrix{...}}, {@code \matrix{...}},
 *       {@code \cases{...}}, {@code \eqalign{...}}, ...) - {@code texmath} only understands the
 *       amsmath <i>environment</i> form ({@code \begin{pmatrix}...\end{pmatrix}});</li>
 *   <li>the plain-TeX row separator {@code \cr} - {@code texmath} only understands {@code \\}.</li>
 * </ul>
 * The transforms here rewrite those two constructs into their {@code texmath}-compatible equivalents
 * so the common case (matrices and multi-line/aligned formulas) renders correctly. This is a
 * targeted compatibility shim, not a full MathJax-to-texmath translation: some exotic constructs
 * ({@code \textcolor}, {@code \ce}, user {@code \def} macros, ...) are still not handled and may fall
 * back to {@code $$...$$} text.
 */
@UtilityClass
@SuppressWarnings("SpellCheckingInspection") // remove visual distraction because here we have a lot of false-positives ('infty', 'cdot' etc.)
public class LatexUtils {

    // All regex matchers below use our RegexMatcher wrapper, which defaults to the RE2J engine (linear-time, the
    // preferred engine in this codebase). Flags are expressed as inline modifiers ((?i), (?s)) rather than the
    // flag-int argument: RegexMatcher's flag constants carry RE2J's native values, so inline modifiers keep the
    // patterns engine-agnostic and unambiguous.

    // Matches <br>, <br/>, <br />, <BR/> and other case/whitespace variants Polarion may emit inside formula
    // data-source attributes (the attribute value is a raw string, not HTML-parsed by Jsoup). (?i) = case-insensitive.
    private static final RegexMatcher BR_TAG_MATCHER = RegexMatcher.get("(?i)<br\\s*/?>");

    // Under XML output syntax, Jsoup (3.1.x / 1.21.2) wraps the data of a <script> element in "//<![CDATA[\n ... \n//]]>".
    // Pandoc expects the raw LaTeX inside <script type="math/tex">, so we unwrap this serialization artifact for our
    // formula scripts. (?s) = DOTALL. The body quantifiers are plain (not possessive): RE2J runs in guaranteed linear
    // time, so the possessive quantifiers the java.util.regex version needed to bound backtracking are unnecessary.
    private static final RegexMatcher MATH_TEX_CDATA_MATCHER = RegexMatcher.get(
            "(?s)(<script type=\"math/tex(?:; mode=display)?\">)//<!\\[CDATA\\[[\\r\\n]+(.*?)[\\r\\n]+//\\]\\]>(</script>)");

    // The two-backslash row separator that texmath expects. Passed as a literal replacement to RegexMatcher#replaceAll,
    // which quotes it for us (so the backslashes are emitted verbatim rather than treated as replacement escapes).
    private static final String LATEX_ROW_SEPARATOR = "\\\\";

    // Matches MathJax's "\require{packageName}" directive, which dynamically loads a MathJax TeX extension in the browser.
    // It is meaningless to texmath and makes the whole formula fail to parse, so we strip it. The package name never
    // contains braces, so a simple (non-balanced) "{...}" match is sufficient.
    private static final RegexMatcher REQUIRE_DIRECTIVE_MATCHER = RegexMatcher.get("\\\\require\\s*\\{[^}]*\\}");

    // Matches the "\cfrac" continued-fraction command as a whole control word (the word boundary stops it matching a
    // longer name). texmath cannot parse "\cfrac" but renders "\frac" fine; we lose only the continued-fraction layout.
    private static final RegexMatcher CFRAC_MATCHER = RegexMatcher.get("\\\\cfrac\\b");

    // texmath-compatible replacements, passed literally to RegexMatcher#replaceAll (which quotes them, so the backslash
    // is emitted verbatim rather than treated as a replacement escape - same contract as LATEX_ROW_SEPARATOR above).
    private static final String FRAC_COMMAND = "\\frac";
    private static final String EMPTY = "";

    // Plain-TeX control words handled by the balanced-scan transforms below, matched exactly (never as a prefix of a
    // longer command: "\over" must not match "\overline", "\overset" or "\overwithdelims", and "\atop" must not match
    // "\atopwithdelims" - the delimited variants are handled by their own exact-name entries below).
    private static final String OVER_COMMAND = "over";
    private static final String ATOP_COMMAND = "atop";
    private static final String OVERWITHDELIMS_COMMAND = "overwithdelims";
    private static final String ATOPWITHDELIMS_COMMAND = "atopwithdelims";
    private static final String ROOT_COMMAND = "root";
    private static final String OF_COMMAND = "of";
    private static final String CR_COMMAND = "cr";
    private static final String BUILDREL_COMMAND = "buildrel";

    /**
     * Maps each plain-TeX/old-MathJax font-switch command (without the leading backslash) to the {@code \mathXX} command
     * {@code texmath} understands. The switches are declaration-style ({@code \rm} sets the font for the rest of the
     * current group/cell), whereas the targets are argument-style ({@code \mathrm{...}}); {@link #convertOldFontSwitches}
     * bridges the two by wrapping the switch's scope. {@code texmath} cannot parse the bare switches at all (they leak the
     * whole formula), but accepts every target here.
     */
    private static final Map<String, String> OLD_FONT_SWITCHES = Map.ofEntries(
            Map.entry("rm", "mathrm"),
            Map.entry("bf", "mathbf"),
            Map.entry("it", "mathit"),
            Map.entry("sf", "mathsf"),
            Map.entry("tt", "mathtt"),
            Map.entry("cal", "mathcal"),
            Map.entry("frak", "mathfrak"),
            Map.entry("Bbb", "mathbb"),
            Map.entry("scr", "mathscr")
    );

    /**
     * Maps each plain-TeX matrix/alignment macro name (without the leading backslash) that {@code texmath}
     * cannot parse to the amsmath environment name it understands. The matrix variants keep their own delimiters
     * ({@code pmatrix} = parentheses, {@code bmatrix} = brackets, {@code vmatrix} = single bars, ...). The plain-TeX
     * alignment macros are mapped to the closest amsmath environment: {@code \eqalign} -> {@code aligned} (relation
     * alignment), {@code \displaylines} -> {@code gathered} (centred lines).
     */
    private static final Map<String, String> PLAIN_TEX_MATRIX_ENVIRONMENTS = Map.ofEntries(
            Map.entry("matrix", "matrix"),
            Map.entry("pmatrix", "pmatrix"),
            Map.entry("bmatrix", "bmatrix"),
            Map.entry("Bmatrix", "Bmatrix"),
            Map.entry("vmatrix", "vmatrix"),
            Map.entry("Vmatrix", "Vmatrix"),
            Map.entry("cases", "cases"),
            Map.entry("eqalign", "aligned"),
            Map.entry("displaylines", "gathered")
    );

    /**
     * Rewrites the LaTeX source of a Polarion formula into a Pandoc/{@code texmath}-compatible form.
     * The steps are applied in order:
     * <ol>
     *   <li>replace formatting {@code <br>} tags with a space (see {@link #replaceFormattingLineBreaks});</li>
     *   <li>strip MathJax {@code \require{...}} directives (see {@link #stripRequireDirectives});</li>
     *   <li>rewrite {@code \cfrac} continued fractions to {@code \frac} (see {@link #convertContinuedFractions});</li>
     *   <li>rewrite {@code \root n \of x} into {@code \sqrt[n]{x}} (see {@link #convertRoots});</li>
     *   <li>rewrite {@code \buildrel A \over B} into {@code \overset{A}{B}} (see {@link #convertBuildrel});</li>
     *   <li>rewrite the infix operators {@code \over} / {@code \atop} (into {@code \frac} / {@code \substack}) and the
     *       delimited {@code \overwithdelims} / {@code \atopwithdelims} (into {@code \left..\frac..\right} /
     *       {@code \left..\substack..\right}) (see {@link #convertInfixOperators});</li>
     *   <li>rewrite old declaration-style font switches ({@code \rm}, {@code \bf}, ...) into {@code \mathrm{...}},
     *       {@code \mathbf{...}}, ... (see {@link #convertOldFontSwitches});</li>
     *   <li>convert plain-TeX matrix/alignment macros into amsmath environments
     *       (see {@link #convertPlainTexMatrices});</li>
     *   <li>convert {@code \cr} row separators into {@code \\} (see {@link #convertCrRowSeparators}).</li>
     * </ol>
     * {@code \buildrel} runs <i>before</i> the infix rewrite on purpose: its syntax ends in a {@code \over} keyword that
     * the infix rewrite would otherwise consume as a fraction. The font-switch rewrite runs <i>after</i> {@code \buildrel}
     * (so a {@code \rm} inside the produced {@code \overset} argument is handled) but <i>before</i> the matrix rewrite (so
     * a switch is scoped within its matrix cell while the cells are still brace-delimited).
     * <p>
     * The infix rewrite runs <i>before</i> the matrix rewrite on purpose: {@code \over} / {@code \atop} scope to their enclosing
     * brace group, and while a matrix is still in the plain-TeX macro form ({@code \pmatrix{...}}) its cells are inside
     * that group, so a {@code \over} in a cell is correctly handled. Once the matrix is in amsmath environment form its
     * cells are no longer brace-delimited, which would make the infix scan ambiguous.
     *
     * @param latex the raw LaTeX taken from a formula's {@code data-source} attribute
     * @return the normalized LaTeX, safe to feed to Pandoc
     */
    @NotNull
    public String sanitizeFormulaSource(@NotNull String latex) {
        String result = replaceFormattingLineBreaks(latex);
        result = stripRequireDirectives(result);
        result = convertContinuedFractions(result);
        result = convertRoots(result);
        result = convertBuildrel(result);
        result = convertInfixOperators(result);
        result = convertOldFontSwitches(result);
        result = convertPlainTexMatrices(result);
        result = convertCrRowSeparators(result);
        return result;
    }

    /**
     * Replaces every {@code <br>} variant with a single space.
     * <p>
     * Polarion uses {@code <br>} tags only to lay the formula source out across several lines in the editor; they
     * are not meant to appear in the rendered formula. We must drop them, but replacing them with an <i>empty</i>
     * string would glue a control word to the following token: e.g. a matrix row written as
     * {@code "\cr<br/>a_{21}"} would collapse into the undefined control sequence {@code "\cra_{21}"}, making
     * {@code texmath} fail to parse the whole expression. A space is ignored in LaTeX math mode but preserves the
     * control-word boundary ({@code "\cr a_{21}"}).
     */
    @NotNull
    private String replaceFormattingLineBreaks(@NotNull String latex) {
        return BR_TAG_MATCHER.replaceAll(latex, " ");
    }

    /**
     * Converts {@code \cr} row separators into the {@code \\} separator that {@code texmath} expects.
     * Applied globally because {@code \cr} only ever appears as a row/line separator (inside matrices, arrays,
     * {@code cases}, alignment blocks, ...).
     * <p>
     * This is a character scan rather than a regex: a TeX control word ends at the first non-<i>letter</i>, so
     * {@code \cr0} and {@code \cr_} are {@code \cr} followed by {@code 0}/{@code _} and must convert - but RE2J's
     * {@code \b} counts digits and {@code _} as word characters (and RE2J has no lookahead), so a {@code \\cr\b}
     * pattern would miss them. {@link #isControlWordAt} applies the correct letter-boundary rule, and a bare
     * {@code \\} (or any escaped backslash) is copied as a two-character unit so its second backslash is never
     * mistaken for the start of a {@code \cr}.
     */
    @NotNull
    private String convertCrRowSeparators(@NotNull String latex) {
        StringBuilder out = new StringBuilder(latex.length());
        int i = 0;
        int n = latex.length();
        while (i < n) {
            char c = latex.charAt(i);
            if (c != '\\') {
                out.append(c);
                i++;
                continue;
            }
            if (isControlWordAt(latex, i, CR_COMMAND)) {
                out.append(LATEX_ROW_SEPARATOR);
                i += 1 + CR_COMMAND.length();
            } else if (i + 1 < n) {
                out.append(c).append(latex.charAt(i + 1)); // copy "\\" / escaped char as a unit
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * Strips MathJax {@code \require{...}} directives. {@code \require} is a MathJax-only command that loads a TeX
     * extension at render time; {@code texmath} does not understand it and the whole formula fails to parse. Removing it
     * lets the rest of the formula convert (e.g. {@code \require{cancel}\cancel{x}} -> {@code \cancel{x}}, which
     * {@code texmath} supports natively).
     */
    @NotNull
    private String stripRequireDirectives(@NotNull String latex) {
        return REQUIRE_DIRECTIVE_MATCHER.replaceAll(latex, EMPTY);
    }

    /**
     * Rewrites {@code \cfrac} (continued fraction) into {@code \frac}. {@code texmath} cannot parse {@code \cfrac}, so it
     * would leak as text; {@code \frac} renders the same fraction, only without the continued-fraction layout. Applied
     * globally as a whole-word replacement, so nested continued fractions are all converted.
     */
    @NotNull
    private String convertContinuedFractions(@NotNull String latex) {
        return CFRAC_MATCHER.replaceAll(latex, FRAC_COMMAND);
    }

    /**
     * Rewrites the plain-TeX root macro {@code \root <index> \of <radicand>} into {@code \sqrt[<index>]{<radicand>}},
     * which {@code texmath} understands. The {@code <index>} is everything between {@code \root} and the matching
     * {@code \of}; the {@code <radicand>} is the following braced group or single token (control word or character), as
     * TeX scopes it. The index and radicand are processed recursively so a nested {@code \root} is converted too.
     */
    @NotNull
    private String convertRoots(@NotNull String latex) {
        StringBuilder out = new StringBuilder(latex.length());
        int i = 0;
        int n = latex.length();
        while (i < n) {
            char c = latex.charAt(i);
            if (c == '\\' && isControlWordAt(latex, i, ROOT_COMMAND)) {
                int afterRoot = i + 1 + ROOT_COMMAND.length();
                int ofIndex = indexOfControlWord(latex, afterRoot, OF_COMMAND);
                int end = ofIndex == -1 ? -1 : appendRoot(out, latex, afterRoot, ofIndex);
                if (end != -1) {
                    i = end;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * Emits {@code \sqrt[index]{radicand}} for a {@code \root ... \of ...} occurrence and returns the index just past the
     * radicand, or {@code -1} if the radicand is an unbalanced brace group (in which case the caller leaves the source
     * untouched).
     */
    private int appendRoot(@NotNull StringBuilder out, @NotNull String latex, int afterRoot, int ofIndex) {
        String index = latex.substring(afterRoot, ofIndex).trim();
        Argument radicand = readArgument(latex, ofIndex + 1 + OF_COMMAND.length());
        if (radicand == null) {
            return -1;
        }
        out.append("\\sqrt[").append(convertRoots(index)).append("]{").append(convertRoots(radicand.content())).append('}');
        return radicand.end();
    }

    /**
     * Rewrites the plain-TeX stacking macro {@code \buildrel <top> \over <bottom>} into {@code \overset{<top>}{<bottom>}},
     * which {@code texmath} understands (e.g. {@code \buildrel \rm def \over =} -> {@code \overset{\rm def}{=}}, the
     * "defined as" symbol; the {@code \rm} is handled later by {@link #convertOldFontSwitches}). {@code <top>} is
     * everything between {@code \buildrel} and the matching {@code \over} keyword; {@code <bottom>} is the following braced
     * group or single token, as TeX scopes it. This must run <i>before</i> {@link #convertInfixOperators}, otherwise that
     * pass would consume the {@code \over} keyword as an infix fraction. The top and bottom are processed recursively so a
     * nested {@code \buildrel} is converted too.
     */
    @NotNull
    private String convertBuildrel(@NotNull String latex) {
        StringBuilder out = new StringBuilder(latex.length());
        int i = 0;
        int n = latex.length();
        while (i < n) {
            char c = latex.charAt(i);
            if (c == '\\' && isControlWordAt(latex, i, BUILDREL_COMMAND)) {
                int afterBuildrel = i + 1 + BUILDREL_COMMAND.length();
                int overIndex = indexOfControlWord(latex, afterBuildrel, OVER_COMMAND);
                int end = overIndex == -1 ? -1 : appendBuildrel(out, latex, afterBuildrel, overIndex);
                if (end != -1) {
                    i = end;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * Emits {@code \overset{top}{bottom}} for a {@code \buildrel ... \over ...} occurrence and returns the index just past
     * the bottom argument, or {@code -1} if the bottom is an unbalanced brace group (in which case the caller leaves the
     * source untouched).
     */
    private int appendBuildrel(@NotNull StringBuilder out, @NotNull String latex, int afterBuildrel, int overIndex) {
        String top = latex.substring(afterBuildrel, overIndex).trim();
        Argument bottom = readArgument(latex, overIndex + 1 + OVER_COMMAND.length());
        if (bottom == null) {
            return -1;
        }
        out.append("\\overset{").append(convertBuildrel(top)).append("}{").append(convertBuildrel(bottom.content())).append('}');
        return bottom.end();
    }

    /**
     * A macro argument parsed by {@link #readArgument}: its raw (not-yet-recursively-processed) content and the index
     * in the source just past it.
     */
    private record Argument(@NotNull String content, int end) {
    }

    /**
     * Reads the single argument that follows a delimiting keyword - the radicand after {@code \of} in
     * {@code \root ... \of ...}, or the bottom after {@code \over} in {@code \buildrel ... \over ...} - starting at
     * {@code from} after skipping whitespace: either a braced group (whose inner content is returned) or a single TeX
     * token (control sequence or character). Returns {@code null} if it is an unbalanced brace group, so the caller can
     * leave the source untouched.
     */
    @Nullable
    private Argument readArgument(@NotNull String latex, int from) {
        int start = from;
        while (start < latex.length() && Character.isWhitespace(latex.charAt(start))) {
            start++;
        }
        if (start < latex.length() && latex.charAt(start) == '{') {
            int closing = findMatchingBrace(latex, start);
            if (closing == -1) {
                return null;
            }
            return new Argument(latex.substring(start + 1, closing), closing + 1);
        }
        int end = readSingleToken(latex, start);
        return new Argument(latex.substring(start, end), end);
    }

    /**
     * Rewrites the plain-TeX infix operators {@code \over} and {@code \atop}. In TeX, {@code A \over B} turns the entire
     * surrounding group into the fraction {@code \frac{A}{B}}, and {@code A \atop B} into the same stack <i>without</i> a
     * fraction rule; {@code texmath} has no ruleless-fraction primitive, so {@code \atop} maps to {@code \substack{A \\ B}}
     * (the closest construct {@code texmath} accepts: a centred stack with no rule). Both operators scope to their
     * enclosing brace group <i>and</i> their alignment cell. We therefore split the body into top-level alignment cells
     * (separated by {@code &}, {@code \cr} or {@code \\}) and handle the operator within each cell only, so an operator
     * inside a matrix cell does not swallow the neighboring cells. The delimited variants {@code \overwithdelims} and
     * {@code \atopwithdelims} are handled too (see {@link #convertDelimitedInfix}). All four operators are matched as
     * whole control words, so {@code \overline}, {@code \overset} and {@code \overbrace} are left untouched, and
     * {@code \over} does not swallow {@code \overwithdelims} (nor {@code \atop} the {@code \atopwithdelims}).
     */
    @NotNull
    private String convertInfixOperators(@NotNull String body) {
        StringBuilder out = new StringBuilder(body.length());
        int cellStart = 0;
        int depth = 0;
        int i = 0;
        int n = body.length();
        while (i < n) {
            char c = body.charAt(i);
            if (c == '\\') {
                int after = skipControlSequence(body, i);
                if (depth == 0 && isRowSeparatorToken(body, i, after)) {
                    out.append(convertCellInfixOperator(body.substring(cellStart, i))).append(body, i, after);
                    cellStart = after;
                }
                i = after;
            } else if (c == '{') {
                depth++;
                i++;
            } else if (c == '}') {
                depth = Math.max(0, depth - 1); // clamp: a stray unmatched '}' must not go negative and misclassify a later separator
                i++;
            } else if (c == '&' && depth == 0) {
                out.append(convertCellInfixOperator(body.substring(cellStart, i))).append('&');
                cellStart = i + 1;
                i++;
            } else {
                i++;
            }
        }
        out.append(convertCellInfixOperator(body.substring(cellStart)));
        return out.toString();
    }

    /**
     * Handles a single alignment cell: if it contains a top-level {@code \over} the cell becomes
     * {@code \frac{before}{after}}, and a top-level {@code \atop} becomes {@code \substack{before \\ after}} (both sides
     * processed recursively in either case); otherwise the cell is scanned and the transform recurses into its nested
     * brace groups so a {@code {a \over b}} or {@code {a \atop b}} buried inside the cell is still converted. The first
     * top-level operator wins; in valid TeX a single group never carries more than one.
     */
    @NotNull
    private String convertCellInfixOperator(@NotNull String cell) {
        int depth = 0;
        int i = 0;
        int n = cell.length();
        while (i < n) {
            char c = cell.charAt(i);
            if (c == '\\') {
                int after = skipControlSequence(cell, i);
                if (depth == 0) {
                    String converted = tryConvertInfixOperatorAt(cell, i, after);
                    if (converted != null) {
                        return converted;
                    }
                }
                i = after;
            } else if (c == '{') {
                depth++;
                i++;
            } else if (c == '}') {
                depth = Math.max(0, depth - 1); // clamp: a stray unmatched '}' must not go negative and misclassify a later operator
                i++;
            } else {
                i++;
            }
        }
        return recurseIntoGroups(cell);
    }

    /**
     * If the control word spanning {@code [opStart, nameEnd)} is a top-level infix fraction operator, returns the cell
     * rewritten around it ({@code \over} -> {@code \frac}, {@code \atop} -> {@code \substack}, and the delimited
     * {@code \overwithdelims}/{@code \atopwithdelims} -> {@code \left..\right}); otherwise {@code null}. The first
     * operator wins; in valid TeX a single group carries at most one.
     */
    @Nullable
    private String tryConvertInfixOperatorAt(@NotNull String cell, int opStart, int nameEnd) {
        if (isExactName(cell, opStart, nameEnd, OVER_COMMAND)) {
            return "\\frac{" + convertInfixOperators(cell.substring(0, opStart)) + "}{" + convertInfixOperators(cell.substring(nameEnd)) + "}";
        }
        if (isExactName(cell, opStart, nameEnd, ATOP_COMMAND)) {
            return "\\substack{" + convertInfixOperators(cell.substring(0, opStart)) + "\\\\" + convertInfixOperators(cell.substring(nameEnd)) + "}";
        }
        if (isExactName(cell, opStart, nameEnd, OVERWITHDELIMS_COMMAND) || isExactName(cell, opStart, nameEnd, ATOPWITHDELIMS_COMMAND)) {
            return convertDelimitedInfix(cell, opStart, nameEnd, isExactName(cell, opStart, nameEnd, ATOPWITHDELIMS_COMMAND));
        }
        return null;
    }

    /**
     * Rewrites the delimited infix operators {@code \overwithdelims} and {@code \atopwithdelims}. In TeX,
     * {@code A \overwithdelims D1 D2 B} is the fraction {@code A/B} (with a rule) enclosed in the delimiters
     * {@code D1}, {@code D2}; {@code \atopwithdelims} is the same without the rule (e.g. {@code n \atopwithdelims () k}
     * is the binomial coefficient). {@code texmath} understands neither, but it does understand
     * {@code \left D1 ... \right D2}, so we rewrite to {@code \left D1 \frac{A}{B} \right D2} (or {@code \substack} for
     * the ruleless variant). {@code D1} and {@code D2} are the two delimiter tokens that immediately follow the command
     * (a single character such as {@code [} or a control sequence such as {@code \langle}; {@code .} is the null
     * delimiter, which {@code \left} / {@code \right} also accept). The numerator {@code A} (the cell text before the
     * operator) and denominator {@code B} (the text after the two delimiters) are processed recursively.
     *
     * @param cell        the alignment cell containing the operator
     * @param operatorPos the index of the operator's backslash
     * @param afterCommand the index just past the operator's control word
     * @param ruleless    {@code true} for {@code \atopwithdelims} (no rule, maps to {@code \substack}), {@code false}
     *                    for {@code \overwithdelims} (maps to {@code \frac})
     * @return the rewritten expression, or {@code null} if the two delimiter tokens cannot be read (truncated or
     * brace-delimited source), in which case the caller leaves the operator untouched
     */
    @Nullable
    private String convertDelimitedInfix(@NotNull String cell, int operatorPos, int afterCommand, boolean ruleless) {
        int n = cell.length();
        int leftStart = skipWhitespace(cell, afterCommand);
        if (leftStart >= n || isBrace(cell.charAt(leftStart))) {
            return null;
        }
        int leftEnd = readSingleToken(cell, leftStart);
        int rightStart = skipWhitespace(cell, leftEnd);
        if (rightStart >= n || isBrace(cell.charAt(rightStart))) {
            return null;
        }
        int rightEnd = readSingleToken(cell, rightStart);

        String leftDelim = cell.substring(leftStart, leftEnd);
        String rightDelim = cell.substring(rightStart, rightEnd);
        String numerator = convertInfixOperators(cell.substring(0, operatorPos));
        String denominator = convertInfixOperators(cell.substring(rightEnd));
        String fraction = ruleless
                ? "\\substack{" + numerator + "\\\\" + denominator + "}"
                : "\\frac{" + numerator + "}{" + denominator + "}";
        return "\\left" + leftDelim + fraction + "\\right" + rightDelim;
    }

    /**
     * Returns the index of the first non-whitespace character at or after {@code from} (or the string length if none).
     */
    private int skipWhitespace(@NotNull String s, int from) {
        int i = from;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private boolean isBrace(char c) {
        return c == '{' || c == '}';
    }

    /**
     * Removes a single trailing row separator ({@code \cr} or {@code \\}), plus surrounding whitespace, from a
     * matrix/alignment macro body. MathJax authors idiomatically end the last row with {@code \cr} (e.g.
     * {@code \eqalign{ a &= b \cr c &= d \cr }}); once the macro becomes an amsmath environment that trailing
     * separator is an <i>empty final row</i>, which Word renders as a stray dotted placeholder box (most visible in
     * {@code aligned}/{@code gathered}). Only the last separator is stripped, so intentional empty interior rows are
     * kept. Internal separators are converted to {@code \\} later by {@link #convertCrRowSeparators}.
     */
    @NotNull
    private String stripTrailingRowSeparator(@NotNull String body) {
        int end = body.length();
        while (end > 0 && Character.isWhitespace(body.charAt(end - 1))) {
            end--;
        }
        if (end >= 2 && body.charAt(end - 1) == '\\' && body.charAt(end - 2) == '\\') {
            end -= 2; // trailing "\\"
        } else if (end >= 3 && body.charAt(end - 1) == 'r' && body.charAt(end - 2) == 'c' && isControlWordBackslash(body, end - 3)) {
            end -= 3; // trailing control word "\cr" (not the literal "cr" after an escaped "\\")
        } else {
            return body;
        }
        while (end > 0 && Character.isWhitespace(body.charAt(end - 1))) {
            end--;
        }
        return body.substring(0, end);
    }

    /**
     * Whether the backslash at {@code index} starts a control word rather than being the second half of an escaped
     * {@code \\}. A backslash is a control-word introducer only when the run of consecutive backslashes ending at
     * {@code index} is odd (an even run pairs up entirely into {@code \\} escapes). So for {@code \cr} -> true,
     * {@code \\cr} -> false, {@code \\\cr} -> true, ... - correct for any number of leading backslashes.
     */
    private boolean isControlWordBackslash(@NotNull String body, int index) {
        // Count the run of backslashes ending at index (0 if index is not a backslash). Caller guarantees index >= 0.
        int backslashes = 0;
        for (int i = index; i >= 0 && body.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    /**
     * Copies {@code s} verbatim except that each top-level brace group has its content rewritten by
     * {@link #convertInfixOperators}, so an infix {@code \over} / {@code \atop} nested inside a group is converted while
     * the surrounding text is preserved. Escaped characters (backslash + next char) are copied as a unit so {@code \{} /
     * {@code \}} do not disturb brace matching.
     */
    @NotNull
    private String recurseIntoGroups(@NotNull String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            i = s.charAt(i) == '\\'
                    ? appendEscapedBackslash(out, s, i)
                    : appendCharOrGroup(out, s, i, LatexUtils::convertInfixOperators);
        }
        return out.toString();
    }

    /**
     * Appends the backslash at {@code i} together with its next character as a unit (so an escaped {@code \{} / {@code \}}
     * or a {@code \\} row break does not disturb brace matching), and returns the index just past it.
     */
    private int appendEscapedBackslash(@NotNull StringBuilder out, @NotNull String s, int i) {
        out.append(s.charAt(i));
        if (i + 1 < s.length()) {
            out.append(s.charAt(i + 1));
            return i + 2;
        }
        return i + 1;
    }

    /**
     * Appends {@code s.charAt(i)}; if it opens a balanced top-level brace group, the group is emitted with its content
     * rewritten by {@code groupTransform} (otherwise the character is copied verbatim). Returns the index just past what
     * was appended. Assumes {@code s.charAt(i)} is not a backslash (callers handle backslashes first).
     */
    private int appendCharOrGroup(@NotNull StringBuilder out, @NotNull String s, int i, @NotNull UnaryOperator<String> groupTransform) {
        if (s.charAt(i) == '{') {
            int closing = findMatchingBrace(s, i);
            if (closing != -1) {
                out.append('{').append(groupTransform.apply(s.substring(i + 1, closing))).append('}');
                return closing + 1;
            }
        }
        out.append(s.charAt(i));
        return i + 1;
    }

    /**
     * Rewrites old declaration-style font switches ({@code \rm}, {@code \bf}, {@code \it}, ...) into the argument-style
     * {@code \mathXX{...}} commands {@code texmath} understands (see {@link #OLD_FONT_SWITCHES}). {@code texmath} cannot
     * parse the bare switches and leaks the whole formula, whereas the {@code \mathXX} forms convert.
     * <p>
     * A switch is a declaration: it applies to the rest of its enclosing group <i>and</i> alignment cell. We therefore
     * wrap everything from the switch up to the end of the current group or the next top-level cell separator
     * ({@code &}, {@code \cr}, {@code \\}) - whichever comes first - in {@code \mathXX{...}}. The scan recurses into nested
     * brace groups (so a switch confined to an inner group is handled) and into the wrapped scope (so a later switch that
     * overrides the first - e.g. {@code {\rm a {\bf b}}} - is handled).
     */
    @NotNull
    private String convertOldFontSwitches(@NotNull String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n && isAsciiLetter(s.charAt(i + 1))) {
                i = appendControlWordOrFontSwitch(out, s, i);
            } else if (c == '\\') {
                i = appendEscapedBackslash(out, s, i);
            } else {
                i = appendCharOrGroup(out, s, i, LatexUtils::convertOldFontSwitches);
            }
        }
        return out.toString();
    }

    /**
     * Handles a control word starting at {@code i} (a backslash followed by letters). If it is an old font switch, its
     * scope (up to the end of the group/cell, via {@link #findCellEnd}) is wrapped as {@code \mathXX{...}}; otherwise the
     * control word is copied verbatim. Returns the index just past what was consumed.
     */
    private int appendControlWordOrFontSwitch(@NotNull StringBuilder out, @NotNull String s, int i) {
        int nameEnd = controlWordEnd(s, i);
        String mathCommand = OLD_FONT_SWITCHES.get(s.substring(i + 1, nameEnd));
        if (mathCommand == null) {
            out.append(s, i, nameEnd);
            return nameEnd;
        }
        int scopeEnd = findCellEnd(s, nameEnd);
        out.append('\\').append(mathCommand).append('{').append(convertOldFontSwitches(s.substring(nameEnd, scopeEnd).strip())).append('}');
        return scopeEnd;
    }

    /**
     * Returns the index where the current alignment cell ends at or after {@code from}: the first top-level ({@code depth}
     * 0) cell separator ({@code &}, {@code \cr} or {@code \\}), or the string length if none. Nested brace groups and
     * escaped characters are skipped. Used to bound a font-switch declaration to its cell.
     */
    private int findCellEnd(@NotNull String s, int from) {
        int depth = 0;
        int i = from;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\') {
                int after = skipControlSequence(s, i);
                if (depth == 0 && isRowSeparatorToken(s, i, after)) {
                    return i;
                }
                i = after;
            } else if (c == '{') {
                depth++;
                i++;
            } else if (c == '}' && depth == 0) {
                return i; // the enclosing group closes here
            } else if (c == '}') {
                depth--;
                i++;
            } else if (c == '&' && depth == 0) {
                return i;
            } else {
                i++;
            }
        }
        return i;
    }

    /**
     * Returns the index just past the control word that starts with the backslash at {@code backslashIndex} (i.e. the
     * first character after its run of ASCII letters). Assumes the character after the backslash is a letter.
     */
    private int controlWordEnd(@NotNull String s, int backslashIndex) {
        int end = backslashIndex + 1;
        while (end < s.length() && isAsciiLetter(s.charAt(end))) {
            end++;
        }
        return end;
    }

    /**
     * Whether the control word spanning {@code [backslashIndex+1, nameEnd)} equals exactly {@code name}.
     */
    private boolean isExactName(@NotNull String s, int backslashIndex, int nameEnd, @NotNull String name) {
        return nameEnd - (backslashIndex + 1) == name.length() && s.regionMatches(backslashIndex + 1, name, 0, name.length());
    }

    /**
     * Whether a complete control word {@code \name} starts at {@code index} (a backslash, the exact letters of
     * {@code name}, then a non-letter or end of string, so it never matches a longer command).
     */
    private boolean isControlWordAt(@NotNull String s, int index, @NotNull String name) {
        if (index >= s.length() || s.charAt(index) != '\\') {
            return false;
        }
        int end = index + 1 + name.length();
        if (end > s.length() || !s.regionMatches(index + 1, name, 0, name.length())) {
            return false;
        }
        return end == s.length() || !isAsciiLetter(s.charAt(end));
    }

    /**
     * Returns the index of the control word {@code \name} at brace depth 0 at or after {@code from}, or {@code -1} if
     * none is found. Nested groups and escaped characters are skipped.
     */
    private int indexOfControlWord(@NotNull String s, int from, @NotNull String name) {
        int depth = 0;
        int i = from;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\') {
                if (depth == 0 && isControlWordAt(s, i, name)) {
                    return i;
                }
                i = skipControlSequence(s, i);
            } else if (c == '{') {
                depth++;
                i++;
            } else if (c == '}') {
                depth = Math.max(0, depth - 1); // clamp: a stray unmatched '}' must not go negative and hide a later top-level control word
                i++;
            } else {
                i++;
            }
        }
        return -1;
    }

    /**
     * Reads a single TeX token starting at {@code from}: a control sequence (backslash + letters, or backslash + one
     * symbol) or a single character. Returns the index just past the token.
     */
    private int readSingleToken(@NotNull String s, int from) {
        int n = s.length();
        if (from >= n) {
            return from;
        }
        if (s.charAt(from) == '\\') {
            return from + 1 < n && isAsciiLetter(s.charAt(from + 1)) ? controlWordEnd(s, from) : Math.min(from + 2, n);
        }
        return from + 1;
    }

    /**
     * Rewrites plain-TeX matrix/alignment macros ({@code \pmatrix{...}}, {@code \matrix{...}}, {@code \cases{...}},
     * {@code \eqalign{...}}, ...) into the corresponding amsmath environment ({@code \begin{pmatrix}...\end{pmatrix}},
     * ...), which is the only form {@code texmath} understands.
     * <p>
     * A regular expression cannot do this reliably because the macro body contains nested braces (e.g. {@code a_{11}}),
     * so we scan the string and match braces by depth. The scan is recursive on the macro body so that a matrix nested
     * inside another matrix's cell is converted as well.
     */
    @NotNull
    private String convertPlainTexMatrices(@NotNull String latex) {
        StringBuilder out = new StringBuilder(latex.length());
        int i = 0;
        int n = latex.length();
        while (i < n) {
            char c = latex.charAt(i);
            if (c != '\\') {
                out.append(c);
                i++;
            } else if (i + 1 < n && !isAsciiLetter(latex.charAt(i + 1))) {
                // A control symbol (e.g. "\\", "\{", "\,"): copy both chars so they neither look like a control word
                // nor disturb brace matching.
                i = appendEscapedBackslash(out, latex, i);
            } else {
                i = appendMatrixEnvironmentOrControlWord(out, latex, i);
            }
        }
        return out.toString();
    }

    /**
     * Handles a control word starting at {@code i}: if it is a plain-TeX matrix/alignment macro with a balanced
     * {@code {...}} body, emits the corresponding amsmath environment (recursing into the body, after dropping a trailing
     * row separator); otherwise copies the control word verbatim. Returns the index just past what was consumed.
     */
    private int appendMatrixEnvironmentOrControlWord(@NotNull StringBuilder out, @NotNull String latex, int i) {
        int nameEnd = controlWordEnd(latex, i);
        String environment = PLAIN_TEX_MATRIX_ENVIRONMENTS.get(latex.substring(i + 1, nameEnd));
        int braceIndex = environment == null ? -1 : indexOfOpeningBrace(latex, nameEnd);
        int closingIndex = braceIndex == -1 ? -1 : findMatchingBrace(latex, braceIndex);
        if (closingIndex == -1) {
            out.append(latex, i, nameEnd); // not a matrix macro, or no balanced "{...}" body
            return nameEnd;
        }
        String body = stripTrailingRowSeparator(latex.substring(braceIndex + 1, closingIndex));
        out.append("\\begin{").append(environment).append('}')
                .append(convertPlainTexMatrices(body)) // recurse to handle nested matrices
                .append("\\end{").append(environment).append('}');
        return closingIndex + 1;
    }

    /**
     * Returns the index of the first opening brace at or after {@code from}, skipping only whitespace in between, or
     * {@code -1} if a non-whitespace, non-brace character is encountered first (meaning the macro has no group to wrap).
     */
    private int indexOfOpeningBrace(@NotNull String latex, int from) {
        int i = from;
        while (i < latex.length() && Character.isWhitespace(latex.charAt(i))) {
            i++;
        }
        return i < latex.length() && latex.charAt(i) == '{' ? i : -1;
    }

    /**
     * Finds the index of the closing brace that matches the opening brace at {@code openBraceIndex}, honoring nested
     * braces. An escaped character (a backslash followed by any character, such as an escaped brace or a double
     * backslash) is skipped so it does not affect the depth count.
     *
     * @return the index of the matching closing brace, or {@code -1} if the braces are unbalanced
     */
    private int findMatchingBrace(@NotNull String latex, int openBraceIndex) {
        int depth = 0;
        int i = openBraceIndex;
        int n = latex.length();
        while (i < n) {
            char c = latex.charAt(i);
            if (c == '\\') {
                i += 2; // skip the escaped character
            } else if (c == '{') {
                depth++;
                i++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
                i++;
            } else {
                i++;
            }
        }
        return -1;
    }

    /**
     * Returns the index just past the control sequence whose backslash is at {@code backslashIndex}: a control word
     * (backslash + a run of letters, via {@link #controlWordEnd}) or a control symbol (backslash + one non-letter, or a
     * lone trailing backslash).
     */
    private int skipControlSequence(@NotNull String s, int backslashIndex) {
        if (backslashIndex + 1 < s.length() && isAsciiLetter(s.charAt(backslashIndex + 1))) {
            return controlWordEnd(s, backslashIndex);
        }
        return Math.min(backslashIndex + 2, s.length());
    }

    /**
     * Whether the token spanning {@code [start, end)} (as returned by {@link #skipControlSequence}) is a row separator:
     * a {@code \\} (backslash + backslash) or the control word {@code \cr}.
     */
    private boolean isRowSeparatorToken(@NotNull String s, int start, int end) {
        return (end == start + 2 && s.charAt(start + 1) == '\\') || isExactName(s, start, end, CR_COMMAND);
    }

    private boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /**
     * Removes Jsoup's XML-mode CDATA wrapper around {@code math/tex} {@code <script>} bodies so Pandoc receives the
     * raw LaTeX. The replacement (the script tags plus the unwrapped LaTeX body) must be emitted literally: LaTeX
     * contains {@code '\'} and {@code '$'}, which a regex replacement would otherwise interpret as escape/group
     * metacharacters (e.g. {@code "\left"} would become {@code "left"}). {@link RegexMatcher#replace(String,
     * RegexMatcher.IReplacementCalculator)} quotes the replacement for us.
     */
    @NotNull
    public String unwrapMathScriptCdata(@NotNull String html) {
        return MATH_TEX_CDATA_MATCHER.replace(html, engine -> engine.group(1) + engine.group(2) + engine.group(3));
    }

}
