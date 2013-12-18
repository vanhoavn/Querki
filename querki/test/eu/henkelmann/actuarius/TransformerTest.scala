package eu.henkelmann.actuarius

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FlatSpec
import org.junit.runner.RunWith

/**
 * Tests the behavior of the complete parser, i.e. all parsing steps together.
 * 
 * IMPORTANT: this source file must use Unix newlines, not DOS ones! The resulting
 * strings come out different, and the multi-line tests break if you use DOS encoding! 
 * If it starts breaking when you run this in ScalaTest, then select the file in
 * Package Explorer, and do File -> Convert Line Delimiters To -> Unix.
 */
//@RunWith(classOf[JUnitRunner])
class TransformerTest extends FlatSpec with ShouldMatchers with Transformer {
    
    "The Transformer" should "create xhtml fragments from markdown" in {
        apply("") should equal ("")
        apply("\n") should equal ("")
        apply("Paragraph1\n")                should equal (
              "<p>Paragraph1</p>\n")
        apply("Paragraph1\n\nParagraph2\n") should equal (
              "<p>Paragraph1</p>\n<p>Paragraph2</p>\n")
        apply("Paragraph1 *italic*\n")       should equal (
              "<p>Paragraph1 <em>italic</em></p>\n")
        apply("\n\nParagraph1\n")                should equal (
              "<p>Paragraph1</p>\n")
    }

    it should "parse code blocks" in {
        apply("    foo\n") should equal ("<pre><code>foo\n</code></pre>\n")
        apply("\tfoo\n")   should equal ("<pre><code>foo\n</code></pre>\n")
        apply("    foo\n    bar\n") should equal ("<pre><code>foo\nbar\n</code></pre>\n")
        apply("    foo\n  \n    bar\n") should equal ("<pre><code>foo\n  \nbar\n</code></pre>\n")
        apply("    foo\n\tbaz\n  \n    bar\n") should equal ("<pre><code>foo\nbaz\n  \nbar\n</code></pre>\n")
        apply("    public static void main(String[] args)\n") should equal ("<pre><code>public static void main(String[] args)\n</code></pre>\n")
    }
    
    it should "parse a trivial paragraph" in {
      apply("""Here is
a paragraph""") should equal(
"""<p>Here is
a paragraph</p>
""")
    }

    it should "parse paragraphs" in {
        apply(
"""Lorem ipsum dolor sit amet,
consetetur sadipscing elitr,
sed diam nonumy eirmod tempor invidunt ut
""") should equal (
"""<p>Lorem ipsum dolor sit amet,
consetetur sadipscing elitr,
sed diam nonumy eirmod tempor invidunt ut</p>
""")
    }

    it should "parse multiple paragraphs" in {
        apply("test1\n\ntest2\n") should equal ("<p>test1</p>\n<p>test2</p>\n")
apply(
"""test

test

test"""
) should equal (
"""<p>test</p>
<p>test</p>
<p>test</p>
"""
)
    }

    it should "parse block quotes" in {
        apply("> quote\n> quote2\n") should equal("<blockquote><p>quote\nquote2</p>\n</blockquote>\n")
    }



    it should "parse ordered and unordered lists" in {
        apply("* foo\n* bar\n* baz\n") should equal (
"""<ul>
<li>foo</li>
<li>bar</li>
<li>baz</li>
</ul>
"""
        )
        apply("1. foo\n22. bar\n10. baz\n") should equal (
"""<ol>
<li>foo</li>
<li>bar</li>
<li>baz</li>
</ol>
"""
        )
        apply("* foo\n\n* bar\n\n* baz\n\n") should equal (
"""<ul>
<li><p>foo</p>
</li>
<li><p>bar</p>
</li>
<li><p>baz</p>
</li>
</ul>
"""
        )
        apply("* foo\n\n* bar\n* baz\n") should equal (
"""<ul>
<li><p>foo</p>
</li>
<li><p>bar</p>
</li>
<li>baz</li>
</ul>
"""
        )
        apply("""* foo

* bar
* baz

* bam
""") should equal (
"""<ul>
<li><p>foo</p>
</li>
<li><p>bar</p>
</li>
<li>baz</li>
<li><p>bam</p>
</li>
</ul>
"""
        )
    }
    
    it should "parse definition lists" in {
        apply(": color : red") should equal (
"""<dl>
<dt>color</dt>
<dd>red</dd>
</dl>
"""
        )   
        apply(": color : red\n: number : 42\n: animal : cat\n") should equal (
"""<dl>
<dt>color</dt>
<dd>red</dd>
<dt>number</dt>
<dd>42</dd>
<dt>animal</dt>
<dd>cat</dd>
</dl>
"""
        )      
    }
    
    it should "handle class divs" in {
      apply("""{{ myClass :
this is some styled text
}}"""
          ) should equal (
"""<div class="myClass">
<p>this is some styled text</p>
</div>
"""
          )
      
      apply("""{{ myClass :
* list 1
* list 2
}}"""
          ) should equal (
"""<div class="myClass">
<ul>
<li>list 1</li>
<li>list 2</li>
</ul>
</div>
"""
          )
    }
        
    it should "handle nested class divs" in {
      apply("""{{ myClass :
{{ myClass2 :
this is some styled text
}}
}}"""
          ) should equal (
"""<div class="myClass">
<div class="myClass2">
<p>this is some styled text</p>
</div>
</div>
"""
          )
    }
    
    it should "handle multiple-class divs" in {
      apply("""{{ myClass myClass2  myClass3 :
this is some styled text
}}"""
          ) should equal (
"""<div class="myClass myClass2 myClass3">
<p>this is some styled text</p>
</div>
"""
          )
    }
    
    it should "handle class spans" in {
      apply("""{{ myClass: here is some styled text!}} and unstyled""") should equal (
            """<p><span class="myClass"> here is some styled text!</span> and unstyled</p>
""")
      apply("""Here is some {{myClass:styled text that
crosses a line}} and then
continues
""") should equal ("""<p>Here is some <span class="myClass">styled text that
crosses a line</span> and then
continues</p>
""")
    }

    it should "stop a list after an empty line" in {
apply("""1. a
2. b

paragraph"""
    ) should equal (
"""<ol>
<li>a</li>
<li>b</li>
</ol>
<p>paragraph</p>
"""
)

    }

    it should "recursively evaluate quotes" in {
        apply("> foo\n> > bar\n> \n> baz\n") should equal (
"""<blockquote><p>foo</p>
<blockquote><p>bar</p>
</blockquote>
<p>baz</p>
</blockquote>
"""
        )
    }

    it should "handle corner cases for bold and italic in paragraphs" in {
        apply("*foo * bar *\n") should equal ("<p>*foo * bar *</p>\n")
        apply("*foo * bar*\n") should equal ("<p><em>foo * bar</em></p>\n")
        apply("*foo* bar*\n") should equal ("<p><em>foo</em> bar*</p>\n")
        apply("**foo* bar*\n") should equal ("<p>*<em>foo</em> bar*</p>\n")
        apply("**foo* bar**\n") should equal ("<p><strong>foo* bar</strong></p>\n")
        apply("** foo* bar **\n") should equal ("<p>** foo* bar **</p>\n")
    }

    it should "resolve referenced links" in {
        apply("""An [example][id]. Then, anywhere
else in the doc, define the link:

  [id]: http://example.com/  "Title"
""") should equal ("""<p>An <a href="http://example.com/" title="Title">example</a>. Then, anywhere
else in the doc, define the link:</p>
""")
    }

    it should "parse atx style headings" in {
        apply("#A Header\n")               should equal ("<h1>A Header</h1>\n")
        apply("###A Header\n")             should equal ("<h3>A Header</h3>\n")
        apply("### A Header  \n")          should equal ("<h3>A Header</h3>\n")
        apply("### A Header##\n")          should equal ("<h3>A Header</h3>\n")
        apply("### A Header##  \n")        should equal ("<h3>A Header</h3>\n")
        apply("### A Header  ##  \n")      should equal ("<h3>A Header</h3>\n")
        apply("### A Header ## foo ## \n") should equal ("<h3>A Header ## foo</h3>\n")
    }

    it should "parse setext style level 1 headings" in {
        apply("A Header\n===\n")           should equal ("<h1>A Header</h1>\n")
        apply("A Header\n=\n")             should equal ("<h1>A Header</h1>\n")
        apply("  A Header \n========\n")   should equal ("<h1>A Header</h1>\n")
        apply("  A Header \n===  \n")      should equal ("<h1>A Header</h1>\n")
        apply("  ==A Header== \n======\n") should equal ("<h1>==A Header==</h1>\n")
        apply("##Header 1==\n=     \n")    should equal ("<h1>##Header 1==</h1>\n")
    }

    it should "parse setext style level 2 headings" in {
        apply("A Header\n---\n")           should equal ("<h2>A Header</h2>\n")
        apply("A Header\n-\n")             should equal ("<h2>A Header</h2>\n")
        apply("  A Header \n--------\n")   should equal ("<h2>A Header</h2>\n")
        apply("  A Header \n---  \n")      should equal ("<h2>A Header</h2>\n")
        apply("  --A Header-- \n------\n") should equal ("<h2>--A Header--</h2>\n")
    }


    it should "parse xml-like blocks as is" in {
        apply("<foo> bla\nblub <bar>hallo</bar>\n</foo>\n") should equal (
              "<foo> bla\nblub <bar>hallo</bar>\n</foo>\n")
    }
    
    it should "parse fenced code blocks" in {
apply(
"""```  foobar
System.out.println("Hello World!");
    
<some> verbatim xml </some>
    
    <-not a space-style code line
 1. not a
 2. list
    
## not a header
``` gotcha: not the end
-----------
but this is:
```         
"""    
) should equal (
"""<pre><code>System.out.println(&quot;Hello World!&quot;);
    
&lt;some&gt; verbatim xml &lt;/some&gt;
    
    &lt;-not a space-style code line
 1. not a
 2. list
    
## not a header
``` gotcha: not the end
-----------
but this is:
</code></pre>
"""    
)

apply(
"""```
System.out.println("Hello World!");
```
And now to something completely different.
    old style code
"""    
) should equal (
"""<pre><code>System.out.println(&quot;Hello World!&quot;);
</code></pre>
<p>And now to something completely different.</p>
<pre><code>old style code
</code></pre>
"""    
)

apply(
"""```
System.out.println("Hello World!");
No need to end blocks

And now to something completely different.
    old style code
"""    
) should equal (
"""<pre><code>System.out.println(&quot;Hello World!&quot;);
No need to end blocks

And now to something completely different.
    old style code
</code></pre>
"""    
)

apply(
"""Some text first
```
System.out.println("Hello World!");
No need to end blocks

And now to something completely different.
    old style code
"""    
) should equal (
"""<p>Some text first</p>
<pre><code>System.out.println(&quot;Hello World!&quot;);
No need to end blocks

And now to something completely different.
    old style code
</code></pre>
"""    
)
    }
    
    it should "allow HTTP URLs" in {
      apply("[Querki](http://www.querki.net/)") should equal ("<p><a href=\"http://www.querki.net/\">Querki</a></p>\n")
    }
    
    it should "allow HTTPS URLs" in {
      apply("[Querki](https://www.querki.net/)") should equal ("<p><a href=\"https://www.querki.net/\">Querki</a></p>\n")
    }
    
    it should "allow relative URLs" in {
      apply("[My Thing](My-Thing)") should equal ("<p><a href=\"My-Thing\">My Thing</a></p>\n")
    }
    
    it should "prevent all forms of Javascript injection" in {
      apply("[Evil](javascript://www.querki.net/)") should equal ("<p><a href=\"./javascript://www.querki.net/\">Evil</a></p>\n")
      apply("[Evil](javascript+://www.querki.net/)") should equal ("<p><a href=\"./javascript+://www.querki.net/\">Evil</a></p>\n")
      apply("[Evil](javascript-://www.querki.net/)") should equal ("<p><a href=\"./javascript-://www.querki.net/\">Evil</a></p>\n")
      apply("[Evil](javascript.://www.querki.net/)") should equal ("<p><a href=\"./javascript.://www.querki.net/\">Evil</a></p>\n")
    }
    
    it should "be careful about unknown schemes" in {
      apply("[Weird](thingamahoozie:Is-My-Name)") should equal ("<p><a href=\"./thingamahoozie:Is-My-Name\">Weird</a></p>\n")
    }
}