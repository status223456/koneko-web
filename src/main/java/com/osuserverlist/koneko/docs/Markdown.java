package com.osuserverlist.koneko.docs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small Markdown renderer, just big enough for the documents this site ships
 * in {@code /docs}.
 *
 * <p>It is deliberately hand written rather than a dependency: the pages here
 * are written by us, not by visitors, so the renderer only has to understand
 * the handful of constructs those pages use - headings (with the
 * {@code {id=anchor}} suffix the osu! wiki uses), paragraphs, bullet lists,
 * block quotes, fenced code, tables, rules, and inline bold, italic, code and
 * links.
 *
 * <p>Everything that is not markup is HTML escaped first, so a document can
 * never inject markup into the page by accident.
 */
public final class Markdown {

    private static final Pattern HEADING = Pattern.compile("^(#{1,4})\\s+(.*)$");
    private static final Pattern HEADING_ID = Pattern.compile("\\s*\\{id=([A-Za-z0-9_-]+)}\\s*$");
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*]\\s+(.*)$");
    private static final Pattern ORDERED = Pattern.compile("^\\s*\\d+[.)]\\s+(.*)$");
    private static final Pattern QUOTE = Pattern.compile("^>\\s?(.*)$");
    private static final Pattern TABLE_DIVIDER = Pattern.compile("^\\|?[\\s:|-]+\\|[\\s:|-]*$");

    private static final Pattern CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern LINK = Pattern.compile("\\[([^]]+)]\\(([^)\\s]+)\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<![*\\w])\\*([^*]+)\\*(?![*\\w])");

    private Markdown() {
    }

    /** Renders a Markdown document to a fragment of HTML. */
    public static String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        List<String> lines = List.of(markdown.replace("\r\n", "\n").split("\n", -1));

        StringBuilder html = new StringBuilder();
        List<String> paragraph = new ArrayList<>();

        // What block we are inside of, so a list or a table can span lines
        // without the loop having to look ahead.
        String openList = null;
        boolean inQuote = false;
        boolean inCode = false;

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);

            if (line.trim().startsWith("```")) {
                if (inCode) {
                    html.append("</code></pre>\n");
                    inCode = false;
                } else {
                    paragraph = flushParagraph(html, paragraph);
                    openList = closeList(html, openList);
                    inQuote = closeQuote(html, inQuote);
                    html.append("<pre><code>");
                    inCode = true;
                }

                continue;
            }

            if (inCode) {
                html.append(escape(line)).append('\n');
                continue;
            }

            if (line.isBlank()) {
                paragraph = flushParagraph(html, paragraph);
                openList = closeList(html, openList);
                inQuote = closeQuote(html, inQuote);
                continue;
            }

            // A table: the header row, its divider, then the body.
            if (line.trim().startsWith("|") && index + 1 < lines.size()
                    && TABLE_DIVIDER.matcher(lines.get(index + 1).trim()).matches()) {

                paragraph = flushParagraph(html, paragraph);
                openList = closeList(html, openList);
                inQuote = closeQuote(html, inQuote);

                index = table(html, lines, index);
                continue;
            }

            Matcher heading = HEADING.matcher(line);

            if (heading.matches()) {
                paragraph = flushParagraph(html, paragraph);
                openList = closeList(html, openList);
                inQuote = closeQuote(html, inQuote);

                int level = heading.group(1).length();
                String text = heading.group(2).trim();

                // "## Title {id=anchor}" becomes an id, so the page can be
                // linked to section by section.
                Matcher id = HEADING_ID.matcher(text);
                String anchor = null;

                if (id.find()) {
                    anchor = id.group(1);
                    text = text.substring(0, id.start()).trim();
                }

                html.append("<h").append(level);

                if (anchor != null) {
                    html.append(" id=\"").append(escape(anchor)).append('"');
                }

                html.append('>').append(inline(text)).append("</h").append(level).append(">\n");
                continue;
            }

            if (line.trim().equals("---") || line.trim().equals("***")) {
                paragraph = flushParagraph(html, paragraph);
                openList = closeList(html, openList);
                inQuote = closeQuote(html, inQuote);
                html.append("<hr>\n");
                continue;
            }

            Matcher quote = QUOTE.matcher(line);

            if (quote.matches()) {
                paragraph = flushParagraph(html, paragraph);
                openList = closeList(html, openList);

                if (!inQuote) {
                    html.append("<blockquote>\n");
                    inQuote = true;
                }

                html.append(inline(quote.group(1).trim())).append(' ');
                continue;
            }

            Matcher bullet = BULLET.matcher(line);
            Matcher ordered = ORDERED.matcher(line);

            if (bullet.matches() || ordered.matches()) {
                paragraph = flushParagraph(html, paragraph);

                String tag = bullet.matches() ? "ul" : "ol";

                if (openList != null && !openList.equals(tag)) {
                    openList = closeList(html, openList);
                }

                if (openList == null) {
                    html.append('<').append(tag).append(">\n");
                    openList = tag;
                }

                String text = bullet.matches() ? bullet.group(1) : ordered.group(1);

                html.append("<li>").append(inline(text.trim())).append("</li>\n");
                continue;
            }

            // A plain line that follows a list item or a quote belongs to it:
            // that is how the documents wrap long items.
            if (openList != null && line.startsWith("  ")) {
                html.setLength(html.length() - "</li>\n".length());
                html.append(' ').append(inline(line.trim())).append("</li>\n");
                continue;
            }

            if (inQuote) {
                html.append(inline(line.trim())).append(' ');
                continue;
            }

            paragraph.add(line.trim());
        }

        flushParagraph(html, paragraph);
        closeList(html, openList);
        closeQuote(html, inQuote);

        if (inCode) {
            html.append("</code></pre>\n");
        }

        return html.toString();
    }

    /** Renders one table and returns the index of its last line. */
    private static int table(StringBuilder html, List<String> lines, int start) {
        html.append("<table class=\"table\">\n<thead><tr>");

        for (String cell : cells(lines.get(start))) {
            html.append("<th>").append(inline(cell)).append("</th>");
        }

        html.append("</tr></thead>\n<tbody>\n");

        int index = start + 2;

        while (index < lines.size() && lines.get(index).trim().startsWith("|")) {
            html.append("<tr>");

            for (String cell : cells(lines.get(index))) {
                html.append("<td>").append(inline(cell)).append("</td>");
            }

            html.append("</tr>\n");
            index++;
        }

        html.append("</tbody>\n</table>\n");

        return index - 1;
    }

    private static List<String> cells(String row) {
        String trimmed = row.trim();

        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }

        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        List<String> cells = new ArrayList<>();

        for (String cell : trimmed.split("\\|", -1)) {
            cells.add(cell.trim());
        }

        return cells;
    }

    private static List<String> flushParagraph(StringBuilder html, List<String> paragraph) {
        if (!paragraph.isEmpty()) {
            html.append("<p>").append(inline(String.join(" ", paragraph))).append("</p>\n");
        }

        return new ArrayList<>();
    }

    private static String closeList(StringBuilder html, String openList) {
        if (openList != null) {
            html.append("</").append(openList).append(">\n");
        }

        return null;
    }

    private static boolean closeQuote(StringBuilder html, boolean inQuote) {
        if (inQuote) {
            html.append("\n</blockquote>\n");
        }

        return false;
    }

    /** Inline markup, applied to text that has already been escaped. */
    private static String inline(String text) {
        String html = escape(text);

        html = CODE.matcher(html).replaceAll(match -> "<code>"
                + Matcher.quoteReplacement(match.group(1)) + "</code>");

        html = LINK.matcher(html).replaceAll(match -> {
            String label = match.group(1);
            String href = match.group(2);
            // Only ordinary links are rendered as links; anything exotic is
            // left as plain text rather than trusted.
            boolean safe = href.startsWith("/") || href.startsWith("#")
                    || href.startsWith("http://") || href.startsWith("https://")
                    || href.startsWith("mailto:");

            if (!safe) {
                return Matcher.quoteReplacement(label);
            }

            boolean external = href.startsWith("http");

            return Matcher.quoteReplacement("<a href=\"" + href + "\""
                    + (external ? " target=\"_blank\" rel=\"noopener\"" : "")
                    + ">" + label + "</a>");
        });

        html = BOLD.matcher(html).replaceAll(match -> "<strong>"
                + Matcher.quoteReplacement(match.group(1)) + "</strong>");

        html = ITALIC.matcher(html).replaceAll(match -> "<em>"
                + Matcher.quoteReplacement(match.group(1)) + "</em>");

        return html;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
