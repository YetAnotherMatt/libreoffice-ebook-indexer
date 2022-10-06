# LibreOffice eBook indexer

A tool to create a clickable, eBook-friendly, index from existing index marks in an Open Document Format text (.odt) file.

It will convert any index marks in the document to bookmarks, and construct an index with hyperlinks to each bookmark.

This code was written to allow me to create an ebook-compatible index as quickly and simply as possible. 
It was not designed to be a user-friendly tool, and I'm unlikely to revisit it for maintenance/bug fixes - use it at your own risk.
It is released under an MIT licence, so feel free to copy and modify it for your own needs.

## Pre-requisites

* Java (>= version 11)

## Instructions for use

* If your document is in Word format, save it as an Open Document Format text (.odt) file.
* Edit your document:
  * Remove any existing index
  * Create two new text styles - One to control the appearance of index entries, another to control the appearance of index headers ('A', 'B', etc.).
  * (Optionally) create two additional text styles to control the appearance of links (and visited links) within the index.
  * Add a new paragraph containing the text `[INDEX_HERE]` where you want the index to appear. Note that any other content in the same paragraph as this mark will be deleted.
* Download this tool to a directory on your computer. Unzip it if necessary. You should have 3 files:
  * `EbookIndexer.java`
  * `EbookIndexer.properties`
  * This `README.md`
* Edit `EbookIndexer.properties`:
  * Set the values of `INDEX_HEADING_PARAGRAPH_STYLE` and `INDEX_ENTRY_PARAGRAPH_STYLE` to the names of the styles you created above.
    **See implementation details, below**, for notes on style names.
  * (Optionally) set the values of `INDEX_LINK_STYLE` and `INDEX_VISITED_LINK_STYLE` - these will default to the standard LibreOffice link styles if not specified.
  * If you want to also remove soft page breaks from your ebook you can set `REMOVE_SOFT_PAGE_BREAKS` to `true`.
  * Save your changes.
* Open a command prompt/terminal window in the directory where you downloaded this tool.
  (If you saved your document in the same directory as this tool, the following command will be simpler).
  Run: (assuming your document is called 'MyEbook.odt')
  * `java EbookIndexer.java "MyEbook.odt"`
* There should now be a new document in the same directory: 'MyEbook.odt.indexed.odt'.

## Implementation details

* This code was written to update a document created by LibreOffice 6.4.7.2. It should continue to work until the .odt format changes.
* The .odt file is actually a zip archive, containing (amongst other files) `content.xml` which holds the textual content of the document.
  This tool modifies `content.xml`, and only that file.
* Version 6.4.7.2 of LibreOffice converts style names when the document is saved, 
  for instance the name for a style called 'Index entry' becomes 'Index_20_entry' as seen in content.xml.
* This tool uses the exact style names specified in `EbookIndexer.properties` when modifying content.xml.
  Currently, LibreOffice seems happy to translate between 'Index entry' and 'Index_20_entry' - it applies the correct styles, 
  and if you save the resulting document in LibreOffice it will convert the style names, e.g. from 'Index entry' to 'Index_20_entry'.
* If this behaviour changes in later versions of LibreOffice, you might need to open the .odt file as a zip archive and examine content.xml
  to see what format it stores style names in, and use the correct names in `EbookIndexer.properties`.
  

