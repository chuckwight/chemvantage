package org.chemvantage;

import javax.persistence.Id;

import com.googlecode.objectify.annotation.Cached;

@Cached
public class Text {
    @Id Long id;
    String title;
    String author;
    String publisher;
    String URL;

    Text() {}
    
    Text(String title, String author, String publisher, String URL) {
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.URL = URL;
    }

}