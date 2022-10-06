import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * A tool that extracts content.xml from an .odt zip archive and:
 * - Replaces all `text:alphabetical-index-mark` and `text:alphabetical-index-mark-start` elements with `text:bookmark`
 * - Removes all `text:alphabetical-index-mark-end` elements
 * - Optionally removes all `text:soft-page-break` elements
 * - Replaces a `text:p` element containing `[INDEX_HERE]` with a series of entries containing
 *   a `text:p` element for each unique index entry, containing one `text:a` element for each bookmark for that entry.
 */
public class EbookIndexer {

    static final String INDEX_MARK_PREFIX = "<text:alphabetical-index-mark";
    static final String INDEX_MARK_START = "<text:alphabetical-index-mark-start";
    static final String INDEX_MARK_END = "<text:alphabetical-index-mark-end";
    static final String SOFT_PAGE_BREAK = "<text:soft-page-break/>";

    static final IndexProperties PROPERTIES = new IndexProperties();

    /**
     * Expects a single argument - the path to the .odt file to copy & modify.
     */
    public static void main(String[] args) throws IOException {
        Path sourceFile = Path.of(args[0]);
        String sourceXml = readContentXmlFromOdtFile(sourceFile);
        Map<String, List<String>> bookmarks = new TreeMap<>(String::compareToIgnoreCase);
        String xml = replaceIndexMarksWithBookMarks(sourceXml, bookmarks);
        String index = createIndex(bookmarks);
        if (PROPERTIES.removeSoftPageBreaks) {
            xml = xml.replace(SOFT_PAGE_BREAK, "");
        }
        Path outputFile = Path.of(args[0] + ".indexed.odt");
        Files.deleteIfExists(outputFile);
        Files.copy(sourceFile, outputFile);
        xml = insertIndex(xml, index);
        writeContentXmlToOdtFile(outputFile, xml);
    }

    static String readContentXmlFromOdtFile(Path odtFile) {
        URI uri = URI.create("jar:" + odtFile.toUri());
        try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
            Path content = fs.getPath("content.xml");
            return Files.readString(content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String replaceIndexMarksWithBookMarks(String sourceXml, Map<String, List<String>> bookmarks) {
        StringBuilder result = new StringBuilder();
        int currentIndex = 0;
        int nextMark = sourceXml.indexOf(INDEX_MARK_PREFIX);
        while (nextMark >= 0) {
            if (nextMark > currentIndex) {
                result.append(sourceXml.substring(currentIndex, nextMark));
            }
            currentIndex = nextMark;
            String elementStart = sourceXml.substring(nextMark, nextMark + INDEX_MARK_START.length());
            if (elementStart.equals(INDEX_MARK_START)) { // index mark has start and end elements
                // move to after the start element
                currentIndex = endOfXmlElement(sourceXml, currentIndex);
                // extract the bookmark text from between start and end elements
                int endIndex = sourceXml.indexOf(INDEX_MARK_END, currentIndex);
                String markedText = sourceXml.substring(currentIndex, endIndex);
                String bookmarkText = markedText.replaceAll("<[^>]+>", "").trim();
                // insert a bookmark and the marked text
                result.append(addBookmark(bookmarks, bookmarkText))
                        .append(markedText);
                currentIndex = endOfXmlElement(sourceXml, endIndex);
            } else { // index mark is a single element
                int nameStart = sourceXml.indexOf('"', currentIndex) + 1;
                int nameEnd = sourceXml.indexOf('"', nameStart);
                String bookmarkText = sourceXml.substring(nameStart, nameEnd).trim();
                result.append(addBookmark(bookmarks, bookmarkText));
                currentIndex = endOfXmlElement(sourceXml, currentIndex);
            }
            nextMark = sourceXml.indexOf(INDEX_MARK_PREFIX, currentIndex);
        }
        // append anything left
        result.append(sourceXml.substring(currentIndex));

        return result.toString();
    }

    private static String createIndex(Map<String, List<String>> bookmarks) {
        StringBuilder index = new StringBuilder();
        char currentLetter = bookmarks.keySet().iterator().next().toUpperCase().charAt(0);
        if (currentLetter > 'A') {
            index.append(letterHeader(String.format("A-%s", currentLetter)));
        } else {
            index.append(letterHeader(String.format("%s", currentLetter)));
        }
        for (Map.Entry<String, List<String>> entry : bookmarks.entrySet()) {
            String bookmarkText = entry.getKey();
            List<String> targets = entry.getValue();
            char firstLetter = bookmarkText.toUpperCase().charAt(0);
            if (firstLetter > currentLetter) {
                index.append(letterHeader(currentLetter, firstLetter));
                currentLetter = firstLetter;
            }
            index.append(String.format("<text:p text:style-name=\"%s\">", PROPERTIES.indexEntryParagraphStyle));
            if (targets.size() == 1) {
                index.append(String.format("<text:a text:style-name=\"%s\" text:visited-style-name=\"%s\" xlink:href=\"#%s\" xlink:type=\"simple\">%s</text:a>",
                        PROPERTIES.indexLinkStyle, PROPERTIES.indexVisitedLinkStyle, targets.get(0), bookmarkText));
            } else {
                index.append(bookmarkText).append(": ");
                for (int i = 0; i < targets.size(); ) {
                    index.append(String.format("[<text:a text:style-name=\"%s\" text:visited-style-name=\"%s\" xlink:href=\"#%s\" xlink:type=\"simple\">%d</text:a>]",
                            PROPERTIES.indexLinkStyle, PROPERTIES.indexVisitedLinkStyle, targets.get(0), i + 1));
                    if (++i < targets.size()) {
                        index.append(", ");
                    }
                }
            }
            index.append("</text:p>");
        }
        ;
        return index.toString();
    }

    private static String letterHeader(char oldLetter, char newLetter) {
        if (newLetter > oldLetter + 1) {
            return letterHeader(String.format("%s-%s", oldLetter + 1, newLetter));
        } else {
             return letterHeader(String.format("%s", newLetter));
        }
    }

    private static String letterHeader(String headerText) {
        return String.format("<text:p text:style-name=\"%s\">%s</text:p>", PROPERTIES.indexHeadingParagraphStyle, headerText);
    }

    private static String addBookmark(Map<String, List<String>> bookmarks, String bookmarkText) {
        List<String> currentBookmarks = bookmarks.computeIfAbsent(bookmarkText, k -> new ArrayList<>());
        String bookmarkName = bookmarkText.replaceAll("[^a-zA-Z0-9]", "_") + "_" + currentBookmarks.size();
        currentBookmarks.add(bookmarkName);
        return String.format("<text:bookmark text:name=\"%s\"/>", bookmarkName);
    }

    private static int endOfXmlElement(String sourceXml, int currentIndex) {
        return sourceXml.indexOf("/>", currentIndex) + 2;
    }

    private static String insertIndex(String sourceXml, String index) {
        int indexOf = sourceXml.indexOf("[INDEX_HERE]");
        int start = sourceXml.lastIndexOf("<text:p", indexOf);
        String endParagraph = "</text:p>";
        int end = sourceXml.indexOf(endParagraph, indexOf) + endParagraph.length();
        return sourceXml.substring(0, start) + index + sourceXml.substring(end);
    }

    private static void writeContentXmlToOdtFile(Path odtFile, String contentXml) {
        URI uri = URI.create("jar:" + odtFile.toUri());
        try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
            Path content = fs.getPath("content.xml");
            Files.writeString(content, contentXml);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class IndexProperties {
        final String indexEntryParagraphStyle;
        final String indexHeadingParagraphStyle;
        final String indexLinkStyle;
        final String indexVisitedLinkStyle;
        final boolean removeSoftPageBreaks;

        IndexProperties() {
            Properties properties = loadProperties();
            this.indexEntryParagraphStyle = properties.getProperty("INDEX_ENTRY_PARAGRAPH_STYLE");
            this.indexHeadingParagraphStyle = properties.getProperty("INDEX_HEADING_PARAGRAPH_STYLE");
            this.indexLinkStyle = properties.getProperty("INDEX_LINK_STYLE", "Internet_20_link");
            this.indexVisitedLinkStyle = properties.getProperty("INDEX_VISITED_LINK_STYLE", "Visited_20_Internet_20_Link");
            this.removeSoftPageBreaks = Boolean.parseBoolean(properties.getProperty("REMOVE_SOFT_PAGE_BREAKS", "true"));
        }

        static Properties loadProperties() {
            Properties properties = new Properties();
            try (InputStream inputStream = Files.newInputStream(Path.of(EbookIndexer.class.getSimpleName() + ".properties"))) {
                properties.load(inputStream);
                return properties;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
