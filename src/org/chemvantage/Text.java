/*  ChemVantage - A Java web application for online learning
*   Copyright (C) 2011 ChemVantage LLC
*   
*    This program is free software: you can redistribute it and/or modify
*   it under the terms of the GNU General Public License as published by
*   the Free Software Foundation, either version 3 of the License, or
*   (at your option) any later version.
*
*   This program is distributed in the hope that it will be useful,
*   but WITHOUT ANY WARRANTY; without even the implied warranty of
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*   GNU General Public License for more details.
*
*   You should have received a copy of the GNU General Public License
*   along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.chemvantage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

@Entity
public class Text implements Serializable {
	private static final long serialVersionUID = 137L;
    @Id	Long id;
    @Index	String title;
    	String author;
    	String publisher;
    	String URL;
    	String imgUrl;
    	String printCopyUrl;
    @Index boolean smartText;
    	List<Chapter> chapters = new ArrayList<Chapter>();

    Text() {}
    
    Text(String title, String author, String publisher, String URL, String imgUrl,String printCopyUrl) {
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.URL = URL;
        this.imgUrl = imgUrl;
        this.printCopyUrl = printCopyUrl;
    }
}

// Note: to create a new Chapter object for the textbook txt, use
// Text txt = ofy().load().key(txtKey).now();
// Chapter newChapter = txt.new Chapter()
// Chapter 0 will normally refer to the front matter (title page, copyright, foreward, etc.)
// Additional chapters can be labeled Appendix A, Exercises, Index, etc
class Chapter {
	int chapterNumber;
	String title;
	String url;
	List<Long> conceptIds = new ArrayList<Long>();
	
	Chapter() {}
}
