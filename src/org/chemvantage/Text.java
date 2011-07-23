package org.chemvantage;

import java.io.Serializable;

import javax.persistence.Id;

import com.googlecode.objectify.annotation.Cached;

@Cached
public class Text implements Serializable {
	private static final long serialVersionUID = 137L;
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